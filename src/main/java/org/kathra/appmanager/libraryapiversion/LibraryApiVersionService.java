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
package org.kathra.appmanager.libraryapiversion;

import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.codegen.model.CodeGenTemplate;
import org.kathra.codegen.model.CodeGenTemplateArgument;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.LibraryApiVersionsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraException;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author julien.boubechtoula
 */
public class LibraryApiVersionService extends AbstractResourceService<LibraryApiVersion> {

    private LibraryApiVersionsClient resourceManager;
    private ApiVersionService apiVersionService;
    private SourceRepositoryService sourceRepositoryService;
    private LibraryService libraryService;
    private PipelineService pipelineService;
    private CodegenClient codegenClient;

    private final String METADATA_LAST_BUILD_NUMBER = "last-build-number";

    public LibraryApiVersionService() {

    }

    public void configure(ServiceInjection serviceInjection) {
        super.configure(serviceInjection);
        this.resourceManager = new LibraryApiVersionsClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
        sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        pipelineService = serviceInjection.getService(PipelineService.class);
        libraryService = serviceInjection.getService(LibraryService.class);
        codegenClient = new CodegenClient(serviceInjection.getConfig().getCodegenUrl(), serviceInjection.getSessionManager());
        apiVersionService = serviceInjection.getService(ApiVersionService.class);
    }

    public LibraryApiVersionService(LibraryApiVersionsClient resourceManager, CodegenClient codegenClient, LibraryService libraryService, PipelineService pipelineService, SourceRepositoryService sourceRepositoryService, ApiVersionService apiVersionService, KathraSessionManager kathraSessionManager) {
        this.resourceManager = resourceManager;
        this.libraryService = libraryService;
        this.pipelineService = pipelineService;
        this.sourceRepositoryService = sourceRepositoryService;
        this.codegenClient = codegenClient;
        this.apiVersionService = apiVersionService;
        super.kathraSessionManager = kathraSessionManager;
    }

    @Override
    protected void patch(LibraryApiVersion object) throws ApiException {
        resourceManager.updateLibraryApiVersionAttributes(object.getId(), object);
    }

    @Override
    public Optional<LibraryApiVersion> getById(String id) throws ApiException {
        LibraryApiVersion libraryApiVersion = resourceManager.getLibraryApiVersion(id);
        return libraryApiVersion == null ? Optional.empty() : Optional.of(libraryApiVersion);
    }

    @Override
    public List<LibraryApiVersion> getAll() throws ApiException {
        return resourceManager.getLibraryApiVersions();
    }

    public Optional<LibraryApiVersion> findByApiVersionAndLibrary(ApiVersion apiVersion, Library library) throws ApiException {
        // TO BE IMPROVED
        final Session session = kathraSessionManager.getCurrentSession();
        return apiVersion.getLibrariesApiVersions().parallelStream().map(apiLib -> {
            try {
                kathraSessionManager.handleSession(session);
                return this.getById(apiLib.getId()).get();
            } catch (ApiException e) {
                e.printStackTrace();
                return null;
            }
        }).filter(apiLib -> apiLib.getLibrary().getId().equals(library.getId())).findFirst();
    }

    public LibraryApiVersion create(ApiVersion apiVersion, Library library, File apiFile, Runnable callback) throws ApiException {

        if (apiVersion == null) {
            throw new IllegalArgumentException("ApiVersion is null");
        }
        if (StringUtils.isEmpty(apiVersion.getVersion())) {
            throw new IllegalArgumentException("ApiVersion's version is null or empty");
        }
        if (!apiVersion.getMetadata().containsKey(ApiVersionService.METADATA_API_GROUP_ID) || StringUtils.isEmpty((CharSequence) apiVersion.getMetadata().get(ApiVersionService.METADATA_API_GROUP_ID))) {
            throw new IllegalArgumentException("ApiVersion's "+ApiVersionService.METADATA_API_GROUP_ID+" is null or empty");
        }
        if (!apiVersion.getMetadata().containsKey(ApiVersionService.METADATA_API_ARTIFACT_NAME) || StringUtils.isEmpty((CharSequence) apiVersion.getMetadata().get(ApiVersionService.METADATA_API_ARTIFACT_NAME))) {
            throw new IllegalArgumentException("ApiVersion's "+ApiVersionService.METADATA_API_ARTIFACT_NAME+" is null or empty");
        }
        if (library == null) {
            throw new IllegalArgumentException("Library is null");
        }
        if (apiFile == null) {
            throw new IllegalArgumentException("File is null or empty");
        }
        Library libraryWithDetails = libraryService.getById(library.getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find Library '"+library.getId()+"'"));
        if (!isReady(libraryWithDetails)) {
            throw new IllegalStateException("Library '"+library.getId()+"' is not READY");
        }
        if (libraryWithDetails.getSourceRepository() == null) {
            throw new IllegalStateException("Library '"+library.getId()+"' doesn't have a sourceRepository");
        }

        if (findByApiVersionAndLibrary(apiVersion, library).isPresent()) {
            throw new IllegalArgumentException("LibraryApiVersion linked ApiVersion '"+apiVersion.getId()+"' and Library '"+library.getId()+"' already exists");
        }

        SourceRepository sourceRepositoryWithDetails = sourceRepositoryService.getById(libraryWithDetails.getSourceRepository().getId()).orElseThrow(() -> new IllegalStateException("Unable to find SourceRepository '"+libraryWithDetails.getSourceRepository().getId()+"'"));
        if (!isReady(sourceRepositoryWithDetails)) {
            throw new IllegalStateException("SourceRepository '"+sourceRepositoryWithDetails.getId()+"' is not READY");
        }

        LibraryApiVersion libraryApiToAdd = new LibraryApiVersion() .name(libraryWithDetails.getName() + "-" + apiVersion.getName())
                                                                    .status(Resource.StatusEnum.PENDING)
                                                                    .apiVersion(new ApiVersion().id(apiVersion.getId()))
                                                                    .library(new Library().id(library.getId())).pipelineStatus(LibraryApiVersion.PipelineStatusEnum.PENDING).apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.PENDING);

        final LibraryApiVersion libraryApiVersion = resourceManager.addLibraryApiVersion(libraryApiToAdd);

        if (StringUtils.isEmpty(libraryApiVersion.getId())) {
            throw new IllegalStateException("LibraryApiVersion's id should be defined by resourceManager");
        }
        if (!isPending(libraryApiVersion)) {
            throw new IllegalStateException("LibraryApiVersion should have status PENDING");
        }
        if (libraryApiVersion.getLibrary() == null || libraryApiVersion.getApiVersion() == null) {
            throw new IllegalStateException("LibraryApiVersion should have a ApiVersion and Library defined");
        }

        final Session session = kathraSessionManager.getCurrentSession();
        CompletableFuture.runAsync(() -> {
            try {
                kathraSessionManager.handleSession(session);
                // defined LibraryApiVersion with Library and ApiVersion detailled
                libraryApiVersion.library(libraryWithDetails.sourceRepository(sourceRepositoryWithDetails));
                libraryApiVersion.apiVersion(apiVersion);
                generateAndUpdateSrc(libraryApiVersion, apiFile, callback);
            } catch (Exception e) {
                manageError(libraryApiVersion, e);
                if (callback != null) {
                    callback.run();
                }
            } finally {
                //kathraSessionManager.deleteSession();
            }
        });
        logger.info("ApiVersionLibrary created with id " + libraryApiVersion.getId());
        return libraryApiVersion;
    }

    public LibraryApiVersion update(LibraryApiVersion libraryApiVersion, File apiFile, Runnable callback) throws ApiException {
        if (libraryApiVersion == null) {
            throw new IllegalArgumentException("LibraryApiVersion is null");
        }
        if (StringUtils.isEmpty(libraryApiVersion.getId())) {
            throw new IllegalArgumentException("LibraryApiVersion's id is null or empty");
        }
        final LibraryApiVersion libraryApiVersionWithDetails = resourceManager.getLibraryApiVersion(libraryApiVersion.getId());

        if (apiFile == null) {
            throw new IllegalArgumentException("File is null or empty");
        }
        if (!isReady(libraryApiVersionWithDetails)) {
            //throw new IllegalStateException("LibraryApiVersion '" + libraryApiVersionWithDetails.getId() + "' '" + libraryApiVersionWithDetails.getName() + "' is not READY");
        }

        final Library libraryWithDetails = libraryService.getById(libraryApiVersionWithDetails.getLibrary().getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find Library '"+libraryApiVersion.getLibrary().getId()+"'"));
        if (!isReady(libraryWithDetails)) {
            throw new IllegalStateException("Library '"+libraryWithDetails.getId()+"' is not READY");
        }
        if (libraryWithDetails.getSourceRepository() == null) {
            throw new IllegalStateException("Library '"+libraryWithDetails.getId()+"' doesn't have a sourceRepository");
        }
        final SourceRepository sourceRepositoryWithDetails = sourceRepositoryService.getById(libraryWithDetails.getSourceRepository().getId()).orElseThrow(() -> new IllegalStateException("Unable to find SourceRepository '"+libraryWithDetails.getSourceRepository().getId()+"'"));
        if (!isReady(sourceRepositoryWithDetails)) {
            throw new IllegalStateException("SourceRepository '"+sourceRepositoryWithDetails.getId()+"' is not READY");
        }
        final ApiVersion apiVersionWithDetails = apiVersionService.getById(libraryApiVersionWithDetails.getApiVersion().getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find ApiVersion '"+libraryApiVersion.getApiVersion().getId()+"'"));



        patch(new LibraryApiVersion().id(libraryApiVersionWithDetails.getId()).apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.UPDATING).status(Resource.StatusEnum.UPDATING));

        final Session session = kathraSessionManager.getCurrentSession();
        CompletableFuture.runAsync(() -> {
            try {
                kathraSessionManager.handleSession(session);
                LibraryApiVersion libraryApiVersionDetails = new LibraryApiVersion().id(libraryApiVersionWithDetails.getId())
                                                                                    .library(libraryWithDetails.sourceRepository(sourceRepositoryWithDetails))
                                                                                    .apiVersion(apiVersionWithDetails);
                generateAndUpdateSrc(libraryApiVersionDetails, apiFile, callback);
            } catch (Exception e) {
                manageError(libraryApiVersionWithDetails, e);
                if (callback != null) {
                    callback.run();
                }
            } finally {
                //kathraSessionManager.deleteSession();
            }
        });

        return libraryApiVersion.status(Resource.StatusEnum.UPDATING).apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.UPDATING);
    }

    private CodeGenTemplate getCodeGenTemplate(LibraryApiVersion libraryApiVersion, File apiFile) throws ApiException {
        final String artifactGroup = (String) libraryApiVersion.getApiVersion().getMetadata().get(ApiVersionService.METADATA_API_GROUP_ID);
        final String artifactName = (String) libraryApiVersion.getApiVersion().getMetadata().get(ApiVersionService.METADATA_API_ARTIFACT_NAME);
        try {
            final String content = new String ( java.nio.file.Files.readAllBytes( java.nio.file.Paths.get(apiFile.getAbsolutePath()) ) );
            return new CodeGenTemplate().name("LIBRARY_"+libraryApiVersion.getLibrary().getLanguage().toString()+"_REST_"+libraryApiVersion.getLibrary().getType())
                                        .addArgumentsItem(new CodeGenTemplateArgument().key("NAME").value(artifactName))
                                        .addArgumentsItem(new CodeGenTemplateArgument().key("GROUP").value(artifactGroup))
                                        .addArgumentsItem(new CodeGenTemplateArgument().key("SWAGGER2_SPEC").value(content))
                                        .addArgumentsItem(new CodeGenTemplateArgument().key("VERSION").value(libraryApiVersion.getApiVersion().getVersion()));
        } catch(Exception e) {
            throw new ApiException("Unable to read api file");
        }
    }

    private void generateAndUpdateSrc(LibraryApiVersion libraryApiVersion, File apiFile, Runnable callback) throws ApiException {
        try {
            final String artifactGroup = (String) libraryApiVersion.getApiVersion().getMetadata().get(ApiVersionService.METADATA_API_GROUP_ID);
            final String artifactName = (String) libraryApiVersion.getApiVersion().getMetadata().get(ApiVersionService.METADATA_API_ARTIFACT_NAME);
            final File sourceGenerated = codegenClient.generateFromTemplate(getCodeGenTemplate(libraryApiVersion, apiFile));
            
            if (sourceGenerated == null) {
                throw new IllegalArgumentException("CodeGenClient's File is null");
            }

            if (!isReady(libraryApiVersion.getLibrary().getSourceRepository())) {
                throw new IllegalStateException("SourceRepository '"+libraryApiVersion.getLibrary().getSourceRepository().getId()+"' is not READY");
            }
            try {
                SourceRepositoryCommit commit = sourceRepositoryService.commitArchiveAndTag(libraryApiVersion.getLibrary().getSourceRepository(), ApiVersionService.DEFAULT_BRANCH, sourceGenerated, ".", libraryApiVersion.getApiVersion().getVersion());
                if (StringUtils.isEmpty(commit.getId())) {
                    throw new IllegalStateException("SourceManager doesn't return commit with id");
                }
            } catch(ApiException e) {
                // PRECONDITION FAILED EXCEPTION IF THROWS WHEN SOURCE REPOSITORY IS ALREADY UPDATED
                if (e.getCode() != KathraException.ErrorCode.PRECONDITION_FAILED.getCode()) {
                    throw e;
                }
            }
            this.patch(new LibraryApiVersion().id(libraryApiVersion.getId()).apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.READY));
            if (callback != null) {
                callback.run();
            }
        } catch(Exception e){
            patch(new LibraryApiVersion().id(libraryApiVersion.getId()).apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.ERROR));
            throw e;
        }
    }

    public Build build(final LibraryApiVersion libraryApiVersion, Runnable callback) throws ApiException {
        if (libraryApiVersion == null) {
            throw new IllegalArgumentException("LibraryApiVersion is null");
        }
        LibraryApiVersion libraryApiVersionWithDetails = this.getById(libraryApiVersion.getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find LibraryApiVersion with id "+libraryApiVersion.getId()));
        if (isError(libraryApiVersionWithDetails)) {
            throw new IllegalArgumentException("LibraryApiVersion has status "+libraryApiVersionWithDetails.getStatus());
        }
        if (!LibraryApiVersion.ApiRepositoryStatusEnum.READY.equals(libraryApiVersionWithDetails.getApiRepositoryStatus())) {
            throw new IllegalArgumentException("LibraryApiVersion's repository status is not READY");
        }
        if (libraryApiVersionWithDetails.getApiVersion() == null) {
            throw new IllegalArgumentException("LibraryApiVersion's ApiVersion is null");
        }
        if (libraryApiVersionWithDetails.getLibrary() == null) {
            throw new IllegalArgumentException("LibraryApiVersion's Library is null");
        }
        ApiVersion apiVersionWithDetails = apiVersionService.getById(libraryApiVersion.getApiVersion().getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find ApiVersion with id "+libraryApiVersion.getApiVersion().getId()));
        if (StringUtils.isEmpty(apiVersionWithDetails.getVersion())) {
            throw new IllegalArgumentException("LibraryApiVersion's version is null or empty");
        }

        Optional<Library> library = libraryService.getById(libraryApiVersionWithDetails.getLibrary().getId());
        Optional<Pipeline> pipeline = pipelineService.getById(library.get().getPipeline().getId());
        if (!pipeline.isPresent()){
            throw new IllegalStateException("Pipeline with id '"+library.get().getPipeline().getId()+"' for apiVersion "+library.get().getId() + " not found.");
        }
        logger.info("libraryApiVersion '" + libraryApiVersion.getId() + "' '" + libraryApiVersion.getName() + "' - build pipeline " + pipeline.get().getPath());

        Runnable callbackIfBuildIsFinished = () -> validateBuilding(libraryApiVersionWithDetails, pipeline.get(), callback);

        Build build = pipelineService.build(pipeline.get(), ApiVersionService.DEFAULT_BRANCH, null, callbackIfBuildIsFinished);
        libraryApiVersionWithDetails.setPipelineStatus(LibraryApiVersion.PipelineStatusEnum.PENDING);
        libraryApiVersionWithDetails.putMetadataItem(METADATA_LAST_BUILD_NUMBER, build.getBuildNumber());
        patch(libraryApiVersionWithDetails);
        return build;
    }

    /**
     * Executed when building is finished (Build's status should be READY or ERROR)
     * @param libraryApiVersion
     * @param pipeline
     * @param callback
     * @return
     */
    private boolean validateBuilding(LibraryApiVersion libraryApiVersion, Pipeline pipeline, Runnable callback) {

        try {
            if (!libraryApiVersion.getMetadata().containsKey(METADATA_LAST_BUILD_NUMBER)) {
                throw new IllegalStateException("Unable to get metadata '" + METADATA_LAST_BUILD_NUMBER + "' for LibraryApiVersion " + libraryApiVersion.getId());
            }

            Build build = pipelineService.getBuild(pipeline, (String) libraryApiVersion.getMetadata().get(METADATA_LAST_BUILD_NUMBER));
            switch (build.getStatus()) {
                case SCHEDULED:
                case PROCESSING:
                    throw new IllegalStateException("LibraryApiVersion '" + libraryApiVersion.getId() + "' '" + libraryApiVersion.getName() + " building should be finished");
                case SUCCESS:
                    logger.info("LibraryApiVersion '" + libraryApiVersion.getId() + "' '" + libraryApiVersion.getName() + "' has been build");
                    libraryApiVersion.pipelineStatus(LibraryApiVersion.PipelineStatusEnum.READY);
                    patch(new LibraryApiVersion().id(libraryApiVersion.getId()).pipelineStatus(LibraryApiVersion.PipelineStatusEnum.READY));
                    if (!isReady(libraryApiVersion)) {
                        updateStatus(libraryApiVersion, Resource.StatusEnum.READY);
                    }
                    if (callback != null) {
                        callback.run();
                    }
                    return true;
                case FAILED:
                    patch(new LibraryApiVersion().id(libraryApiVersion.getId()).pipelineStatus(LibraryApiVersion.PipelineStatusEnum.ERROR));
                    throw new Exception("An error occurred during building libraryApiVersion '" + libraryApiVersion.getId() + "' '" + libraryApiVersion.getName());
                default:
                    throw new IllegalStateException("LibraryApiVersion '" + libraryApiVersion.getId() + "' '" + libraryApiVersion.getName() + " building have not implemented status "+build.getStatus());
            }
        } catch(Exception e) {
            manageError(libraryApiVersion, e);
            if (callback != null) {
                callback.run();
            }
            return false;
        }
    }

    public void delete(LibraryApiVersion libApiVersion, boolean purge) throws ApiException {
        try {
            LibraryApiVersion libApiVersionToDeleted = resourceManager.getLibraryApiVersion(libApiVersion.getId());
            if (isDeleted(libApiVersionToDeleted)) {
                return;
            }
            resourceManager.deleteLibraryApiVersion(libApiVersionToDeleted.getId());
            libApiVersion.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(libApiVersion, e);
            throw e;
        }
    }
}
