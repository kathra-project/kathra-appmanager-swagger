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
package org.kathra.appmanager.apiversion;

import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.libraryapiversion.LibraryApiVersionService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ApiVersionsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraException;
import org.kathra.utils.KathraSessionManager;
import io.swagger.annotations.Api;
import javassist.NotFoundException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author julien.boubechtoula
 */
public class ApiVersionService extends AbstractResourceService<ApiVersion> {

    private ComponentService componentService;
    private ApiVersionsClient resourceManager;
    private OpenApiParser openApiParser;
    private SourceRepositoryService sourceRepositoryService;
    private LibraryService libraryService;
    private LibraryApiVersionService libraryApiVersionService;
    private ImplementationVersionService implementationVersionService;

    public static final String METADATA_API_GROUP_ID = "artifact-groupId";
    public static final String METADATA_API_ARTIFACT_NAME = "artifact-artifactName";

    public static final String DEFAULT_BRANCH = "dev";
    public static final String API_FILENAME = "swagger.yaml";

    private static final Pattern PATTERN_NAME = Pattern.compile("^[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}$");

    public ApiVersionService() {

    }

    public void configure(ServiceInjection service) {
        super.configure(service);
        this.resourceManager = new ApiVersionsClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.sourceRepositoryService = service.getService(SourceRepositoryService.class);
        this.libraryService = service.getService(LibraryService.class);
        this.openApiParser = new OpenApiParser();
        this.componentService = service.getService(ComponentService.class);
        this.libraryApiVersionService = service.getService(LibraryApiVersionService.class);
        this.implementationVersionService = service.getService(ImplementationVersionService.class);
    }

    public ApiVersionService(ApiVersionsClient resourceManager, ComponentService componentService, OpenApiParser openApiParser, LibraryService libraryService, LibraryApiVersionService libraryApiVersionService, SourceRepositoryService sourceRepositoryService, KathraSessionManager kathraSessionManager, ImplementationVersionService implementationVersionService) {
        this.componentService = componentService;
        this.resourceManager = resourceManager;
        this.openApiParser = openApiParser;
        this.libraryService = libraryService;
        this.libraryApiVersionService = libraryApiVersionService;
        this.sourceRepositoryService = sourceRepositoryService;
        super.kathraSessionManager = kathraSessionManager;
        this.implementationVersionService = implementationVersionService;
    }

    public ApiVersion create(String componentId, File apiFile, Runnable callback) throws Exception {
        if (StringUtils.isEmpty(componentId)) {
            throw new IllegalArgumentException("componentId is null or empty");
        }
        Component component = componentService.getById(componentId).orElseThrow(() -> new IllegalArgumentException("Component '" + componentId + "' doesn't exist"));
        return create(component, apiFile, callback);
    }

    public File getFile(ApiVersion apiVersion) throws ApiException, NotFoundException {
        Component component = componentService.getById(apiVersion.getComponent().getId()).orElseThrow(() -> new NotFoundException("Component not found"));
        SourceRepository sourceRepository =  sourceRepositoryService.getById(component.getApiRepository().getId()).orElseThrow(() -> new NotFoundException("Component not found"));
        try {
            return sourceRepositoryService.getFile(sourceRepository, apiVersion.getVersion(), "swagger.yaml");
        } catch(Exception e) {
            return sourceRepositoryService.getFile(sourceRepository, apiVersion.getVersion(), "swagger.yml");
        }
    }

    public ApiVersion create(Component component, File apiFile, Runnable callback) throws ApiException, IOException {
        Component componentWithDetails = componentService.getById(component.getId()).orElseThrow(() -> new IllegalArgumentException("Component '" + component.getId() + "' doesn't exist"));

        if (!isReady(componentWithDetails)) {
            throw new IllegalStateException("Component '" + componentWithDetails.getId() + "' is not READY.");
        }

        final ApiVersion apiVersion;
        try {
            apiVersion = openApiParser.getApiVersionFromApiFile(apiFile);
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }

        if (!apiVersion.getMetadata().containsKey(METADATA_API_ARTIFACT_NAME) || StringUtils.isEmpty((String)apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME))) {

            // Add x-artifactName if not present
            String swagger = FileUtils.readFileToString(apiFile, "UTF-8");
            swagger = StringUtils.replace(swagger,"  x-groupId","  x-artifactName: "+componentWithDetails.getName().toLowerCase()+"\n  x-groupId");
            FileUtils.writeStringToFile(apiFile, swagger, StandardCharsets.UTF_8,false);

            apiVersion.getMetadata().put(METADATA_API_ARTIFACT_NAME, componentWithDetails.getName().toLowerCase());
        }

        //apiVersion.getMetadata().putIfAbsent(METADATA_API_GROUP_ID, ((String)component.getMetadata().get(ComponentService.METADATA_GROUP_PATH)).replaceAll("/",".").replaceAll("-","."));

        checkApiVersionFromApiFile(apiVersion);

        if (getApiVersion(componentWithDetails, apiVersion.getVersion()).isPresent()) {
            throw new IllegalArgumentException("Component with version '" + apiVersion.getVersion() + "' already existing");
        }

        List<ApiVersion> existingApiVersionWithSameArtifactIdentifier = getApiVersionByArtifact((String) apiVersion.getMetadata().get(METADATA_API_GROUP_ID), (String) apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME));
        Optional<Component> componentWithSameArtifactIdentifier = existingApiVersionWithSameArtifactIdentifier.parallelStream().filter(apiV -> !apiV.getComponent().getId().equals(component.getId())).map(ApiVersion::getComponent).findAny();
        if (componentWithSameArtifactIdentifier.isPresent()) {
            throw new IllegalArgumentException("A another component '" + componentWithSameArtifactIdentifier.get().getId() + "' using the same groupId and artifactId");
        }

        if (componentWithDetails.getMetadata() == null ||
                !componentWithDetails.getMetadata().containsKey(ComponentService.METADATA_API_GROUP_ID) ||
                !componentWithDetails.getMetadata().containsKey(ComponentService.METADATA_API_ARTIFACT_NAME)) {
            componentService.patch(new Component().id(componentWithDetails.getId())
                    .putMetadataItem(ComponentService.METADATA_API_GROUP_ID, apiVersion.getMetadata().get(METADATA_API_GROUP_ID))
                    .putMetadataItem(ComponentService.METADATA_API_ARTIFACT_NAME, apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME)));
        }

        apiVersion.component(new Component().id(componentWithDetails.getId()));
        apiVersion.released(false);
        apiVersion.apiRepositoryStatus(ApiVersion.ApiRepositoryStatusEnum.PENDING);

        ApiVersion apiVersionWithId = resourceManager.addApiVersion(apiVersion);
        final Session session = kathraSessionManager.getCurrentSession();

        File permApiFile = tmpFileToPermanentFile(apiFile);
        if (!permApiFile.getName().equals(API_FILENAME)) {
            throw new ApiException("Filename is not '" + API_FILENAME + "'");
        }
        CompletableFuture.runAsync(() -> {
            this.kathraSessionManager.handleSession(session);
            createLibrariesApiVersionUpdateSourceAndBuild(apiVersionWithId, permApiFile, callback);
        });

        return apiVersionWithId;
    }

    private File tmpFileToPermanentFile(File apiFile) throws IOException {
        File tmpFile = new File(apiFile.getParentFile().getPath()+File.separator+"AppManager-Swagger_"+apiFile.getName()+File.separator+API_FILENAME);
        FileUtils.copyFile(apiFile, tmpFile);
        return tmpFile;
    }

    private void checkApiVersionFromApiFile(ApiVersion apiVersion) {
        if (StringUtils.isEmpty(apiVersion.getVersion())) {
            throw new IllegalArgumentException("Version should be defined");
        } else if (!PATTERN_NAME.matcher(apiVersion.getVersion()).find()) {
            throw new IllegalArgumentException("Version do not respect nomenclature X.X.X (ex: 1.0.1)");
        } else if (StringUtils.isEmpty((CharSequence) apiVersion.getMetadata().get(METADATA_API_GROUP_ID))) {
            throw new IllegalArgumentException("Metadata " + METADATA_API_GROUP_ID + " not defined");
        } else if (StringUtils.isEmpty((CharSequence) apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME))) {
            throw new IllegalArgumentException("Metadata " + METADATA_API_ARTIFACT_NAME + " not defined");
        }
    }

    public ApiVersion update(ApiVersion apiVersion, File apiFile, Runnable callback) throws ApiException, IOException {

        if (apiVersion == null) {
            throw new IllegalArgumentException("ApiVersion is null.");
        } else if (apiFile == null) {
            throw new IllegalArgumentException("File is null.");
        // } else if (!isReady(apiVersion))
        //      throw new IllegalStateException("ApiVersion '" + apiVersion.getId() + "' is not READY.");
        } else if (apiVersion.isReleased()) {
            throw new IllegalStateException("ApiVersion '" + apiVersion.getId() + "' is already released.");
        }

        ApiVersion apiVersionFromFile = openApiParser.getApiVersionFromApiFile(apiFile);

        apiVersionFromFile.getMetadata().putIfAbsent(METADATA_API_ARTIFACT_NAME, apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME));
        apiVersionFromFile.getMetadata().putIfAbsent(METADATA_API_GROUP_ID, apiVersion.getMetadata().get(METADATA_API_GROUP_ID));

        checkApiVersionFromApiFile(apiVersion);

        if (!apiVersionFromFile.getVersion().equals(apiVersion.getVersion())) {
            throw new IllegalArgumentException("ApiVersion and apiFile have different version. ApiVersion=" + apiVersion.getVersion() + " apiFile=" + apiVersionFromFile.getVersion());
        } else if (!apiVersion.getMetadata().get(METADATA_API_GROUP_ID).equals(apiVersionFromFile.getMetadata().get(METADATA_API_GROUP_ID))) {
            throw new IllegalArgumentException("ApiVersion and apiFile have different artifact's groupId. ApiVersion=" + apiVersion.getMetadata().get(METADATA_API_GROUP_ID) + " apiFile=" + apiVersionFromFile.getMetadata().get(METADATA_API_GROUP_ID));
        } else if (!apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME).equals(apiVersionFromFile.getMetadata().get(METADATA_API_ARTIFACT_NAME))) {
            throw new IllegalArgumentException("ApiVersion and apiFile have different artifact's name. ApiVersion=" + apiVersion.getMetadata().get(METADATA_API_ARTIFACT_NAME) + " apiFile=" + apiVersionFromFile.getMetadata().get(METADATA_API_ARTIFACT_NAME));
        }

        updateStatus(apiVersion, Resource.StatusEnum.UPDATING);

        File permApiFile = tmpFileToPermanentFile(apiFile);
        if (!permApiFile.getName().equals(API_FILENAME)) {
            throw new ApiException("Filename is not '" + API_FILENAME + "'");
        }
        final Session session = kathraSessionManager.getCurrentSession();
        CompletableFuture.runAsync(() -> {
            try {
                kathraSessionManager.handleSession(session);
                patch(apiVersion.apiRepositoryStatus(ApiVersion.ApiRepositoryStatusEnum.UPDATING));
                updateSwaggerFileIntoApiRepository(apiVersion, permApiFile);
                for (LibraryApiVersion libraryApiVersion : apiVersion.getLibrariesApiVersions()) {
                    libraryApiVersionService.update(libraryApiVersion, permApiFile, () -> notifyWhenLibraryRepositoryIsUpdated(apiVersion, permApiFile, callback));
                }
            } catch (Exception e) {
                try {
                    patch(apiVersion.apiRepositoryStatus(ApiVersion.ApiRepositoryStatusEnum.ERROR));
                } catch (ApiException e1) {
                    deleteApiFile(permApiFile);
                }
                manageError(apiVersion, e);
                if (callback != null) {
                    callback.run();
                }
            } finally {
                //kathraSessionManager.deleteSession();
            }
        });

        return apiVersion;
    }

    private List<ApiVersion> getApiVersionByArtifact(String groupId, String artifactName) throws ApiException {
        return this.resourceManager.getApiVersions().parallelStream().filter(item ->
                artifactName.equals(item.getMetadata().get(METADATA_API_ARTIFACT_NAME)) &&
                        groupId.equals(item.getMetadata().get(METADATA_API_GROUP_ID))).collect(Collectors.toList());
    }

    private void createLibrariesApiVersionUpdateSourceAndBuild(ApiVersion apiVersion, File apiFile, Runnable callback) {
        try {
            updateSwaggerFileIntoApiRepository(apiVersion, apiFile);
            List<LibraryApiVersion> librariesApiVersion = new ArrayList<>();
            for (Library library : componentService.getById(apiVersion.getComponent().getId()).get().getLibraries()) {
                librariesApiVersion.add(createLibraryApiVersion(apiVersion, library, apiFile, callback));
            }
            patch(new ApiVersion().id(apiVersion.getId()).librariesApiVersions(librariesApiVersion));
        } catch (Exception e) {
            manageError(apiVersion, e);
            deleteApiFile(apiFile);
        }
    }

    /**
     * Update ApiFile for ApiVersion
     *
     * @param apiVersion
     * @param file
     * @throws ApiException
     */
    private void updateSwaggerFileIntoApiRepository(ApiVersion apiVersion, File file) throws ApiException, NotFoundException {
        Component component = componentService.getById(apiVersion.getComponent().getId()).orElseThrow(() -> new NotFoundException("Component not found"));
        SourceRepository sourceRepository = sourceRepositoryService.getById(component.getApiRepository().getId()).orElseThrow(() -> new NotFoundException("SourceRepository not found"));
        try {
            SourceRepositoryCommit commit = sourceRepositoryService.commitFileAndTag(sourceRepository, DEFAULT_BRANCH, file, "swagger.yaml", apiVersion.getVersion());
            if (commit == null || StringUtils.isEmpty(commit.getId())) {
                throw new IllegalStateException("A commit with id should be return by SourceRepositoryService");
            }
        } catch(ApiException e) {
            if (e.getCode() != KathraException.ErrorCode.NOT_MODIFIED.getCode()) {
                throw e;
            }
        }
    }

    /**
     * Create a LibraryApiVersion
     *
     * @param apiVersion
     * @param library
     * @param apiFile
     * @param callback
     * @return
     * @throws ApiException
     */
    private LibraryApiVersion createLibraryApiVersion(ApiVersion apiVersion, Library library, File apiFile, Runnable callback) throws ApiException {
        LibraryApiVersion libraryApiVersion = libraryApiVersionService.create(apiVersion, library, apiFile, () -> notifyWhenLibraryRepositoryIsUpdated(apiVersion, apiFile, callback));
        logger.info("apiVersion '" + apiVersion.getId() + "' - LibraryApiVersion created with id " + libraryApiVersion.getId());
        if (apiVersion.getLibrariesApiVersions() == null || !apiVersion.getLibrariesApiVersions().contains(libraryApiVersion)) {
            apiVersion.addLibrariesApiVersionsItem(libraryApiVersion);
        }
        return libraryApiVersion;
    }

    /**
     * Callback method when a LibraryApiVersion's SourceRepository is updated
     *
     * @param apiVersion
     * @param callback
     * @return
     */
    private synchronized boolean notifyWhenLibraryRepositoryIsUpdated(ApiVersion apiVersion, File apiFile, Runnable callback) {
        logger.debug("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - a library's repository is updated");
        try {
            ApiVersion apiVersionFromDb = this.getById(apiVersion.getId()).get();
            logger.debug("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - has status " + apiVersionFromDb.getStatus());
            logger.debug("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - has ApiRepositoryStatus " + apiVersionFromDb.getApiRepositoryStatus());
            if (apiVersionFromDb.getApiRepositoryStatus().equals(ApiVersion.ApiRepositoryStatusEnum.READY)) {
                return true;
            } else if (apiVersionFromDb.getApiRepositoryStatus().equals(ApiVersion.ApiRepositoryStatusEnum.ERROR)) {
                return false;
            }

            List<LibraryApiVersion> librariesApiVersionDetailed = new ArrayList<>();

            AtomicReference<Exception> exceptionFound = new AtomicReference<>();

            List<Library> librariesFromComponent = componentService.getById(apiVersion.getComponent().getId()).orElseThrow(() -> new IllegalStateException("Unable")).getLibraries();
            List<LibraryApiVersion> librariesApiVersions = apiVersionFromDb.getLibrariesApiVersions();
            if( librariesFromComponent.size() != librariesApiVersions.size() ){
                logger.debug("All libraries are not created yet expect: " + librariesFromComponent.size() + " / created: " + librariesApiVersions.size());
                return false;
            }

            final Session session = kathraSessionManager.getCurrentSession();
            // CHECK ALL LIBRARIES ARE UPDATED
            boolean libraryIsNotReadyFound = apiVersionFromDb.getLibrariesApiVersions().parallelStream().map(Resource::getId).filter(Objects::nonNull).anyMatch(id -> {
                try {
                    kathraSessionManager.handleSession(session);
                    LibraryApiVersion libraryApiVersion = libraryApiVersionService.getById(id).orElseThrow(() -> new IllegalStateException("Unable to find libraryApiVersion"));
                    if (!checkLibraryApiVersionIsUpdated(libraryApiVersion)) {
                        logger.debug("LibraryApiVersion: " + libraryApiVersion.getId() + " / pipelineStatus: " + libraryApiVersion.getApiRepositoryStatus());
                        return true;
                    }
                    libraryApiVersion.library(libraryService.getById(libraryApiVersion.getLibrary().getId()).get());
                    librariesApiVersionDetailed.add(libraryApiVersion);
                    return false;
                } catch (Exception e) {
                    exceptionFound.set(e);
                    return true;
                }
            });

            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            } else if (!libraryIsNotReadyFound) {
                // ALL LIBRARIES ARE UPDATED
                logger.info("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - all libraries are updated and tagged to version " + apiVersion.getVersion());

                apiVersionFromDb.setApiRepositoryStatus(ApiVersion.ApiRepositoryStatusEnum.READY);
                patch(apiVersionFromDb);
                deleteApiFile(apiFile);

                apiVersionFromDb.librariesApiVersions(librariesApiVersionDetailed);

                // BUILD LIBRARIES UPDATED
                build(apiVersionFromDb, callback);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            manageError(apiVersion, e);
            deleteApiFile(apiFile);
            if (callback != null) {
                callback.run();
            }
            return false;
        }
    }

    private void deleteApiFile(File apiFile) {
        try {
            FileUtils.deleteDirectory(apiFile.getParentFile());
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    /**
     * Check if a LibraryApiVersion has updated
     *
     * @param libraryApiVersion
     * @return
     */
    private boolean checkLibraryApiVersionIsUpdated(LibraryApiVersion libraryApiVersion) {
        if (isError(libraryApiVersion)) {
            throw new IllegalStateException("The LibraryApiVersion has status ERROR. LibraryApiVersion:" + libraryApiVersion.getId());
        }
        if (libraryApiVersion.getApiRepositoryStatus().equals(LibraryApiVersion.ApiRepositoryStatusEnum.ERROR)) {
            throw new IllegalStateException("The library's repository have an error during update. LibraryApiVersion:" + libraryApiVersion.getId());
        }
        return libraryApiVersion.getApiRepositoryStatus().equals(LibraryApiVersion.ApiRepositoryStatusEnum.READY);
    }

    /**
     * Build a apiVersion
     *
     * @param apiVersion
     * @param callback
     */
    private void build(ApiVersion apiVersion, Runnable callback) throws ApiException {
        logger.info("apiVersion '" + apiVersion.getId() + "' - build ");
        final Session session = kathraSessionManager.getCurrentSession();

        AtomicReference<ApiException> exceptionFound = new AtomicReference<>();

        List<LibraryApiVersion> libraryApiVersionWithLibraryDetails = this.getById(apiVersion.getId()).get()
                .getLibrariesApiVersions()
                .parallelStream().map(libraryApiVersion -> {
                    try {
                        this.kathraSessionManager.handleSession(session);
                        LibraryApiVersion libraryApiVersionWithDetails = libraryApiVersionService.getById(libraryApiVersion.getId()).get();
                        libraryApiVersionWithDetails.setLibrary(libraryService.getById(libraryApiVersionWithDetails.getLibrary().getId()).get());
                        return libraryApiVersionWithDetails;
                    } catch (ApiException e) {
                        exceptionFound.set(e);
                        return null;
                    }
                }).collect(Collectors.toList());

        if (exceptionFound.get() != null) {
            throw exceptionFound.get();
        }

        apiVersion.setLibrariesApiVersions(libraryApiVersionWithLibraryDetails);

        Arrays.stream(Library.LanguageEnum.values()).parallel().forEach(lang -> {
            this.kathraSessionManager.handleSession(session);
            buildForLanguage(apiVersion, lang, callback);
        });
    }

    /**
     * Build model, interface and client
     * @param apiVersion
     * @param language
     * @param callback
     */
    private void buildForLanguage(ApiVersion apiVersion, Library.LanguageEnum language, Runnable callback) {
        final Session session = kathraSessionManager.getCurrentSession();
        final Runnable buildApiClient = () -> {
            this.kathraSessionManager.handleSession(session);
            Runnable callbackPostBuild = () -> notifyWhenBuildLibraryPipelineIsFinished(apiVersion, callback);
            buildForLanguageAndTypeLib(apiVersion, language, Library.TypeEnum.CLIENT, callbackPostBuild, callback);
        };
        final Runnable buildInterface = () -> {
            this.kathraSessionManager.handleSession(session);
            buildForLanguageAndTypeLib(apiVersion, language, Library.TypeEnum.INTERFACE, buildApiClient, callback);
        };
        buildForLanguageAndTypeLib(apiVersion, language, Library.TypeEnum.MODEL, buildInterface, callback);
    }

    private LibraryApiVersion findLibraryApiVersion(ApiVersion apiVersion, Library.LanguageEnum language, Library.TypeEnum type) throws ApiException {

        AtomicReference<ApiException> exceptionFound = new AtomicReference<>();
        List<LibraryApiVersion> libraries =     apiVersion.getLibrariesApiVersions().parallelStream().filter(libraryApiVersion -> libraryApiVersion.getLibrary().getLanguage().equals(language) && libraryApiVersion.getLibrary().getType().equals(type)).collect(Collectors.toList());

        if (exceptionFound.get() != null) {
            throw exceptionFound.get();
        } else if (libraries.isEmpty()) {
            throw new IllegalStateException("Not library found for apiVersion " + apiVersion.getId() + ", lang " + language + ", type=" + type);
        } else if (libraries.size() > 1) {
            throw new IllegalStateException("Several libraries found for apiVersion " + apiVersion.getId() + ", lang " + language + ", type=" + type);
        }
        return libraries.get(0);
    }

    /**
     * Build apiVersion for specific language and type
     * @param apiVersion
     * @param language
     * @param type
     * @param callbackNextBuild
     * @param finalCallback
     */
    private void buildForLanguageAndTypeLib(ApiVersion apiVersion, Library.LanguageEnum language, Library.TypeEnum type, Runnable callbackNextBuild, Runnable finalCallback) {
        logger.info("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - build language " + language + " libType " + type);
        try {
            LibraryApiVersion libraryApiVersion = findLibraryApiVersion(apiVersion, language, type);
            libraryApiVersionService.build(libraryApiVersion, callbackNextBuild);
        } catch (ApiException e) {
            logger.error("apiVersion '" + apiVersion.getId() + "' - unable to build apiVersion for language " + language);
            manageError(apiVersion, e);
            if (finalCallback != null) {
                finalCallback.run();
            }
        }
    }

    /**
     * Callback when build is finished
     *
     * @param apiVersion
     * @param callback
     * @return
     */
    private synchronized boolean notifyWhenBuildLibraryPipelineIsFinished(ApiVersion apiVersion, Runnable callback) {
        logger.info("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - the pipeline have finished.. Check if apiVersion is ready");

        try {
            ApiVersion apiVersionWithDetails = this.getById(apiVersion.getId()).orElseThrow(() -> new IllegalStateException("Unable to find ApiVersion " + apiVersion.getId() + "."));
            if (isError(apiVersionWithDetails)) {
                return false;
            } else if (isReady(apiVersionWithDetails)) {
                return true;
            }

            AtomicReference<Exception> exceptionFound = new AtomicReference<>();

            final Session session = kathraSessionManager.getCurrentSession();
            // CHECK ALL LIBRARIES ARE BUILD
            boolean libraryIsNotReadyFound = apiVersionWithDetails.getLibrariesApiVersions().parallelStream().map(LibraryApiVersion::getId).anyMatch(id -> {
                try {
                    kathraSessionManager.handleSession(session);
                    LibraryApiVersion libraryApiVersionWithDetails = libraryApiVersionService.getById(id).orElseThrow(() -> new IllegalStateException("Unable to find LibraryApiVersion with id :" + id));
                    return !checkLibraryApiVersionIsBuild(libraryApiVersionWithDetails);
                } catch (Exception e) {
                    exceptionFound.set(e);
                }
                return true;
            });

            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            } else if (!libraryIsNotReadyFound) {
                // ALL LIBRARIES ARE BUILD
                logger.info("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - all libraries are updated and build");
                validate(apiVersion, callback);
                return true;
            }
        } catch (Exception e) {
            manageError(apiVersion, e);
            if (callback != null) {
                callback.run();
            }
        }
        return false;
    }

    /**
     * Check if a LibraryApiVersion is build
     *
     * @param libraryApiVersionWithDetails
     * @return
     */
    private boolean checkLibraryApiVersionIsBuild(LibraryApiVersion libraryApiVersionWithDetails) {
        if (isError(libraryApiVersionWithDetails)) {
            throw new IllegalStateException("The LibraryApiVersion has status ERROR. LibraryApiVersion:" + libraryApiVersionWithDetails.getId());
        } else if (!isReady(libraryApiVersionWithDetails)) {
            return false;
        } else if (libraryApiVersionWithDetails.getPipelineStatus().equals(LibraryApiVersion.PipelineStatusEnum.ERROR)) {
            throw new IllegalStateException("The library's repository have an error during build. LibraryApiVersion:" + libraryApiVersionWithDetails.getId());
        }
        return libraryApiVersionWithDetails.getPipelineStatus().equals(LibraryApiVersion.PipelineStatusEnum.READY);
    }

    /**
     * Validation post-creating
     *
     * @param apiVersion
     * @param apiVersionReadyCallback
     * @return
     */
    private synchronized boolean validate(ApiVersion apiVersion, Runnable apiVersionReadyCallback) {
        if (isReady(apiVersion)) {
            return true;
        } else if (isError(apiVersion)) {
            return false;
        }
        logger.info("ApiVersion '" + apiVersion.getId() + "' - '" + apiVersion.getName() + "' - all libraries are updated and build");
        updateStatus(apiVersion, Resource.StatusEnum.READY);
        if (apiVersionReadyCallback != null) {
            apiVersionReadyCallback.run();
        }
        return true;
    }

    public Optional<ApiVersion> getApiVersion(Component component, String versionName) throws ApiException {
        return this.resourceManager.getApiVersions().parallelStream().filter(item -> item.getComponent().getId().equals(component.getId()) &&
                item.getVersion().equals(versionName)).findFirst();
    }

    public List<ApiVersion> getApiVersions(List<Component> components) throws ApiException {
        return (components == null || components.isEmpty()) ? new ArrayList<>() : this.resourceManager.getApiVersions()
                .stream().filter(apiVersion -> components.stream().anyMatch(component -> apiVersion.getComponent().getId().equals(component.getId()))).collect(Collectors.toList());
    }

    public List<ApiVersion> getApiVersionsForImplementationVersion(List<ImplementationVersion> implementationVersions) throws ApiException {
        return (implementationVersions == null || implementationVersions.isEmpty()) ? new ArrayList<>() : this.resourceManager.getApiVersions().stream().filter(apiVersion -> implementationVersions.stream().anyMatch(implementationVersion -> apiVersion.getId().equals(implementationVersion.getApiVersion().getId()))).collect(Collectors.toList());
    }

    @Override
    protected void patch(ApiVersion object) throws ApiException {
        resourceManager.updateApiVersionAttributes(object.getId(), object);
    }

    @Override
    public Optional<ApiVersion> getById(String id) throws ApiException {
        return Optional.of(resourceManager.getApiVersion(id));
    }

    @Override
    public List<ApiVersion> getAll() throws ApiException {
        return resourceManager.getApiVersions();
    }

    public void delete(ApiVersion apiVersion, boolean purge, boolean force) throws ApiException {

        try {
            ApiVersion apiVersionToDeleted = resourceManager.getApiVersion(apiVersion.getId());
            if (isDeleted(apiVersionToDeleted)) {
                return;
            }
            if (apiVersionToDeleted.getImplementationsVersions().size() > 0 && !force) {
                throw new IllegalStateException("ApiVersion "+apiVersionToDeleted.getId()+" is used by some versions of implementations, delete its versions implementations before");
            }
            final AtomicReference<ApiException> exceptionFound = new AtomicReference<>();
            final Session session = kathraSessionManager.getCurrentSession();
            apiVersionToDeleted.getImplementationsVersions().parallelStream().forEach(implementationVersion -> {
                kathraSessionManager.handleSession(session);
                try {
                    implementationVersionService.delete(implementationVersion, purge);
                } catch (ApiException e) {
                    exceptionFound.set(e);
                }
            });
            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            }
            apiVersionToDeleted.getLibrariesApiVersions().parallelStream().forEach(libApiVersion -> {
                kathraSessionManager.handleSession(session);
                try {
                    libraryApiVersionService.delete(libApiVersion, purge);
                } catch (ApiException e) {
                    exceptionFound.set(e);
                }
            });
            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            }
            resourceManager.deleteApiVersion(apiVersionToDeleted.getId());
            apiVersion.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(apiVersion, e);
            throw e;
        }

    }

    public void tryToReconcile(ApiVersion apiVersion) throws Exception {
        if (StringUtils.isEmpty(apiVersion.getName())) {
            throw new IllegalStateException("Name null or empty");
        }
        if (apiVersion.getComponent() == null) {
            throw new IllegalStateException("Component is null");
        }

        if (isReady(apiVersion) || isPending(apiVersion) || isDeleted(apiVersion)) {
            return;
        }

        if (!apiVersion.getApiRepositoryStatus().equals(ApiVersion.ApiRepositoryStatusEnum.READY)) {
            throw new IllegalStateException("Source not updated, impossible to reconcile this ApiVersion");
        }
        Component component = componentService.getById(apiVersion.getComponent().getId()).orElseThrow(() -> new IllegalArgumentException("Component "+apiVersion.getComponent().getId()+" not found"));

        List<LibraryApiVersion> libraryApiVersions = new ArrayList<>();
        for(LibraryApiVersion libraryApiVersion:apiVersion.getLibrariesApiVersions()) {
            libraryApiVersions.add(libraryApiVersionService.getById(libraryApiVersion.getId()).orElseThrow(() -> new Exception("LibraryApiVersion not found")));
        }


        Exception missingLibrary = null;
        File apiFile = null;
        for(Library library:component.getLibraries()) {
            Library libraryWithDetails = libraryService.getById(library.getId()).orElseThrow(() -> new Exception("Library not found"));

            // IF LIBRARY API VERSION NOT EXISTS, CREATE NEW ONE
            if (libraryApiVersions.stream().noneMatch(libraryApiVersion -> libraryApiVersion.getLibrary().getId().equals(libraryWithDetails.getId()))) {
                missingLibrary = new Exception("LibraryApiVersion not found");
                if (apiFile == null) {
                    apiFile = this.getFile(apiVersion);
                }
                createLibraryApiVersion(apiVersion, libraryWithDetails, apiFile, null);
            }
        }
        if (missingLibrary != null) {
            throw missingLibrary;
        }

        if (libraryApiVersions.stream().anyMatch(libraryApiVersion -> !libraryApiVersionService.isReady(libraryApiVersion))) {
            throw new Exception("Some LibraryApiVersion are not ready");
        }

        updateStatus(apiVersion, Resource.StatusEnum.READY);
    }

}
