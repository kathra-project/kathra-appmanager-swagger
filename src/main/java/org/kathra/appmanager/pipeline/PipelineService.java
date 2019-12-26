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
package org.kathra.appmanager.pipeline;

import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.pipelinemanager.client.PipelinemanagerClient;
import org.kathra.resourcemanager.client.PipelinesClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import javax.xml.transform.Source;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author julien.boubechtoula
 */
public class PipelineService extends AbstractResourceService<Pipeline> {

    private PipelinesClient resourceManager;
    private PipelinemanagerClient pipelineManagerClient;
    private LibraryService libraryService;
    private SourceRepositoryService sourceRepositoryService;
    private ComponentService componentService;

    private static final String BUILD_PARAM_BRANCH = "GIT_BRANCH";
    private static final String BUILD_PARAM_SRC_URL = "GIT_URL";


    public int intervalCheckMs = 30000;
    public int intervalTimeoutMs = 600000;

    public PipelineService() {

    }

    public void configure(ServiceInjection serviceInjection) {
        super.configure(serviceInjection);
        this.resourceManager = new PipelinesClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
        this.pipelineManagerClient = new PipelinemanagerClient(serviceInjection.getConfig().getPipelineManagerUrl(), serviceInjection.getSessionManager());
        this.libraryService = serviceInjection.getService(LibraryService.class);
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        this.componentService = serviceInjection.getService(ComponentService.class);
    }

    public PipelineService(PipelinesClient resourceManager, PipelinemanagerClient pipelineManagerClient, LibraryService libraryService, SourceRepositoryService sourceRepositoryService, KathraSessionManager kathraSessionManager, ComponentService componentService) {
        this.libraryService = libraryService;
        this.pipelineManagerClient = pipelineManagerClient;
        this.resourceManager = resourceManager;
        this.sourceRepositoryService = sourceRepositoryService;
        this.componentService = componentService;
        super.kathraSessionManager = kathraSessionManager;
    }

    public void setIntervalCheckMs(int intervalCheckMs) {
        this.intervalCheckMs = intervalCheckMs;
    }

    public void setIntervalTimeoutMs(int intervalTimeoutMs) {
        this.intervalTimeoutMs = intervalTimeoutMs;
    }

    public Build build(Pipeline pipeline, String branchOrTag, Map<String,String> extrasArgs, Runnable callback) throws ApiException {

        if (pipeline == null) {
            throw new IllegalArgumentException("Pipeline is null");
        }
        if (StringUtils.isEmpty(pipeline.getPath())) {
            throw new IllegalArgumentException("Pipeline's path is null or empty");
        }

        if (StringUtils.isEmpty(pipeline.getProviderId())) {
            throw new IllegalArgumentException("Pipeline's providerId is null or empty");
        }

        if (pipeline.getSourceRepository() == null) {
            throw new IllegalArgumentException("Pipeline's SourceRepository is null");
        }

        if (StringUtils.isEmpty(branchOrTag)) {
            throw new IllegalArgumentException("branch or tag is null or empty");
        }
        final String sshUrl = getSshUrlRepository(pipeline);

        Build build = new Build()   .path(pipeline.getProviderId())
                                    .addBuildArgumentsItem(new BuildArgument().key(BUILD_PARAM_BRANCH).value(branchOrTag))
                                    .addBuildArgumentsItem(new BuildArgument().key(BUILD_PARAM_SRC_URL).value(sshUrl));
        if(extrasArgs != null) {
            for(Map.Entry<String,String> entry:extrasArgs.entrySet()){
                build.addBuildArgumentsItem(new BuildArgument().key(entry.getKey()).value(entry.getValue()));
            }
        }
        Build buildWithNumber = pipelineManagerClient.createBuild(build);
        if (callback != null) {
            final Session session = kathraSessionManager.getCurrentSession();
            CompletableFuture.runAsync(() -> {
                try {
                    kathraSessionManager.handleSession(session);
                    checkBuildPipeline(pipeline, buildWithNumber, callback);
                } finally {
                   // kathraSessionManager.deleteSession();
                }
            });
        }
        return buildWithNumber;
    }

    private String getSshUrlRepository(Pipeline pipeline) throws ApiException {
        final String sshUrl;
        if (StringUtils.isEmpty(pipeline.getSourceRepository().getSshUrl())) {
            Optional<SourceRepository> sourceRepositoryWithDetails = sourceRepositoryService.getById(pipeline.getSourceRepository().getId());
            if (!sourceRepositoryWithDetails.isPresent()) {
                throw new IllegalArgumentException("SourceRepository not found");
            }
            if (StringUtils.isEmpty(sourceRepositoryWithDetails.get().getSshUrl())) {
                throw new IllegalArgumentException("SourceRepository's sshUrl is null or empty");
            } else {
                sshUrl = sourceRepositoryWithDetails.get().getSshUrl();
            }
        } else {
            sshUrl = pipeline.getSourceRepository().getSshUrl();
        }
        return sshUrl;
    }


    private void checkBuildPipeline(Pipeline pipeline, Build build, Runnable callback) {
        long start = System.currentTimeMillis();
        try {
            while(true) {
                if (System.currentTimeMillis() - start > intervalTimeoutMs) {
                    throw new Exception("Build " + pipeline.getProviderId() + " #" + build.getBuildNumber() + " timeout");
                }
                Thread.sleep(intervalCheckMs);
                try {
                    Build buildWithStatus = pipelineManagerClient.getBuild(pipeline.getProviderId(), build.getBuildNumber());
                    switch (buildWithStatus.getStatus()) {
                        case PROCESSING:
                        case SCHEDULED:
                            break;
                        case SUCCESS:
                        case FAILED:
                            logger.info("Build " + pipeline.getProviderId() + " #" + build.getBuildNumber() + " has finished with status " + buildWithStatus.getStatus());
                            callback.run();
                            return;
                        default:
                            throw new NotImplementedException("Build status not implemented " + buildWithStatus.getStatus());
                    }
                } catch(ApiException exception) {
                    logger.warn(exception.getMessage(), exception);
                }
            }
        } catch (Exception e) {
            logger.error("Error during check pipeline's build PipelineId:" + pipeline.getId()+" build:"+pipeline.getProviderId()+" #"+ build.getBuildNumber(), e);
            super.updateStatus(pipeline, Resource.StatusEnum.UNSTABLE);
        }
    }

    public Pipeline create(String name, String path, SourceRepository sourceRepository, Pipeline.TemplateEnum template, String credentialId, Runnable callback) throws ApiException {


        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Name is null or empty");
        }
        if (StringUtils.isEmpty(path)) {
            throw new IllegalArgumentException("Path is null or empty");
        }
        if (template == null) {
            throw new IllegalArgumentException("Template is null or empty");
        }
        if (StringUtils.isEmpty(credentialId)) {
            throw new IllegalArgumentException("Credential is null or empty");
        }
        if (StringUtils.isEmpty(sourceRepository.getSshUrl())) {
            throw new IllegalArgumentException("SourceRepository sshUrl is null or empty");
        }
        if (!isReady(sourceRepository)) {
            throw new IllegalArgumentException("SourceRepository's library is not ready");
        }


        Pipeline toAdd = new Pipeline() .name(name)
                .sourceRepository(sourceRepository)
                .path(path)
                .template(template)
                .credentialId(credentialId);


        final Pipeline pipeline = resourceManager.addPipeline(toAdd);
        try {

            final Session session = kathraSessionManager.getCurrentSession();
            CompletableFuture.runAsync(() -> {
                try {
                    kathraSessionManager.handleSession(session);
                    Pipeline pipelineUpdated = pipelineManagerClient.createPipeline(pipeline.sourceRepository(sourceRepository));
                    pipeline.provider(pipelineUpdated.getProvider());
                    pipeline.providerId(pipelineUpdated.getProviderId());
                    pipeline.status(Resource.StatusEnum.READY);
                    resourceManager.updatePipeline(pipeline.getId(), pipeline);
                    if (callback != null) {
                        try {
                            callback.run();
                        } catch(Exception e){
                            logger.error("Unable run callback", e);
                        }
                    }
                } catch (Exception e) {
                    manageError(pipeline, e);
                    if (callback != null) {
                        try {
                            callback.run();
                        } catch(Exception e2){
                            logger.error("Unable run callback", e2);
                        }
                    }
                } finally {
                   // kathraSessionManager.deleteSession();
                }
            });

        } catch(Exception e){
            manageError(pipeline, e);
        }

        return pipeline;
    }

    public Pipeline createLibraryPipeline(Library library, Runnable callback) throws ApiException {
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

        if (library.getSourceRepository() == null) {
            throw new IllegalArgumentException("SourceRepository's library is null");
        }

        SourceRepository sourceRepository = sourceRepositoryService.getById(library.getSourceRepository().getId()).orElseThrow(() -> new IllegalStateException("Unable to find SourceRepository "+library.getSourceRepository().getId()));
        if (StringUtils.isEmpty(sourceRepository.getSshUrl())) {
            throw new IllegalArgumentException("SourceRepository sshUrl is null or empty");
        }
        if (!isReady(sourceRepository)) {
            throw new IllegalArgumentException("SourceRepository's library is not ready");
        }

        String path = component.getMetadata().get(ComponentService.METADATA_GROUP_PATH)+"/components/"+component.getName()+"/"+library.getLanguage()+"/"+library.getType();
        String credentialId = (String) component.getMetadata().get(ComponentService.METADATA_GROUP_ID);
        Pipeline.TemplateEnum template = null;

        switch(library.getLanguage()){
            case JAVA:
                template = Pipeline.TemplateEnum.JAVA_LIBRARY;
                break;
            case PYTHON:
                template = Pipeline.TemplateEnum.PYTHON_LIBRARY;
                break;
            default:
                throw new NotImplementedException("language "+library.getLanguage()+" not implemented");
        }

        Pipeline pipeline = create(library.getName(), path, sourceRepository, template, credentialId, callback);
        libraryService.patch(new Library().id(library.getId()).pipeline(pipeline));
        return pipeline;
    }

    public Build getBuild(Pipeline pipeline, String buildNumber) throws ApiException {
        return pipelineManagerClient.getBuild(pipeline.getProviderId(), buildNumber);
    }

    @Override
    protected void patch(Pipeline object) throws ApiException {
        resourceManager.updatePipelineAttributes(object.getId(), object);
    }

    public Optional<Pipeline> getById(String id) throws ApiException {
        Pipeline pipeline = resourceManager.getPipeline(id);
        return pipeline == null ? Optional.of(null) : Optional.of(pipeline);
    }

    @Override
    public List<Pipeline> getAll() throws ApiException {
        return resourceManager.getPipelines();
    }

    public List<Build> getBuildsByBranch(Pipeline pipeline, String branch) throws ApiException {
        return pipelineManagerClient.getBuilds(pipeline.getProviderId(), branch, null);
    }

    public void delete(Pipeline pipeline, boolean purge) throws ApiException {
        try {
            Pipeline pipelineToDeleted = resourceManager.getPipeline(pipeline.getId());
            if (isDeleted(pipelineToDeleted)) {
                return;
            }

            if (purge) {
                pipelineManagerClient.deletePipeline(pipelineToDeleted.getProviderId());
            }

            resourceManager.deletePipeline(pipelineToDeleted.getId());
            pipeline.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(pipeline, e);
            throw e;
        }
    }
}
