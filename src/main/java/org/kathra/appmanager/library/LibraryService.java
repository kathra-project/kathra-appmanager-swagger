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
package org.kathra.appmanager.library;

import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.LibrariesClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author julien.boubechtoula
 */
public class LibraryService extends AbstractResourceService<Library> {

    private SourceRepositoryService sourceRepositoryService;
    private PipelineService pipelineService;
    private LibrariesClient resourceManager;
    private ComponentService componentService;

    public LibraryService() {

    }

    public void configure(ServiceInjection serviceInjection){
        super.configure(serviceInjection);
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        this.pipelineService = serviceInjection.getService(PipelineService.class);
        this.componentService = serviceInjection.getService(ComponentService.class);
        this.resourceManager = new LibrariesClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
    }

    public LibraryService(LibrariesClient resourceManager, ComponentService componentService, PipelineService pipelineService, SourceRepositoryService sourceRepositoryService, KathraSessionManager kathraSessionManager) {
        this.sourceRepositoryService = sourceRepositoryService;
        this.componentService = componentService;
        this.pipelineService = pipelineService;
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
    }

    public Optional<Library> getLibraryByComponentAndLanguageAndType(Component component, Library.LanguageEnum languageProgramming, Library.TypeEnum typeLib) throws ApiException {
        Component componentWithDetails = componentService.getById(component.getId()).get();

        final Session session = kathraSessionManager.getCurrentSession();
        AtomicReference<ApiException> exception = new AtomicReference<>();
        if (componentWithDetails.getLibraries() == null) {
            return Optional.empty();
        }

        Optional<Library> exist = componentWithDetails.getLibraries().parallelStream().filter(lib -> {
            try {
                kathraSessionManager.handleSession(session);
                Library libWithDetails = resourceManager.getLibrary(lib.getId());
                return libWithDetails.getLanguage().equals(languageProgramming) && libWithDetails.getType().equals(typeLib);
            } catch (ApiException e) {
                e.printStackTrace();
                exception.set(e);
                return false;
            }
        }).findFirst();

        if (exception.get() != null) {
            throw exception.get();
        }

        return exist;
    }


    public Library add(Component component, Library.LanguageEnum languageProgramming, Library.TypeEnum typeLib, Runnable callback) throws ApiException {

        if (component == null) {
            throw new IllegalArgumentException("Component is null");
        }
        if (languageProgramming == null) {
            throw new IllegalArgumentException("Programming language is null");
        }
        if (typeLib == null) {
            throw new IllegalArgumentException("Library's type is null");
        }

        if (getLibraryByComponentAndLanguageAndType(component, languageProgramming, typeLib).isPresent()) {
            throw new IllegalArgumentException("This library component already exists");
        }

        Library toAdd = new Library().name(component.getName()+"-"+languageProgramming.toString()+"-"+typeLib.toString()).component(component).language(languageProgramming).type(typeLib);

        try {
            final Library library = resourceManager.addLibrary(toAdd);
            final Session session = kathraSessionManager.getCurrentSession();
            CompletableFuture.runAsync(() -> {
                try {
                    kathraSessionManager.handleSession(session);
                    SourceRepository sourceRepository = sourceRepositoryService.createLibraryRepository(library, () -> validationSourceRepositoryReady(library, callback));
                    // Patch library with new source repository
                    this.patch(new Library().id(library.getId()).sourceRepository(sourceRepository));
                } catch (Exception e) {
                    manageError(library, e);
                    if (callback != null) {
                        callback.run();
                    }
                }
            });


            return library;
        } catch (Exception e) {
            manageError(toAdd, e);
            throw e;
        }
    }

    /**
     * Method executed when SourceRepository is READY
     * @param library
     * @param callbackIfReady
     * @return
     */
    private synchronized boolean validationSourceRepositoryReady(final Library library, Runnable callbackIfReady) {

        try {
            Library libraryUpdated = resourceManager.getLibrary(library.getId());
            if (libraryUpdated == null) {
                logger.error("Library '"+library.getId()+"' doesn't exist.");
                return false;
            }
            if (isReady(libraryUpdated)){
                return true;
            }
            boolean sourceRepositoryIsReady = sourceRepositoryIsReady(libraryUpdated);
            logger.info("Library '"+libraryUpdated.getId()+"': SourceRepository '"+libraryUpdated.getSourceRepository().getId()+"' is ready = "+sourceRepositoryIsReady);
            if (sourceRepositoryIsReady && (library.getPipeline() == null || library.getPipeline().getId() == null)) {
                logger.info("SourceRepository "+libraryUpdated.getSourceRepository().getId()+" is ready, create pipeline for library "+libraryUpdated.getId());
                Pipeline pipeline = pipelineService.createLibraryPipeline(libraryUpdated, () -> validationPipelineReady(libraryUpdated, callbackIfReady));
                library.setPipeline(pipeline);
                return true;
            }
            return false;
        } catch (Exception e) {
            manageError(library, e);
            if (callbackIfReady != null) {
                callbackIfReady.run();
            }
            return false;
        }
    }

    /**
     * Method executed when Pipeline is READY
     * @param library
     * @param callbackIfReady
     * @return
     */
    private synchronized boolean validationPipelineReady(final Library library, Runnable callbackIfReady) {

        try {
            Library libraryUpdated = resourceManager.getLibrary(library.getId());
            if (libraryUpdated == null) {
                logger.error("Library '"+library.getId()+"' doesn't exist.");
                return false;
            }
            if (isReady(libraryUpdated)){
                return true;
            }

            boolean pipelineIsReady = pipelineIsReady(libraryUpdated);

            logger.info("Library '"+libraryUpdated.getId()+"': Pipeline is ready = "+pipelineIsReady);
            if (pipelineIsReady) {
                logger.info("Library '"+libraryUpdated.getId()+"' is ready");
                updateStatus(libraryUpdated, Resource.StatusEnum.READY);
                if (callbackIfReady != null){
                    callbackIfReady.run();
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            manageError(library, e);
            if (callbackIfReady != null) {
                callbackIfReady.run();
            }
            return false;
        }
    }

    private boolean sourceRepositoryIsReady(Library library) throws Exception {
        if (library.getSourceRepository() == null) {
            return false;
        }
        Optional<SourceRepository> sourceRepository = sourceRepositoryService.getById(library.getSourceRepository().getId());
        if (sourceRepository.isPresent()) {
            throwExceptionIfError(sourceRepository.get());
            if (StringUtils.isEmpty(sourceRepository.get().getSshUrl())) {
                throw new Exception("SourceRepository's sshUrl should be defined");
            }
            return isReady(sourceRepository.get());
        }
        return false;
    }

    private boolean pipelineIsReady(Library library) throws ApiException {
        if (library.getPipeline() == null) {
            return false;
        }
        Optional<Pipeline> pipeline = pipelineService.getById(library.getPipeline().getId());
        if (pipeline.isPresent()) {
            throwExceptionIfError(pipeline.get());
            return isReady(pipeline.get());
        }
        return false;
    }

    @Override
    public void patch(Library object) throws ApiException {
        resourceManager.updateLibraryAttributes(object.getId(), object);
    }

    @Override
    public Optional<Library> getById(String id) throws ApiException {
        Library lib = resourceManager.getLibrary(id);
        return lib == null ? Optional.empty() : Optional.of(lib);
    }

    @Override
    public List<Library> getAll() throws ApiException {
        return resourceManager.getLibraries();
    }

    public void delete(Library library, boolean force, boolean purge) throws ApiException {
        try {
            Library libraryToDeleted = resourceManager.getLibrary(library.getId());
            if (isDeleted(libraryToDeleted)) {
                return;
            }
            if (libraryToDeleted.getPipeline() != null){
                pipelineService.delete(libraryToDeleted.getPipeline(), purge);
            }
            if (libraryToDeleted.getSourceRepository() != null){
                sourceRepositoryService.delete(libraryToDeleted.getSourceRepository(), purge);
            }
            resourceManager.deleteLibrary(library.getId());
            library.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(library, e);
            throw e;
        }
    }
}
