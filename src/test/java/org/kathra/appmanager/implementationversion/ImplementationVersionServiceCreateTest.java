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
import org.kathra.appmanager.implementation.ImplementationServiceCreateTest;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationVersionsClient;
import org.kathra.utils.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.kathra.appmanager.implementation.ImplementationServiceCreateTest.API_VERSION_ID;
import static org.kathra.appmanager.implementation.ImplementationServiceCreateTest.getApiVersion;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.kathra.appmanager.Config;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ImplementationVersionServiceCreateTest extends AbstractServiceTest {

    /**
     * Local Variables
     */
    private ImplementationVersionService underTest;

    @Mock
    private Config config;
    @Mock
    private ImplementationVersionsClient resourceManager;
    @Mock
    private ComponentService componentService;
    @Mock
    private ImplementationService implementationService;
    @Mock
    private ApiVersionService apiVersionService;
    @Mock
    private SourceRepositoryService sourceRepositoryService;
    @Mock
    private CodegenClient codegenClient;
    @Mock
    private PipelineService pipelineService;

    public final String IMPL_VERSION_VERSION = "1.2.0";
    public final String IMPL_VERSION_ID = "implem-version-id";

    public ImplementationVersion implementationVersionDb;

    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(resourceManager);
        Mockito.reset(implementationService);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(componentService);
        Mockito.reset(codegenClient);
        Mockito.reset(apiVersionService);
        Mockito.reset(pipelineService);
        callback = Mockito.mock(Runnable.class);
        this.implementationVersionDb = null;
        Mockito.doReturn("my-registry.com").when(config).getImageRegistryHost();
        underTest = new ImplementationVersionService(this.config, this.resourceManager, apiVersionService, componentService, implementationService, sourceRepositoryService, codegenClient, kathraSessionManager, pipelineService);
        mockNominalBehavior();
    }


    private void mockNominalBehavior() throws Exception {
        mockResourceManager();
        mockGetFileRepository();
        mockUpdateRepositoryImpl();
        mockApiVersion();
        mockComponent();
        mockImpl();
        mockSrcRepoApi();
        mockSrcRepoImpl();
        mockImplGen();
        mockPipeline();
        mockPipelineBuild(Build.StatusEnum.SUCCESS);
    }

    private void mockPipeline() throws ApiException {
        Mockito.doReturn(Optional.of(getImplementation().getPipeline())).when(pipelineService).getById(getImplementation().getPipeline().getId());
    }

    private void mockPipelineBuild(Build.StatusEnum status) throws ApiException {
        String pipelineId = getImplementation().getPipeline().getId();
        Mockito.doAnswer(invocationOnMock -> {
            Runnable callback = invocationOnMock.getArgument(3);
            Thread.sleep(500);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(1500);
                    Build build = new Build().commitId("d654f6dq4s").buildNumber("5").status(status);
                    Mockito.doReturn(build).when(pipelineService).getBuild(Mockito.argThat(pipeline -> pipeline.getId().equals(pipelineId)), Mockito.eq("5"));
                    callback.run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Build build = new Build().commitId("d654f6dq4s").buildNumber("5").status(Build.StatusEnum.SCHEDULED);
            Mockito.doReturn(build).when(pipelineService).getBuild(Mockito.argThat(pipeline -> pipeline.getId().equals(pipelineId)), Mockito.eq("5"));
            return build;
        }).when(pipelineService)
                .build(Mockito.argThat(pipeline -> pipeline.getId().equals(pipelineId)),
                        Mockito.eq(ImplementationVersionService.DEFAULT_BRANCH),
                        Mockito.eq(ImmutableMap.of("DOCKER_URL","my-registry.com")),
                        Mockito.any());
    }


    private void mockImplGen() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(1000);
            return Mockito.mock(File.class);
        }).when(codegenClient).generateFromTemplate(Mockito.argThat(t -> t.getName().equals("SERVER_"+Implementation.LanguageEnum.JAVA.toString()+"_REST")));
    }

    private void mockUpdateRepositoryImpl() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            return new SourceRepositoryCommit().id("d654f6dq4s");
        }).when(sourceRepositoryService)
                .commitArchiveAndTag(Mockito.argThat(src -> src.getId().equals(ImplementationServiceCreateTest.IMPL_SRC_REPO_ID)),
                        Mockito.eq("dev"),
                        Mockito.any(),
                        Mockito.eq("."),
                        Mockito.eq(IMPL_VERSION_VERSION));
    }

    private void mockApiVersion() throws ApiException {
        Mockito.doReturn(Optional.of(ImplementationServiceCreateTest.getApiVersion())).when(apiVersionService).getById(API_VERSION_ID);
    }

    private void mockImpl() throws ApiException {
        Mockito.doReturn(Optional.of(getImplementation())).when(implementationService).getById(ImplementationServiceCreateTest.IMPL_ID);
    }

    private void mockComponent() throws ApiException {
        Mockito.doReturn(Optional.of(getImplementation().getComponent())).when(componentService).getById(ImplementationServiceCreateTest.COMPONENT_ID);
    }

    private void mockSrcRepoApi() throws ApiException {
        Mockito.doReturn(Optional.of(getImplementation().getComponent().getApiRepository())).when(sourceRepositoryService).getById(ImplementationServiceCreateTest.API_SRC_REPO_ID);
    }

    private void mockSrcRepoImpl() throws ApiException {
        Mockito.doReturn(Optional.of(getImplementation().getSourceRepository())).when(sourceRepositoryService).getById(ImplementationServiceCreateTest.IMPL_SRC_REPO_ID);
    }

    private void mockResourceManager() throws Exception {
        //ADD
        Mockito.doAnswer(invocationOnMock -> {
            implementationVersionDb = invocationOnMock.getArgument(0);
            implementationVersionDb.id(IMPL_VERSION_ID);
            mockGetImplVersion();
            return copy(implementationVersionDb);
        }).when(resourceManager).addImplementationVersion(Mockito.any());

        // UPDATE
        Mockito.doAnswer(invocationOnMock -> {
            implementationVersionDb = invocationOnMock.getArgument(1);
            mockGetImplVersion();
            return copy(implementationVersionDb);
        }).when(resourceManager).updateImplementationVersion(Mockito.any(), Mockito.any());

        // PATCH
        Mockito.doAnswer(invocationOnMock -> {
            ImplementationVersion implementationPatch = invocationOnMock.getArgument(1);
            if (implementationPatch.getStatus() != null) {
                implementationVersionDb.status(implementationPatch.getStatus());
            }
            if (implementationPatch.getImplementation() != null) {
                implementationVersionDb.implementation(implementationPatch.getImplementation());
            }
            if (implementationPatch.getApiVersion() != null) {
                implementationVersionDb.apiVersion(implementationPatch.getApiVersion());
            }
            if (implementationPatch.getVersion() != null) {
                implementationVersionDb.version(implementationPatch.getVersion());
            }
            if (implementationPatch.getMetadata() != null) {
                if (implementationVersionDb.getMetadata() != null) {
                    implementationVersionDb.getMetadata().putAll(implementationPatch.getMetadata());
                } else {
                    implementationVersionDb.metadata(implementationPatch.getMetadata());
                }
            }

            mockGetImplVersion();
            return copy(implementationVersionDb);
        }).when(resourceManager).updateImplementationVersionAttributes(Mockito.eq(IMPL_VERSION_ID), Mockito.any());
    }

    private void mockGetImplVersion() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> copy(implementationVersionDb)).when(resourceManager).getImplementationVersion(Mockito.eq(IMPL_VERSION_ID));
    }

    private void mockGetFileRepository() throws ApiException, IOException {
        Mockito.doReturn(File.createTempFile("prefix-", "-suffix")).when(sourceRepositoryService).getFile(Mockito.argThat(src -> ImplementationServiceCreateTest.API_SRC_REPO_ID.equals(src.getId())), Mockito.eq(ImplementationServiceCreateTest.getApiVersion().getVersion()), Mockito.eq(ApiVersionService.API_FILENAME));
    }

    @Test
    public void given_nominal_args_when_create_and_until_ready_then_implVersion_is_ready() throws Exception {

        ImplementationVersion implVersion = underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, callback);
        assertImplVersionPending(implVersion);

        waitUntilNotPending(5000);
        Assertions.assertEquals(Resource.StatusEnum.READY, underTest.getById(IMPL_VERSION_ID).get().getStatus());
        super.callbackIsCalled(true);
    }

    private void assertImplVersionPending(ImplementationVersion implVersion) {
        Assertions.assertEquals(IMPL_VERSION_ID, implVersion.getId());
        Assertions.assertEquals(IMPL_VERSION_VERSION, implVersion.getVersion());
        Assertions.assertEquals(ImplementationServiceCreateTest.IMPL_ID, implVersion.getImplementation().getId());
        Assertions.assertEquals(API_VERSION_ID, implVersion.getApiVersion().getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, implVersion.getStatus());
    }

    @Test
    public void given_empty_file_api_repository_when_create_then_throw_exception() throws Exception {
        Mockito.doReturn(null).when(sourceRepositoryService).getFile(Mockito.any(), Mockito.any(), Mockito.any());
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("ApiFile is null or empty", exception.getMessage());
    }

    @Test
    public void given_error_update_repository_when_create_then_implVersion_is_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            throw new ApiException("error");
        }).when(sourceRepositoryService).commitArchiveAndTag(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        ImplementationVersion implVersion = underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack());
        assertImplVersionPending(implVersion);

        waitUntilNotPending(2000);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, underTest.getById(IMPL_VERSION_ID).get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_error_build_when_create_then_implVersion_is_error() throws Exception {

        mockPipelineBuild(Build.StatusEnum.FAILED);

        ImplementationVersion implVersion = underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack());
        assertImplVersionPending(implVersion);

        waitUntilNotPending(5000);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, underTest.getById(IMPL_VERSION_ID).get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_no_completed_build_when_create_then_implVersion_is_error() throws Exception {

        mockPipelineBuild(Build.StatusEnum.SCHEDULED);

        ImplementationVersion implVersion = underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack());
        assertImplVersionPending(implVersion);

        waitUntilNotPending(5000);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, underTest.getById(IMPL_VERSION_ID).get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_error_codegen_when_create_then_implVersion_is_error() throws Exception {

        /*Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            throw new ApiException("error");
        }).when(codegenClient).generateImplementation(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
*/
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            throw new ApiException("error");
        }).when(codegenClient).generateFromTemplate(Mockito.any());

        ImplementationVersion implVersion = underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack());
        assertImplVersionPending(implVersion);

        waitUntilNotPending(2000);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, underTest.getById(IMPL_VERSION_ID).get().getStatus());
        super.callbackIsCalled(true);
    }


    private Implementation getImplementation() {
        return ImplementationServiceCreateTest.generateImplementationExample(Implementation.LanguageEnum.JAVA).component(ImplementationServiceCreateTest.getComponent());
    }

    @Test
    public void given_error_imp_when_create_then_throw_IllegalStateException() throws Exception {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.create(getImplementation().status(Resource.StatusEnum.ERROR), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("Implementation implementation-id has an error", exception.getMessage());
    }

    @Test
    public void given_error_apiVersion_when_create_then_throw_IllegalStateException() throws Exception {
        Mockito.doReturn(Optional.of(ImplementationServiceCreateTest.getApiVersion().status(Resource.StatusEnum.ERROR))).when(apiVersionService).getById(API_VERSION_ID);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("ApiVersion api-version-id has an error", exception.getMessage());
    }

    @Test
    public void given_empty_version_when_create_then_throw_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), "", getCallBack()));
        Assertions.assertEquals("Implementation's version doesn't respect nomenclature X.X.X (ex: 1.0.1)", exception.getMessage());
    }

    @Test
    public void given_existing_version_when_create_then_throw_IllegalStateException() throws Exception {
        Mockito.doReturn(ImmutableList.of(new ImplementationVersion().version(IMPL_VERSION_VERSION).implementation(getImplementation()).apiVersion(getApiVersion()))).when(resourceManager).getImplementationVersions();
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("Implementation's version already exists", exception.getMessage());
    }

    @Test
    public void given_component_without_artifact_name_when_create_then_throw_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create(getImplementation().putMetadataItem(ImplementationService.METADATA_ARTIFACT_NAME, ""), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("Implementation's metadata artifact-artifactName is null or empty", exception.getMessage());
    }

    @Test
    public void given_component_without_artifact_groupId_when_create_then_throw_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create(getImplementation().putMetadataItem(ImplementationService.METADATA_ARTIFACT_GROUP_ID, ""), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("Implementation's metadata artifact-groupId is null or empty", exception.getMessage());
    }

    @Test
    public void given_exception_during_insert_when_create_then_throw_Exception() throws Exception {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            throw new ApiException("Error insert");
        }).when(resourceManager).addImplementationVersion(Mockito.any());
        Exception exception = assertThrows(Exception.class, () -> underTest.create(getImplementation(), ImplementationServiceCreateTest.getApiVersion(), IMPL_VERSION_VERSION, getCallBack()));
        Assertions.assertEquals("Error insert", exception.getMessage());
    }

    public void waitUntilNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(IMPL_VERSION_ID).get().getStatus();
        while (status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed " + (System.currentTimeMillis() - start) + " ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(IMPL_VERSION_ID).get().getStatus();
        }
    }

    private ImplementationVersion copy(ImplementationVersion implementationVersion) {
        return new ImplementationVersion().id(implementationVersion.getId())
                .name(implementationVersion.getName())
                .implementation(implementationVersion.getImplementation())
                .version(implementationVersion.getVersion())
                .status(implementationVersion.getStatus())
                .apiVersion(implementationVersion.getApiVersion())
                .sourceRepo(implementationVersion.getSourceRepo())
                .metadata(implementationVersion.getMetadata());
    }
}
