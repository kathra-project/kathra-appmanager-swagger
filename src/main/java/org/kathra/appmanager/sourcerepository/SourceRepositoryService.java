/* 
 * Copyright 2019 The Kathra Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *
 *    IRT SystemX (https://www.kathra.org/)    
 *
 */
package org.kathra.appmanager.sourcerepository;

import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.SourceRepositoriesClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
/**
 * @author julien.boubechtoula
 */
public class SourceRepositoryService extends AbstractResourceService<SourceRepository> {

    private SourceManagerClient sourceManagerClient;
    private SourceRepositoriesClient resourceManager;
    private LibraryService libraryService;
    private ComponentService componentService;

    public SourceRepositoryService(){

    }

    public void configure(ServiceInjection serviceInjection) {
        super.configure(serviceInjection);
        this.resourceManager = new SourceRepositoriesClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
        this.sourceManagerClient = new SourceManagerClient(serviceInjection.getConfig().getSourceManagerUrl(), serviceInjection.getSessionManager());
        this.libraryService = serviceInjection.getService(LibraryService.class);
        this.componentService = serviceInjection.getService(ComponentService.class);
    }

    public SourceRepositoryService(SourceRepositoriesClient resourceManager, SourceManagerClient sourceManagerClient, ComponentService componentService, LibraryService libraryService, KathraSessionManager kathraSessionManager) {
        this.sourceManagerClient = sourceManagerClient;
        this.resourceManager = resourceManager;
        this.libraryService = libraryService;
        this.componentService = componentService;
        super.kathraSessionManager = kathraSessionManager;
    }

    public void setSourceManagerClient(SourceManagerClient sourceManagerClient) {
        this.sourceManagerClient = sourceManagerClient;
    }

    public SourceRepositoriesClient getResourceManager() {
        return resourceManager;
    }

    public void setResourceManager(SourceRepositoriesClient resourceManager) {
        this.resourceManager = resourceManager;
    }

    public LibraryService getLibraryService() {
        return libraryService;
    }

    public void setLibraryService(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    public SourceRepository createLibraryRepository(Library library, Runnable callback) throws ApiException {
        if (library == null) {
            throw new IllegalArgumentException("library is null");
        }
        if (library.getLanguage() == null) {
            throw new IllegalArgumentException("Programming language's library is null");
        }
        if (library.getType() == null) {
            throw new IllegalArgumentException("Type's library is null");
        }
        if (library.getComponent() == null) {
            throw new IllegalArgumentException("Component's library is null");
        }

        Component component = componentService.getById(library.getComponent().getId()).orElseThrow(() -> new IllegalStateException("Unable to find component "+library.getComponent().getId()));

        if (StringUtils.isEmpty(component.getName())) {
            throw new IllegalArgumentException("Component name is null or empty");
        }
        if (component.getMetadata() == null || !component.getMetadata().containsKey(ComponentService.METADATA_GROUP_PATH) || StringUtils.isEmpty((CharSequence) component.getMetadata().get(ComponentService.METADATA_GROUP_PATH))) {
            throw new IllegalArgumentException("Component "+ComponentService.METADATA_GROUP_PATH+" is null or empty");
        }
        if (component.getMetadata() == null || !component.getMetadata().containsKey(ComponentService.METADATA_GROUP_ID) || StringUtils.isEmpty((CharSequence) component.getMetadata().get(ComponentService.METADATA_GROUP_ID))) {
            throw new IllegalArgumentException("Component "+ComponentService.METADATA_GROUP_ID+" is null or empty");
        }

        String name = library.getName();
        String parentPath = component.getMetadata().get(ComponentService.METADATA_GROUP_PATH)+"/components/"+component.getName()+"/"+library.getLanguage();
        String path = parentPath+"/"+library.getType();
        String[] deploysKeys = {(String) component.getMetadata().get(ComponentService.METADATA_GROUP_ID)};

        // create source repository
        SourceRepository sourceRepository = create(name, path, deploysKeys, callback);

        // Patch library with new source repository
        libraryService.patch(new Library().id(library.getId()).sourceRepository(sourceRepository));
        library.setSourceRepository(sourceRepository);

        return sourceRepository;
    }

    public SourceRepository create(String name, String path, String[] deploysKeys, Runnable callback) throws ApiException {
        SourceRepository sourceRepositoryToAdd = new SourceRepository().name(name).path(path);
        final SourceRepository sourceRepository;

        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("SourceRepository's name is null or empty");
        }

        if (StringUtils.isEmpty(path)) {
            throw new IllegalArgumentException("SourceRepository's path is null or empty");
        }

        if (getByPath(path).isPresent()) {
            throw new IllegalArgumentException("SourceRepository with path '"+path+"' already exists");
        }

        try {
            sourceRepository = resourceManager.addSourceRepository(sourceRepositoryToAdd);

            final Session session = kathraSessionManager.getCurrentSession();
            CompletableFuture.runAsync(() -> {
                try {
                    kathraSessionManager.handleSession(session);
                    SourceRepository sourceRepositoryWithInfoSourceManager = sourceManagerClient.createSourceRepository(sourceRepository, Arrays.asList(deploysKeys));
                    //override uuid
                    sourceRepositoryWithInfoSourceManager.setId(sourceRepository.getId());
                    sourceRepositoryWithInfoSourceManager.setPath(sourceRepository.getName());
                    sourceRepositoryWithInfoSourceManager.setPath(sourceRepository.getPath());
                    sourceRepositoryWithInfoSourceManager.setStatus(Resource.StatusEnum.READY);

                    patch(sourceRepositoryWithInfoSourceManager);
                } catch (Exception e) {
                    manageError(sourceRepository, e);
                } finally {
                    try {
                        if (callback != null) {
                            callback.run();
                        }
                    } catch (Exception e) {
                        logger.error("callback execution error", e);
                    }
                }
            });


            return sourceRepository;
        } catch (ApiException e) {
            manageError(sourceRepositoryToAdd, e);
            throw e;
        }
    }

    public SourceRepositoryCommit commitFileAndTag(SourceRepository sourceRepository, String branch, File file, String filename, String tag) throws ApiException {
        checkSrcRepo(sourceRepository);
        if (StringUtils.isEmpty(branch)) {
            throw new IllegalArgumentException("Branch is null or empty");
        }
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }
        return sourceManagerClient.createCommit(sourceRepository.getPath(), branch, file, filename, false, tag,false);
    }

    private void checkSrcRepo(SourceRepository sourceRepository) {
        if (sourceRepository == null) {
            throw new IllegalArgumentException("SourceRepository is null");
        }
        if (StringUtils.isEmpty(sourceRepository.getPath())) {
            throw new IllegalArgumentException("SourceRepository's path is null or empty");
        }
    }

    public SourceRepositoryCommit commitArchiveAndTag(SourceRepository sourceRepository, String branch, File file, String path, String tag) throws ApiException {
        checkSrcRepo(sourceRepository);
        if (file == null) {
            throw new IllegalArgumentException("file is null");
        }
        if (StringUtils.isEmpty(branch)) {
            throw new IllegalArgumentException("Branch is null or empty");
        }
        return sourceManagerClient.createCommit(sourceRepository.getPath(), branch, file, path, true, tag, true);

    }

    private Optional<SourceRepository> getByPath(String path) throws ApiException {
        return resourceManager.getSourceRepositories().stream().filter( i -> i.getPath().equals(path)).findFirst();
    }

    @Override
    protected void patch(SourceRepository object) throws ApiException {
        resourceManager.updateSourceRepositoryAttributes(object.getId(), object);
    }

    @Override
    public Optional<SourceRepository> getById(String id) throws ApiException {
        SourceRepository object = resourceManager.getSourceRepository(id);
        return object == null ? Optional.empty() : Optional.of(object);
    }

    @Override
    public List<SourceRepository> getAll() throws ApiException {
        return resourceManager.getSourceRepositories();
    }

    public File getFile(SourceRepository sourceRepository, String branch, String filepath) throws ApiException {
        checkSrcRepo(sourceRepository);
        if (StringUtils.isEmpty(branch)) {
            throw new IllegalArgumentException("branch is null or empty");
        }
        if (StringUtils.isEmpty(filepath)) {
            throw new IllegalArgumentException("filepath is null or empty");
        }

        return sourceManagerClient.getFile(sourceRepository.getPath(), branch, filepath);
    }

    public List<SourceRepositoryCommit> getCommits(SourceRepository sourceRepository, String branch) throws ApiException {
        checkSrcRepo(sourceRepository);
        if (StringUtils.isEmpty(branch)) {
            throw new IllegalArgumentException("branch is null or empty");
        }
        return sourceManagerClient.getCommits(sourceRepository.getPath(), branch);
    }

    public List<String> getBranchs(SourceRepository sourceRepository) throws ApiException {
        checkSrcRepo(sourceRepository);
        return sourceManagerClient.getBranches(sourceRepository.getPath());
    }

    public void delete(SourceRepository sourceRepository, boolean purge) throws ApiException {
        try {
            SourceRepository sourceRepositoryToDeleted = resourceManager.getSourceRepository(sourceRepository.getId());
            if (isDeleted(sourceRepositoryToDeleted)) {
                return;
            }
            if (purge) {
                sourceManagerClient.deleteSourceRepository(sourceRepositoryToDeleted.getPath());
            }
            resourceManager.deleteSourceRepository(sourceRepository.getId());
            sourceRepository.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(sourceRepository, e);
            throw e;
        }
    }
}
