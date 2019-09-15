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
package org.kathra.appmanager.implementationversion;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.*;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationVersionsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraException;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.kathra.appmanager.Config;
/**
 * @author pierredadt@kathra.org
 * @author julien.boubechtoula
 */
public class ImplementationVersionService extends AbstractResourceService<ImplementationVersion> {

    public static final String METADATA_LAST_BUILD_NUMBER = "last-build-number";
    public static final String DEFAULT_BRANCH = "dev";
    public static final Pattern patternVersion = Pattern.compile("^[0-9]\\.[0-9]\\.[0-9]$");

    private ImplementationVersionsClient implementationVersionsClient;
    private ComponentService componentService;
    private ApiVersionService apiVersionService;
    private ImplementationService implementationService;
    private PipelineService pipelineService;
    private SourceRepositoryService sourceRepositoryService;
    private CodegenClient codegenClient;

    private Logger logger = LoggerFactory.getLogger(ImplementationVersionService.class);

    private String imageRegistryHost;

    public ImplementationVersionService() {
        imageRegistryHost = null;
    }

    public ImplementationVersionService(Config config, ImplementationVersionsClient implementationVersionsClient, ApiVersionService apiVersionService, ComponentService componentService, ImplementationService implementationService, SourceRepositoryService sourceRepositoryService, CodegenClient codegenClient, KathraSessionManager kathraSessionManager, PipelineService pipelineService) {
        this.implementationVersionsClient = implementationVersionsClient;
        this.sourceRepositoryService = sourceRepositoryService;
        this.codegenClient = codegenClient;
        this.implementationService = implementationService;
        this.apiVersionService = apiVersionService;
        this.componentService = componentService;
        super.kathraSessionManager = kathraSessionManager;
        this.pipelineService = pipelineService;
        this.imageRegistryHost = config.getImageRegistryHost();
    }

    public void configure(ServiceInjection serviceInjection) {
        super.configure(serviceInjection);
        this.implementationVersionsClient = new ImplementationVersionsClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        this.implementationService = serviceInjection.getService(ImplementationService.class);
        this.pipelineService = serviceInjection.getService(PipelineService.class);
        this.codegenClient = new CodegenClient(serviceInjection.getConfig().getCodegenUrl(), serviceInjection.getSessionManager());
        this.componentService = serviceInjection.getService(ComponentService.class);
        this.apiVersionService = serviceInjection.getService(ApiVersionService.class);
        this.imageRegistryHost = serviceInjection.getConfig().getImageRegistryHost();
    }

    @Override
    protected void patch(ImplementationVersion object) throws ApiException {
        implementationVersionsClient.updateImplementationVersionAttributes(object.getId(), object);
    }

    /**
     * @param implementations
     * @return
     * @throws ApiException
     */
    public List<ImplementationVersion> getImplementationVersions(List<Implementation> implementations) throws ApiException {
        if (implementations == null || implementations.isEmpty())
            return new ArrayList<>();

        return this.implementationVersionsClient.getImplementationVersions()
                .stream().filter(implementationVersion -> implementations.stream().anyMatch(implementation -> implementationVersion.getImplementation().getId().equals(implementation.getId()))).collect(Collectors.toList());
    }

    @Override
    public Optional<ImplementationVersion> getById(String id) throws ApiException {
        ImplementationVersion result = implementationVersionsClient.getImplementationVersion(id);
        return result == null ? Optional.empty() : Optional.of(result);
    }

    @Override
    public List<ImplementationVersion> getAll() throws ApiException {
        return implementationVersionsClient.getImplementationVersions();
    }

    public List<ImplementationVersion> fillImplementationVersionWithApiVersion (List<ImplementationVersion> implementationVersions, List<ApiVersion> apiVersions) throws ApiException {
        if (implementationVersions == null || implementationVersions.isEmpty()) return implementationVersions;
        // Converting list to map to optimize accesses
        Map<String, ApiVersion> apiVersionMap =
                apiVersions.stream().collect(Collectors.toMap(ApiVersion::getId, apiVersion -> apiVersion));

        for(ImplementationVersion implementationVersion : implementationVersions) {
            ApiVersion apiVersion = apiVersionMap.get(implementationVersion.getApiVersion().getId());
            implementationVersion.setApiVersion(apiVersion);
        }
        return implementationVersions;

    }


    public ImplementationVersion create(Implementation implementation, ApiVersion apiVersion, String versionImpl, Runnable callback) throws ApiException {

        if (isError(implementation)) {
            throw new IllegalStateException("Implementation "+implementation.getId()+" has an error");
        }
        if (!implementation.getMetadata().containsKey(ImplementationService.METADATA_ARTIFACT_NAME) || StringUtils.isEmpty((CharSequence) implementation.getMetadata().get(ImplementationService.METADATA_ARTIFACT_NAME))){
            throw new IllegalArgumentException("Implementation's metadata "+ImplementationService.METADATA_ARTIFACT_NAME+" is null or empty");
        }
        if (!implementation.getMetadata().containsKey(ImplementationService.METADATA_ARTIFACT_GROUP_ID) || StringUtils.isEmpty((CharSequence) implementation.getMetadata().get(ImplementationService.METADATA_ARTIFACT_GROUP_ID))){
            throw new IllegalArgumentException("Implementation's metadata "+ImplementationService.METADATA_ARTIFACT_GROUP_ID+" is null or empty");
        }

        if (!patternVersion.matcher(versionImpl).find()){
            throw new IllegalArgumentException("Implementation's version doesn't respect nomenclature X.X.X (ex: 1.0.1)");
        }

        List<ImplementationVersion> implementationExisting = getImplementationVersions(ImmutableList.of(implementation));
        if (implementationExisting.parallelStream().anyMatch(implV -> implV.getVersion().equals(versionImpl))) {
            throw new IllegalStateException("Implementation's version already exists");
        }

        ApiVersion apiVersionWithDetails = apiVersionService.getById(apiVersion.getId()).orElseThrow(() -> new IllegalArgumentException("ApiVersion "+apiVersion.getId()+" not found"));
        if (isError(apiVersionWithDetails)) {
            throw new IllegalStateException("ApiVersion " + apiVersion.getId() + " has an error");
        }
        Component component = componentService.getById(apiVersionWithDetails.getComponent().getId()).orElseThrow(() -> new IllegalArgumentException("Component "+apiVersionWithDetails.getComponent().getId()+" not found"));
        SourceRepository apiRepository = sourceRepositoryService.getById(component.getApiRepository().getId()).orElseThrow(() -> new IllegalArgumentException("SourceRepository "+component.getApiRepository().getId()+" not found"));
        File apiFile = sourceRepositoryService.getFile(apiRepository, apiVersion.getVersion(), ApiVersionService.API_FILENAME);
        if (apiFile == null) {
            throw new IllegalStateException("ApiFile is null or empty");
        }

        ImplementationVersion implVersion = implementationVersionsClient.addImplementationVersion(new ImplementationVersion().name(implementation.getName()+":"+versionImpl).apiVersion(apiVersion).implementation(implementation).version(versionImpl).status(Resource.StatusEnum.PENDING));
        final Session session = kathraSessionManager.getCurrentSession();
        CompletableFuture.runAsync(() -> {
            try {
                kathraSessionManager.handleSession(session);
                generateAndUpdateSrc(implVersion.implementation(implementation), apiFile);
                Pipeline implementationPipeline = getPipeline(implVersion);
                build(implVersion, implementationPipeline, () -> validateBuilding(implVersion, implementationPipeline, callback));
            } catch (Exception e) {
                manageError(implVersion, e);
                if (callback != null) {
                    callback.run();
                }
            } finally {
                //kathraSessionManager.deleteSession();
            }
        });

        return implVersion;
    }

    private void generateAndUpdateSrc(ImplementationVersion implVersion, File apiFile) throws ApiException {
        String artifactName = (String) implVersion.getImplementation().getMetadata().get(ImplementationService.METADATA_ARTIFACT_NAME);
        String artifactGroup = (String) implVersion.getImplementation().getMetadata().get(ImplementationService.METADATA_ARTIFACT_GROUP_ID);
        SourceRepository sourceRepository = sourceRepositoryService.getById(implVersion.getImplementation().getSourceRepository().getId()).orElseThrow(() -> new IllegalStateException("Unable to find SourceRepository "+implVersion.getImplementation().getSourceRepository().getId()));
        // generate source code
        File implementationFiles = codegenClient.generateImplementation(apiFile, implVersion.getImplementation().getName(), implVersion.getImplementation().getLanguage().toString(), artifactName, artifactGroup, implVersion.getVersion());
        if (implementationFiles == null) {
            throw new IllegalStateException("Implementation's file generated is null or empty");
        }
        // update source code
        try {
            SourceRepositoryCommit commit = sourceRepositoryService.commitArchiveAndTag(sourceRepository, DEFAULT_BRANCH, implementationFiles, ".", implVersion.getVersion());
            if (commit == null) {
                throw new IllegalStateException("Commit is null");
            }
        } catch(ApiException e) {
            // PRECONDITION FAILED EXCEPTION IF THROWS WHEN SOURCE REPOSITORY IS ALREADY UPDATED
            if (e.getCode() != KathraException.ErrorCode.PRECONDITION_FAILED.getCode()) {
                throw e;
            }
        }
    }

    private boolean validateBuilding(ImplementationVersion implementationVersion, Pipeline pipeline, Runnable callback) {
        try {
            ImplementationVersion implementationVersionDetailed = implementationVersionsClient.getImplementationVersion(implementationVersion.getId());
            if (implementationVersionDetailed.getMetadata() == null || !implementationVersionDetailed.getMetadata().containsKey(METADATA_LAST_BUILD_NUMBER)) {
                throw new IllegalStateException("Unable to get metadata '" + METADATA_LAST_BUILD_NUMBER + "' for ImplementationVersion " + implementationVersion.getId());
            }

            Build build = pipelineService.getBuild(pipeline, (String) implementationVersionDetailed.getMetadata().get(METADATA_LAST_BUILD_NUMBER));
            switch (build.getStatus()) {
                case SCHEDULED:
                case PROCESSING:
                    throw new IllegalStateException("Implementation " + implementationVersionDetailed.getId() + " building should be finished");
                case SUCCESS:
                    logger.info("LibraryApiVersion "+implementationVersionDetailed.getId()+" has been build");
                    updateStatus(implementationVersionDetailed, Resource.StatusEnum.READY);
                    if (callback != null) {
                        callback.run();
                    }
                    return true;
                case FAILED:
                    throw new Exception("An error occurred during building ImplementationVersion " + implementationVersionDetailed.getId());
                default:
                    throw new IllegalStateException("ImplementationVersion " + implementationVersionDetailed.getId() + " building have not implemented status "+build.getStatus());
            }
        } catch(Exception e) {
            manageError(implementationVersion, e);
            if (callback != null) {
                callback.run();
            }
            return false;
        }
    }

    private Pipeline getPipeline(ImplementationVersion implementationVersion) throws ApiException {
        Implementation implementation = implementationService.getById(implementationVersion.getImplementation().getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find Implementation with id : " + implementationVersion.getImplementation().getId()));
        return pipelineService.getById(implementation.getPipeline().getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find Pipeline with id : " + implementation.getPipeline().getId()));
    }

    private Build build(ImplementationVersion implementationVersion, Pipeline pipeline, Runnable callback) throws ApiException {
        Build build = pipelineService.build(pipeline, DEFAULT_BRANCH, ImmutableMap.of("DOCKER_URL", this.imageRegistryHost), callback);
        implementationVersion.putMetadataItem(METADATA_LAST_BUILD_NUMBER, build.getBuildNumber());
        implementationVersionsClient.updateImplementationVersionAttributes(implementationVersion.getId(), new ImplementationVersion().metadata(implementationVersion.getMetadata()));
        return build;
    }
}
