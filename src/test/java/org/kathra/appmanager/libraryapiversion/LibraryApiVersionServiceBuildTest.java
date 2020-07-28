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
package org.kathra.appmanager.libraryapiversion;

import org.junit.jupiter.api.Disabled;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.core.model.Build;
import org.kathra.core.model.LibraryApiVersion;
import org.kathra.core.model.Pipeline;
import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class LibraryApiVersionServiceBuildTest extends LibraryApiVersionServiceAbstractTest {

    @BeforeEach
    public void setUp() throws Exception {
        reset();
        super.libraryApiVersionDb = getLibraryApiVersionWithID();
        mockNominalBehavior();
    }

    private void mockNominalBehavior() throws ApiException {
        Mockito.when(pipelineService.getById(PIPELINE_ID)).thenReturn(Optional.of(getPipeline()));
        Mockito.when(apiVersionService.getById(getApiVersion().getId())).thenReturn(Optional.of(getApiVersion()));
        Mockito.when(sourceRepositoryService.getById(REPOSITORY_ID)).thenReturn(Optional.of(getRepository()));
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary()));
        mockGetAddPatchResourceManager();
        Mockito.doReturn(getCommit()).when(sourceRepositoryService).commitArchiveAndTag(Mockito.argThat(sourceRepository -> sourceRepository.getId().equals(REPOSITORY_ID)),
                                                                                                        Mockito.eq("dev"), Mockito.any(), Mockito.any(), Mockito.eq(ARTIFACT_VERSION));
    }

    public void waitUntilPipelineStatusNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        LibraryApiVersion.PipelineStatusEnum status = underTest.getById(LIBRARY_API_VERSION_ID).get().getPipelineStatus();
        while(status.equals(LibraryApiVersion.PipelineStatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(LIBRARY_API_VERSION_ID).get().getPipelineStatus();
        }
    }

    public Build getBuild() {
        return new Build().buildNumber(BUILD_NUMBER);
    }

    @Test
    public void given_apiVersionLibrary_ready_when_build_then_return_build() throws ApiException, InterruptedException {
        libraryApiVersionDb = getLibraryApiVersionWithID();
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        Mockito.doReturn(getBuild()).when(pipelineService).build(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq(ARTIFACT_VERSION), Mockito.isNull(), Mockito.notNull());
        Build build = underTest.build(libraryApiVersionDb, getCallBack());
        Assertions.assertNotNull(build);
        Assertions.assertEquals(BUILD_NUMBER, build.getBuildNumber());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_apiVersionLibrary_ready_when_build_and_wait_until_pipelineReady_then_libraryApiVersion_pipelineStatus_is_ready() throws Exception {
        libraryApiVersionDb = getLibraryApiVersionWithID();
        mockPipelineService();

        Build build = underTest.build(libraryApiVersionDb, getCallBack());
        Assertions.assertEquals(LibraryApiVersion.PipelineStatusEnum.PENDING, libraryApiVersionDb.getPipelineStatus());
        Assertions.assertNotNull(build);
        Assertions.assertEquals(BUILD_NUMBER, build.getBuildNumber());

        waitUntilPipelineStatusNotPending(20000);
        Assertions.assertEquals(LibraryApiVersion.PipelineStatusEnum.READY, underTest.getById(libraryApiVersionDb.getId()).get().getPipelineStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_pipeline_build_failed_when_build_and_wait_until_pipelineReady_then_libraryApiVersion_pipelineStatus_is_error() throws Exception {
        libraryApiVersionDb = getLibraryApiVersionWithID();
        mockPipelineServiceWithFailed();

        Build build = underTest.build(libraryApiVersionDb, getCallBack());
        Assertions.assertEquals(LibraryApiVersion.PipelineStatusEnum.PENDING, libraryApiVersionDb.getPipelineStatus());
        Assertions.assertNotNull(build);
        Assertions.assertEquals(BUILD_NUMBER, build.getBuildNumber());

        waitUntilPipelineStatusNotPending(20000);
        Assertions.assertEquals(LibraryApiVersion.PipelineStatusEnum.ERROR, underTest.getById(libraryApiVersionDb.getId()).get().getPipelineStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_pipeline_build_exception_when_build_and_wait_until_pipelineReady_then_throw_exception() throws Exception {
        libraryApiVersionDb = getLibraryApiVersionWithID();
        mockPipelineServiceWithException();

        ApiException exception = assertThrows(ApiException.class, () ->
                underTest.build(libraryApiVersionDb, getCallBack()));
        Assertions.assertEquals("Error during build", exception.getMessage());
        super.callbackIsCalled(false);
    }

    private void mockPipelineServiceWithException() throws ApiException {
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        Mockito .doThrow(new ApiException("Error during build")).when(pipelineService).build(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq(ARTIFACT_VERSION), Mockito.isNull(), Mockito.notNull());
    }

    private void mockPipelineServiceWithFailed() throws ApiException {
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        Mockito .doAnswer(invocationOnMock -> {
            Runnable callback = invocationOnMock.getArgument(3);
            mockBuild(getBuild().status(Build.StatusEnum.SCHEDULED));
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                    mockBuild(getBuild().status(Build.StatusEnum.FAILED));
                } catch (Exception e) {}
                callback.run();
            });
            mockBuild(getBuild().status(Build.StatusEnum.SCHEDULED));
            return getBuild();
        }).when(pipelineService).build(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq(ARTIFACT_VERSION), Mockito.isNull(), Mockito.notNull());
    }

    private void mockPipelineService() throws ApiException {
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        Mockito .doAnswer(invocationOnMock -> {
            Pipeline pipeline = invocationOnMock.getArgument(0);
            Runnable callback = invocationOnMock.getArgument(3);
            mockBuild(getBuild().status(Build.StatusEnum.SCHEDULED));
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(500);
                    mockBuild(getBuild().status(Build.StatusEnum.SUCCESS));
                } catch (Exception e) {}
                callback.run();
            });
            mockBuild(getBuild().status(Build.StatusEnum.SCHEDULED));
            return getBuild();
        }).when(pipelineService).build(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq(ARTIFACT_VERSION), Mockito.isNull(), Mockito.notNull());
    }

    private void mockBuild(Build build) throws ApiException {
        Mockito.doReturn(build).when(pipelineService).getBuild(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq(build.getBuildNumber()));
    }

    @Disabled
    @Test
    public void given_error_apiVersionLibrary_when_build_then_throws_illegalStateException() throws ApiException {
        libraryApiVersionDb = getLibraryApiVersionWithID().status(Resource.StatusEnum.ERROR);
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.build(libraryApiVersionDb, getCallBack()));
        Assertions.assertEquals("LibraryApiVersion has status ERROR", exception.getMessage());
    }

    @Test
    public void given_status_repository_apiVersionLibrary_when_build_then_throws_illegalStateException() throws ApiException {
        libraryApiVersionDb = getLibraryApiVersionWithID().apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.ERROR);
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.build(libraryApiVersionDb, getCallBack()));
        Assertions.assertEquals("LibraryApiVersion's repository status is not READY", exception.getMessage());
    }

    @Test
    public void given_apiVersionLibrary_without_library_when_build_then_throws_illegalStateException() throws ApiException {
        libraryApiVersionDb = getLibraryApiVersionWithID().library(null);
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.build(libraryApiVersionDb, getCallBack()));
        Assertions.assertEquals("LibraryApiVersion's Library is null", exception.getMessage());
    }

    @Test
    public void given_apiVersionLibrary_without_apiVersion_when_build_then_throws_illegalStateException() throws ApiException {
        libraryApiVersionDb = getLibraryApiVersionWithID().apiVersion(null);
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.build(libraryApiVersionDb, getCallBack()));
        Assertions.assertEquals("LibraryApiVersion's ApiVersion is null", exception.getMessage());
    }
}
