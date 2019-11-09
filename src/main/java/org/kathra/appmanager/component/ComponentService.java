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
package org.kathra.appmanager.component;

import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.group.GroupService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ImplementationsService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ComponentsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author julien.boubechtoula
 *
 */
public class ComponentService extends AbstractResourceService<Component> {

    public static final String METADATA_GROUP_PATH = "groupPath";
    public static final String METADATA_GROUP_ID= "groupId";
    public static final String METADATA_API_GROUP_ID = "artifact-groupId";
    public static final String METADATA_API_ARTIFACT_NAME = "artifact-artifactName";

    private static final Pattern PATTERN_NAME = Pattern.compile("^[0-9A-Za-z_\\-]+$");

    private GroupService groupsService;
    private SourceRepositoryService sourceRepositoryService;
    private LibraryService libraryService;
    private ComponentsClient componentsClient;
    private ApiVersionService apiVersionService;
    private ImplementationService implementationService;

    private Logger logger = LoggerFactory.getLogger(ComponentService.class);

    public ComponentService(){

    }

    public void configure(ServiceInjection service){
        super.configure(service);
        this.componentsClient = new ComponentsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.sourceRepositoryService = service.getService(SourceRepositoryService.class);
        this.libraryService =  service.getService(LibraryService.class);
        this.groupsService = service.getService(GroupService.class);
        this.apiVersionService = service.getService(ApiVersionService.class);
        this.implementationService = service.getService(ImplementationService.class);
    }

    public ComponentService(ComponentsClient componentsClient, SourceRepositoryService sourceRepositoryService, ApiVersionService apiVersionService, LibraryService libraryService, GroupService groupsService, KathraSessionManager kathraSessionManager, ImplementationService implementationService) {
        this.componentsClient = componentsClient;
        this.implementationService = implementationService;
        this.sourceRepositoryService = sourceRepositoryService;
        this.libraryService = libraryService;
        this.groupsService = groupsService;
        this.apiVersionService = apiVersionService;
        super.kathraSessionManager = kathraSessionManager;
    }


    /**
     *
     * @param component
     * @param groupPath
     * @return
     */
    public Component create(@NotNull Component component, @NotNull String groupPath) throws ApiException {
        return create(component, groupPath, null);
    }

    public Component create(@NotNull Component component, @NotNull String groupPath, Runnable callback) throws ApiException {

        if (StringUtils.isEmpty(groupPath)) {
            throw new IllegalArgumentException("GroupPath should be defined");
        }

        Optional<Group> group = groupsService.findByPath(groupPath);
        if (!group.isPresent()) {
            throw new IllegalArgumentException("GroupPath defined '" + groupPath + "' doesn't exist.");
        }
        if (StringUtils.isEmpty(component.getName())) {
            throw new IllegalArgumentException("Component's name is empty");
        } else if (!PATTERN_NAME.matcher(component.getName()).find()) {
            throw new IllegalArgumentException("Component's name do not respect regex's pattern " + PATTERN_NAME.pattern());
        }

        if (getByNameAndGroupPath(component.getName(), groupPath).isPresent()) {
            throw new IllegalArgumentException("Component already exists");
        }
        // CREATE COMPONENT
        Component componentToAdd = new Component().name(component.getName())
                                                .title(component.getTitle())
                                                .description(component.getDescription())
                                                .putMetadataItem(METADATA_GROUP_ID, group.get().getId())
                                                .putMetadataItem(METADATA_GROUP_PATH, group.get().getPath())
                                                .status(Resource.StatusEnum.PENDING);
        try {

            final Component componentAdded = componentsClient.addComponent(componentToAdd, group.get().getPath());
            try {
                if (StringUtils.isEmpty(componentAdded.getId())) {
                    throw new IllegalStateException("Component'id should be defined");
                } else if (StringUtils.isEmpty((CharSequence) componentAdded.getMetadata().get("groupId"))) {
                    throw new IllegalStateException("Metadata '" + METADATA_GROUP_ID + "' of component should be defined");
                } else if (StringUtils.isEmpty((CharSequence) componentAdded.getMetadata().get("groupPath"))) {
                    throw new IllegalStateException("Metadata '" + METADATA_GROUP_PATH + "' of component should be defined");
                }
            } catch(Exception e) {
                manageError(componentAdded, e);
                throw e;
            }

            final Session session = kathraSessionManager.getCurrentSession();
            CompletableFuture.runAsync(() -> {
                try {
                    kathraSessionManager.handleSession(session);
                    // CREATE API REPOSITORY
                    SourceRepository sourceRepositoryApi = createSourceRepositoryApi(componentAdded, callback);

                    // CREATE COMPONENT LIBRARIES FOR EACH PROGRAMMING LANGUAGES AND LIBRARIES TYPES
                    for(Asset.LanguageEnum languageProgramming : Library.LanguageEnum.values()){
                        for(Library.TypeEnum libraryType : Library.TypeEnum.values()) {
                            Thread.sleep(200);
                            createLibrary(componentAdded, languageProgramming, libraryType, callback);
                        }
                    }
                } catch (Exception e) {
                    manageError(componentAdded, e);
                } finally {
                    //kathraSessionManager.deleteSession();
                }
            });
            return componentAdded;
        } catch(Exception e) {
            manageError(component, e);
            throw e;
        }
    }


    public void delete(Component component, boolean force, boolean purge) throws ApiException {
        Component componentToDeleted = componentsClient.getComponent(component.getId());
        if (isDeleted(componentToDeleted)) {
            return;
        }
        if (componentToDeleted.getImplementations().size() > 0 && !force) {
            throw new IllegalStateException("Component "+component.getId()+" contains implementations, delete its implementations before");
        }
        try {
            final AtomicReference<ApiException> exceptionFound = new AtomicReference<>();
            final Session session = kathraSessionManager.getCurrentSession();
            componentToDeleted.getImplementations().parallelStream().forEach(implementation -> {
                kathraSessionManager.handleSession(session);
                try {
                    implementationService.delete(implementation, purge);
                } catch (ApiException e) {
                    exceptionFound.set(e);
                }
            });
            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            }

            componentToDeleted.getVersions().parallelStream().forEach(apiVersion -> {
                kathraSessionManager.handleSession(session);
                try {
                    apiVersionService.delete(apiVersion, force, purge);
                } catch (ApiException e) {
                    exceptionFound.set(e);
                }
            });
            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            }
            if (componentToDeleted.getApiRepository() != null){
                sourceRepositoryService.delete(componentToDeleted.getApiRepository(), purge);
            }
            componentToDeleted.getLibraries().parallelStream().forEach(library -> {
                kathraSessionManager.handleSession(session);
                try {
                    libraryService.delete(library, force, purge);
                } catch (ApiException e) {
                    exceptionFound.set(e);
                }
            });
            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            }

            componentsClient.deleteComponent(componentToDeleted.getId());
            component.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(component, e);
            throw e;
        }
    }

    public synchronized boolean validate(Component component, Runnable callback) {
        try {
            Component componentUpdated = componentsClient.getComponent(component.getId());
            if (isReady(componentUpdated)) {
                return true;
            }
            boolean apiRepositoryReady = checkRepositoryIsReady(componentUpdated);
            boolean librariesReady = checkLibraryIsReady(componentUpdated);
            logger.info("validate component id=" + componentUpdated.getId() + ", apiRepositoryReady=" + apiRepositoryReady + ", librariesReady=" + librariesReady);
            if (apiRepositoryReady && librariesReady) {
                updateStatus(componentUpdated, Resource.StatusEnum.READY);
                if (callback != null) {
                    callback.run();
                }
                return true;
            }
        } catch(Exception e) {
            logger.error("Unable to validate post creating", e);
            manageError(component, e);
        }
        return false;
    }

    private synchronized boolean checkRepositoryIsReady(Component component) throws ApiException {
        if (component.getApiRepository() == null || component.getApiRepository().getId() == null){
            return false;
        }
        SourceRepository sourceRepository = sourceRepositoryService.getById(component.getApiRepository().getId()).orElse(null);
        throwExceptionIfError(sourceRepository);
        return isReady(sourceRepository);
    }

    private synchronized boolean checkLibraryIsReady(Component component) throws ApiException {
        if (component.getLibraries() == null || component.getLibraries().isEmpty()) {
            return false;
        } else if (component.getLibraries().size() < Library.LanguageEnum.values().length * Library.TypeEnum.values().length) {
            logger.info("All libraries are not initialized : " + component.getLibraries().size());
            return false;
        }
        for(Library item:component.getLibraries()){
            if (item.getId() == null){
                return false;
            }
            Optional<Library> library = libraryService.getById(item.getId());
            if (library.isPresent()) {
                throwExceptionIfError(library.get());
            }
            if (!library.isPresent() || !isReady(library.get())){
                logger.info("Library '"+item.getId()+"' is not ready or not exist.");
                return false;
            }
        }
        return true;
    }

    public void patch(Component component) throws ApiException {
        componentsClient.updateComponentAttributes(component.getId(), component);
    }

    private Library createLibrary(Component component, Library.LanguageEnum languageProgramming, Library.TypeEnum typeLibrary, Runnable afterReadyCallBack) throws ApiException {
        Runnable callback = () -> validate(component, afterReadyCallBack);
        return libraryService.add(component, languageProgramming, typeLibrary, callback);
    }

    private SourceRepository createSourceRepositoryApi(Component component, Runnable afterReadyCallBack) throws ApiException {

        String[] deploysKeys = {(String) component.getMetadata().get(METADATA_GROUP_ID)};
        String groupPathApi = component.getMetadata().get(METADATA_GROUP_PATH) + "/components/" + component.getName() + "/api";

        SourceRepository sourceRepositoryApi =  sourceRepositoryService.create(component.getName(), groupPathApi, deploysKeys, () -> validate(component, afterReadyCallBack));

        component.setApiRepository(sourceRepositoryApi);
        componentsClient.updateComponentAttributes(component.getId(), new Component().id(component.getId()).apiRepository(sourceRepositoryApi));
        return sourceRepositoryApi;
    }

    @Override
    public Optional<Component> getById(String componentId) throws ApiException {
        Component component = componentsClient.getComponent(componentId);
        return component == null ? Optional.empty() : Optional.of(component);
    }

    public Optional<Component> getByNameAndGroupPath(String componentName, String groupPath) throws ApiException {
        return componentsClient .getComponents()
                .stream()
                .filter(existingComponent -> componentName != null && componentName.equals(existingComponent.getName()) &&
                        groupPath != null && groupPath.equals(existingComponent.getMetadata().get(METADATA_GROUP_PATH)))
                .findFirst();
    }

    public List<Component> getAllComponentsWithApiVersions() throws ApiException {

        List<Component> components = componentsClient.getComponents();

        if (components == null || components.isEmpty()) return new ArrayList<>();

        // Cleaning the default provided apiversions
        for (Component component : components)
            component.setVersions(new LinkedList<>());

        // Converting list to map to optimize accesses
        Map<String, Component> componentMap =
                components.stream().collect(Collectors.toMap(Component::getId, component -> component));

        // Only one call to retrieve components APIVersions
        List<ApiVersion> apiVersions = apiVersionService.getApiVersions(components);

        // Completing components with their related APIVersions
        for (ApiVersion apiVersion : apiVersions) {
            Component apiComponent = componentMap.get(apiVersion.getComponent().getId());
            if (apiComponent != null)
                apiComponent.addVersionsItem(apiVersion);
        }
        return components;
    }

    @Override
    public List<Component> getAll() throws ApiException {
        return componentsClient.getComponents();
    }

}
