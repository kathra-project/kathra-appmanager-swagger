/*
 * Copyright (c) 2020. The Kathra Authors.
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
 *    IRT SystemX (https://www.kathra.org/)
 *
 */

package org.kathra.appmanager.service.reconciler;

import org.apache.log4j.Logger;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.catalogentry.CatalogEntryService;
import org.kathra.appmanager.catalogentrypackage.CatalogEntryPackageService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.libraryapiversion.LibraryApiVersionService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.ApiVersionsService;
import org.kathra.appmanager.service.CatalogEntriesService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.CatalogEntry;
import org.kathra.core.model.CatalogEntryPackage;
import org.kathra.core.model.Resource;
import org.kathra.resourcemanager.client.*;
import org.kathra.utils.ApiException;

public class ResourceReconciler {

    private static Logger logger = Logger.getLogger(ResourceReconciler.class);

    private ComponentsClient componentsClient;
    private ComponentService componentService;

    private ImplementationsClient implementationsClient;
    private ImplementationService implementationService;

    private CatalogEntriesClient catalogEntriesClient;
    private CatalogEntryService catalogEntriesService;

    private CatalogEntryPackagesClient catalogEntryPackagesClient;
    private CatalogEntryPackageService catalogEntryPackageService;

    private ImplementationVersionsClient implementationVersionClient;
    private ImplementationVersionService implementationVersionService;

    private LibrariesClient librariesClient;
    private LibraryService libraryService;

    private PipelinesClient pipelinesClient;
    private PipelineService pipelineService;

    private SourceRepositoriesClient sourceRepositoriesClient;
    private SourceRepositoryService sourceRepositoryService;

    private ApiVersionsClient apiVersionsClient;
    private ApiVersionService apiVersionService;

    private LibraryApiVersionsClient libraryApiVersionsClient;
    private LibraryApiVersionService libraryApiVersionService;

    public ResourceReconciler(ServiceInjection service) {
        configure(service);
    }

    public void configure(ServiceInjection service) {
        this.componentsClient = new ComponentsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.componentService = service.getService(ComponentService.class);

        this.implementationsClient = new ImplementationsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.implementationService = service.getService(ImplementationService.class);

        this.catalogEntriesClient = new CatalogEntriesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.catalogEntriesService = service.getService(CatalogEntryService.class);

        this.catalogEntryPackagesClient = new CatalogEntryPackagesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.catalogEntryPackageService = service.getService(CatalogEntryPackageService.class);

        this.librariesClient = new LibrariesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.libraryService = service.getService(LibraryService.class);

        this.apiVersionsClient = new ApiVersionsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.apiVersionService = service.getService(ApiVersionService.class);

        this.pipelinesClient = new PipelinesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.pipelineService = service.getService(PipelineService.class);

        this.libraryApiVersionsClient = new LibraryApiVersionsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.libraryApiVersionService = service.getService(LibraryApiVersionService.class);

        this.implementationVersionClient = new ImplementationVersionsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.implementationVersionService = service.getService(ImplementationVersionService.class);

        this.sourceRepositoriesClient = new SourceRepositoriesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.sourceRepositoryService = service.getService(SourceRepositoryService.class);
    }

    public void processForGlobalResources() throws ApiException {

        sourceRepositoriesClient.getSourceRepositories()
                .stream()
                .filter(sourceRepository -> !sourceRepositoryService.isReady(sourceRepository))
                .forEach(sourceRepository -> {
                    try {
                        sourceRepositoryService.tryToReconcile(sourceRepository);
                    } catch (ApiException e) {
                        manageException(sourceRepository, e);
                    }
                });

        pipelinesClient.getPipelines()
                .stream()
                .filter(pipeline -> !pipelineService.isReady(pipeline))
                .forEach(pipeline -> {
                    try {
                        pipelineService.tryToReconcile(pipeline);
                    } catch (ApiException e) {
                        manageException(pipeline, e);
                    }
                });
    }

    public void processForGroupResource() throws Exception {

        librariesClient.getLibraries()
                .stream()
                .filter(library -> !libraryService.isReady(library))
                .forEach(library -> {
                    try {
                        libraryService.tryToReconcile(library);
                    } catch (Exception e) {
                        manageException(library, e);
                    }
                });

        componentsClient.getComponents()
                .stream()
                .filter(component -> !componentService.isReady(component))
                .forEach(component -> {
                    try {
                        componentService.tryToReconcile(component);
                    } catch (Exception e) {
                        manageException(component, e);
                    }
                });
        implementationsClient.getImplementations()
                .stream()
                .filter(impl -> !implementationService.isReady(impl))
                .forEach(impl -> {
                    try {
                        implementationService.tryToReconcile(impl);
                    } catch (Exception e) {
                        manageException(impl, e);
                    }
                });

        implementationVersionClient.getImplementationVersions()
                .stream()
                .filter(impl -> !implementationVersionService.isReady(impl))
                .forEach(impl -> {
                    try {
                        implementationVersionService.tryToReconcile(impl);
                    } catch (Exception e) {
                        manageException(impl, e);
                    }
                });

        libraryApiVersionsClient.getLibraryApiVersions()
                .stream()
                .filter(lib -> !libraryApiVersionService.isReady(lib))
                .forEach(lib -> {
                    try {
                        libraryApiVersionService.tryToReconcile(lib);
                    } catch (Exception e) {
                        manageException(lib, e);
                    }
                });

        apiVersionsClient.getApiVersions()
                .stream()
                .filter(impl -> !apiVersionService.isReady(impl))
                .forEach(impl -> {
                    try {
                        apiVersionService.tryToReconcile(impl);
                    } catch (Exception e) {
                        manageException(impl, e);
                    }
                });

        catalogEntriesClient.getCatalogEntries()
                .stream()
                .filter(entry -> !catalogEntriesService.isReady(entry))
                .forEach(entry -> {
                    try {
                        catalogEntriesService.tryToReconcile(entry);
                    } catch (Exception e) {
                        manageException(entry, e);
                    }
                });
        catalogEntryPackagesClient.getCatalogEntryPackages()
                .stream()
                .filter(entry -> !catalogEntryPackageService.isReady(entry))
                .forEach(entry -> {
                    try {
                        catalogEntryPackageService.tryToReconcile(entry);
                    } catch (Exception e) {
                        manageException(entry, e);
                    }
                });
    }

    private void manageException(Resource resource, Exception exception) {
        logger.warn("Unable to reconcile resource " + resource.getClass().getName()+" "+resource.getId());
        exception.printStackTrace();
    }
}
