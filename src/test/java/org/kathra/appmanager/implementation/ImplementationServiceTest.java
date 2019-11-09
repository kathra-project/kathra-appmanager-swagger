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
package org.kathra.appmanager.implementation;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.pipeline.PipelineServiceAbstractTest;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationsClient;
import org.kathra.utils.ApiException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.kathra.core.model.Pipeline.TemplateEnum.JAVA_SERVICE;

/**
 * Test ImplementationService
 *
 * @author quentin.semanne
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImplementationServiceTest extends AbstractServiceTest {


    public final static String IMPL_ID = "implementation-id";
    public final static String IMPL_SRC_REPO_ID = "implementation-src-repo-id";
    public final static String IMPL_SRC_REPO_PATH = "implementation-src-repo-path";
    public final static String IMPL_NAME = "implementation-name";
    public final static String IMPL_ARTIFACT_NAME = "implementationid";
    public final static String IMPL_ARTIFACT_GROUP_ID = "com.mygroup.subgroup";

    public static Implementation generateImplementationExample(Asset.LanguageEnum languageEnum) {
        String id = UUID.randomUUID().toString();
        return new Implementation()
                .id(IMPL_ID)
                .name(IMPL_NAME)
                .putMetadataItem("groupPath", "groupName")
                .putMetadataItem(ImplementationService.METADATA_ARTIFACT_NAME, IMPL_ARTIFACT_NAME)
                .putMetadataItem(ImplementationService.METADATA_ARTIFACT_GROUP_ID, IMPL_ARTIFACT_GROUP_ID)
                .sourceRepository(new SourceRepository().id(IMPL_SRC_REPO_ID).path(IMPL_SRC_REPO_PATH))
                .pipeline(new PipelineServiceAbstractTest().getPipeline())
                .component(new ComponentServiceTest().getComponentWithId())
                .language(languageEnum)
                .description("my new implem "+id);
    }

    /**
     * Local Variables
     */
    protected ImplementationService underTest;

    /**
     * Mock clients & services
     */
    @Mock
    protected ApiVersionService apiVersionService;
    @Mock
    protected SourceRepositoryService sourceRepositoryService;
    @Mock
    protected PipelineService pipelineService;
    @Mock
    protected ComponentService componentService;
    @Mock
    protected ImplementationsClient resourceManager;
    @Mock
    protected ImplementationVersionService implementationVersionService;

    protected Implementation implementationDb = null;

    /**
     * Context initialization
     */
    @BeforeAll
    static void setUp() throws Exception {
    }

    /**
     * Mocks behaviour initialization
     */
    @BeforeEach
    void setUpEach() throws Exception {
        this.apiVersionService = Mockito.mock(ApiVersionService.class);
        this.componentService = Mockito.mock(ComponentService.class);
        this.sourceRepositoryService = Mockito.mock(SourceRepositoryService.class);
        this.resourceManager = Mockito.mock(ImplementationsClient.class);
        this.implementationVersionService = Mockito.mock(ImplementationVersionService.class);
        this.pipelineService = Mockito.mock(PipelineService.class);

        underTest = new ImplementationService(this.componentService, this.apiVersionService, this.sourceRepositoryService, this.implementationVersionService, this.resourceManager, this.pipelineService, kathraSessionManager);
        mockNominalBehavior();
    }

    public final static String API_VERSION_ID = "api-version-id";
    public final static String API_VERSION_VERSION = "1.5.0";

    public final static String COMPONENT_ID = "component-id";
    public final static String API_SRC_REPO_ID = "api-repository-id";
    public final static String API_SRC_REPO_PATH = "api-repository-path";
    public final static String API_SRC_REPO_SSH_URL = "api-repository-ssh-url";

    public final static String COMPONENT_NAME = "component-name";
    public final static String GROUP_PATH = "group-identifier";
    public final static String GROUP_ID = "group-identifier";

    public final static String SRC_REPO_ID = "repository-id";
    public final static String PIPELINE_ID = "repository-id";

    public final static String IMPL_VERSION_ID = "impl-version-id";

    public final static String COMPONENT_ARTIFACT_NAME = "implementationid";
    public final static String COMPONENT_ARTIFACT_GROUP_ID = "com.mygroup.subgroup";

    public final static String IMPLEMENTATION_PATH_EXPECTED = "group-identifier/components/component-name/implementations/JAVA/implementation-name";


    protected void mockNominalBehavior() throws Exception {
        mockResourceManager();
        mockCreateRepository();
        mockCreatePipeline();
        mockCreateImplemVersion();
        mockApiVersion();
        mockComponent();
    }

    protected void mockComponent() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent())).when(componentService).getById(Mockito.eq(COMPONENT_ID));
    }

    protected void mockApiVersion() throws ApiException {
        Mockito.doReturn(Optional.of(getApiVersion())).when(apiVersionService).getById(Mockito.eq(API_VERSION_ID));
    }

    protected void mockResourceManager() throws Exception {
        //ADD
        Mockito.doAnswer(invocationOnMock -> {
            implementationDb = invocationOnMock.getArgument(0);
            implementationDb.id(IMPL_ID);
            Mockito.doReturn(implementationDb).when(resourceManager).getImplementation(Mockito.eq(IMPL_ID));
            return implementationDb;
        }).when(resourceManager).addImplementation(Mockito.any());

        // UPDATE
        Mockito.doAnswer(invocationOnMock -> {
            implementationDb = invocationOnMock.getArgument(1);
            Mockito.doReturn(implementationDb).when(resourceManager).getImplementation(Mockito.eq(IMPL_ID));
            return implementationDb;
        }).when(resourceManager).updateImplementation(Mockito.any(), Mockito.any());

        // PATCH
        Mockito.doAnswer(invocationOnMock -> {
            Implementation implementationPatch = invocationOnMock.getArgument(1);
            if (implementationPatch.getStatus() != null) {
                implementationDb.status(implementationPatch.getStatus());
            }
            if (implementationPatch.getVersions() != null) {
                implementationDb.versions(implementationPatch.getVersions());
            }
            if (implementationPatch.getComponent() != null) {
                implementationDb.component(implementationPatch.getComponent());
            }
            if (implementationPatch.getSourceRepository() != null) {
                implementationDb.sourceRepository(implementationPatch.getSourceRepository());
            }
            if (implementationPatch.getPipeline() != null) {
                implementationDb.pipeline(implementationPatch.getPipeline());
            }
            Mockito.doReturn(implementationDb).when(resourceManager).getImplementation(IMPL_ID);
            return implementationDb;
        }).when(resourceManager).updateImplementationAttributes(Mockito.eq(IMPL_ID), Mockito.any());
    }
    protected void mockCreateRepository() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            SourceRepository src = new SourceRepository().id(SRC_REPO_ID).status(Resource.StatusEnum.PENDING);
            Runnable callback = invocationOnMock.getArgument(3);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                    Mockito.doReturn(Optional.of(src.status(Resource.StatusEnum.READY))).when(sourceRepositoryService).getById(SRC_REPO_ID);
                    callback.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Mockito.doReturn(Optional.of(src)).when(sourceRepositoryService).getById(SRC_REPO_ID);
            return src;
        }).when(sourceRepositoryService).create(Mockito.eq("impl-implementation-name"), Mockito.eq(IMPLEMENTATION_PATH_EXPECTED), Mockito.argThat(array -> array[0].equals(GROUP_ID)), Mockito.any());
    }

    protected void mockCreatePipeline() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Pipeline pipeline = new Pipeline().id(PIPELINE_ID).status(Resource.StatusEnum.PENDING);
            Runnable callback = invocationOnMock.getArgument(5);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                    Mockito.doReturn(Optional.of(pipeline.status(Resource.StatusEnum.READY))).when(pipelineService).getById(Mockito.eq(PIPELINE_ID));
                    callback.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Mockito.doReturn(Optional.of(pipeline)).when(pipelineService).getById(PIPELINE_ID);
            return pipeline;
        }).when(pipelineService).create(Mockito.eq(IMPL_NAME), Mockito.eq(IMPLEMENTATION_PATH_EXPECTED), Mockito.argThat(src -> src.getId().equals(SRC_REPO_ID)), Mockito.eq(JAVA_SERVICE), Mockito.eq(GROUP_ID), Mockito.any());
    }

    protected void mockCreateImplemVersion() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            ImplementationVersion implementationVersion = new ImplementationVersion().id(IMPL_VERSION_ID).name(((Implementation)invocationOnMock.getArgument(0)).getName()+"-"+invocationOnMock.getArgument(2)).version(invocationOnMock.getArgument(2)).status(Resource.StatusEnum.PENDING);
            Runnable callback = invocationOnMock.getArgument(3);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                    Mockito.doReturn(Optional.of(implementationVersion.status(Resource.StatusEnum.READY))).when(implementationVersionService).getById(Mockito.eq(IMPL_VERSION_ID));
                    callback.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            implementationDb.addVersionsItem(implementationVersion);
            Mockito.doReturn(implementationDb).when(resourceManager).getImplementation(IMPL_ID);
            Mockito.doReturn(Optional.of(implementationVersion)).when(implementationVersionService).getById(Mockito.eq(IMPL_VERSION_ID));
            return implementationVersion;
        }).when(implementationVersionService).create(Mockito.argThat(impl -> impl.getId().equals(IMPL_ID)), Mockito.argThat(apiVersion -> apiVersion.getId().equals(API_VERSION_ID)), Mockito.eq("1.0.0"), Mockito.any());
    }



    public void waitUntilNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(IMPL_ID).get().getStatus();
        while(status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(IMPL_ID).get().getStatus();
        }
    }

    public static ApiVersion getApiVersion() {
        return new ApiVersion()
                .id(API_VERSION_ID)
                .version(API_VERSION_VERSION)
                .released(false)
                .status(Resource.StatusEnum.READY)
                .component(getComponent());
    }

    public static Component getComponent() {
        return new Component()  .id(COMPONENT_ID)
                .name(COMPONENT_NAME)
                .apiRepository(new SourceRepository().id(API_SRC_REPO_ID).path(API_SRC_REPO_PATH).sshUrl(API_SRC_REPO_SSH_URL))
                .putMetadataItem(ComponentService.METADATA_GROUP_PATH, GROUP_PATH)
                .putMetadataItem(ComponentService.METADATA_GROUP_ID, GROUP_ID)
                .putMetadataItem(ComponentService.METADATA_API_ARTIFACT_NAME, COMPONENT_ARTIFACT_NAME)
                .putMetadataItem(ComponentService.METADATA_API_GROUP_ID, COMPONENT_ARTIFACT_GROUP_ID);
    }
}
