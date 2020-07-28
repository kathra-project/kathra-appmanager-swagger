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
package org.kathra.appmanager.pipeline;

import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.core.model.Library;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class PipelineServiceCreatePipelineLibraryTest extends PipelineServiceAbstractTest {


    Library library;
    String pathExpected;


    @BeforeEach
    public void setUp() throws ApiException {
        resetMock();
        mockNominalBehavior();
    }

    @Test
    public void given_no_sourceRepository_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryPipeline(getLibrary().sourceRepository(null), null));
        Assertions.assertEquals("SourceRepository's library is null", exception.getMessage());
    }

    @Test
    public void given_sshUrl_repository_empty_when_createLibraryRepository_then_throws_exception() throws ApiException {
        Mockito.doReturn(Optional.of(getSourceRepository().sshUrl(""))).when(this.sourceRepositoryService).getById(SRC_ID);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.createLibraryPipeline(getLibrary(), null));
        Assertions.assertEquals("SourceRepository sshUrl is null or empty", exception.getMessage());
    }

    @Test
    public void given_not_ready_repository_empty_when_createLibraryRepository_then_throws_exception() throws ApiException {
        Mockito.doReturn(Optional.of(getSourceRepository().status(Resource.StatusEnum.PENDING))).when(this.sourceRepositoryService).getById(SRC_ID);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.createLibraryPipeline(getLibrary(), null));
        Assertions.assertEquals("SourceRepository's library is not ready", exception.getMessage());
    }

    @Test
    public void given_no_component_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryPipeline(getLibrary().component(null), null));
        Assertions.assertEquals("Component's library is null", exception.getMessage());
    }


    @Test
    public void given_name_component_empty_when_createLibraryRepository_then_throws_exception() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent().name(""))).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.createLibraryPipeline(getLibrary(), null));
        Assertions.assertEquals("Component name is null or empty", exception.getMessage());
    }

    @Test
    public void given_groupPath_component_empty_when_createLibraryRepository_then_throws_exception() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent().putMetadataItem("groupPath", ""))).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.createLibraryPipeline(getLibrary(), null));
        Assertions.assertEquals("Component groupPath is null or empty", exception.getMessage());
    }

    @Test
    public void given_groupId_component_empty_when_createLibraryRepository_then_throws_exception() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent().putMetadataItem("groupId", ""))).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.createLibraryPipeline(getLibrary(), null));
        Assertions.assertEquals("Component groupId is null or empty", exception.getMessage());
    }

    @Test
    public void given_no_lib_type_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryPipeline(getLibrary().type(null), null));
        Assertions.assertEquals("Type's library is null", exception.getMessage());
    }

    @Test
    public void given_language_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryPipeline(getLibrary().language(null), null));
        Assertions.assertEquals("Programming language's library is null", exception.getMessage());
    }

    @Test
    public void given_library_when_createPipeline_and_wait_until_ready_then_pipeline_is_ready() throws Exception {
        Pipeline pipelinePending = underTest.createLibraryPipeline(library, getCallBack());
        assertPendingPipeline(id, credentialId, pathExpected, pipelinePending);

        waitUntilPipelineIsNotPending(200);

        assertionPipelineReady(id, providerId, provider, credentialId, pathExpected);
        super.callbackIsCalled(true);
    }

    private void mockNominalBehavior() throws ApiException {
        library = getLibrary();
        pathExpected = getPathExpectedFromLibrary(library);
        mockAddPipeline(id, library, pathExpected, pipelineDb);
        mockPipelineManager(id, provider, providerId, 100);
        mockPatchPipeline(id, pipelineDb);
        mockUpdatePipeline(id, pipelineDb);
    }

    @Test
    public void given_library_when_createPipeline_and_dont_wait_until_ready_then_pipeline_still_pending() throws Exception {
        Pipeline pipelinePending = underTest.createLibraryPipeline(library, getCallBack());
        assertPendingPipeline(id, credentialId, pathExpected, pipelinePending);

        Optional<Pipeline> pipelineReadyResult = underTest.getById(id);
        Assertions.assertTrue(pipelineReadyResult.isPresent());
        assertEquals(Resource.StatusEnum.PENDING, pipelineReadyResult.get().getStatus());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_occurred_error_during_update_pipeline_when_createPipeline_then_pipeline_has_status_error() throws Exception {
        Mockito.doAnswer(invocationOnMock -> {
            if (((Pipeline) invocationOnMock.getArgument(1)).getStatus().equals(Resource.StatusEnum.READY)) {
                throw new Exception("patch pipeline");
            } else {
                Pipeline pipelinePatch = invocationOnMock.getArgument(1);
                if (pipelinePatch.getStatus() != null) {
                    pipelineDb.status(pipelinePatch.getStatus());
                }
                if (pipelinePatch.getProviderId() != null) {
                    pipelineDb.providerId(pipelinePatch.getProviderId());
                }
                if (pipelinePatch.getProvider() != null) {
                    pipelineDb.provider(pipelinePatch.getProvider());
                }
                Mockito.when(resourceManager.getPipeline(pipelineDb.getId())).thenReturn(copy(pipelineDb));
                return pipelineDb;
            }
        }).when(resourceManager).updatePipeline(Mockito.eq(id), Mockito.any());

        Pipeline pipelinePending = underTest.createLibraryPipeline(library, getCallBack());
        assertPendingPipeline(id, credentialId, pathExpected, pipelinePending);

        Thread.sleep(200);
        Optional<Pipeline> pipelineReadyResult = underTest.getById(id);
        Assertions.assertTrue(pipelineReadyResult.isPresent());
        assertEquals(Resource.StatusEnum.ERROR, pipelineReadyResult.get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_occurred_error_during_call_pipelineManagerClient_when_createPipeline_then_pipeline_has_status_error() throws Exception {
        Mockito.doThrow(new ApiException("unable to create pipeline")).when(pipelineManagerClient).createPipeline(Mockito.argThat(pipeline -> pipeline.getId().equals(id)));

        Pipeline pipelinePending = underTest.createLibraryPipeline(library, getCallBack());
        assertPendingPipeline(id, credentialId, pathExpected, pipelinePending);

        waitUntilPipelineIsNotPending(200);
        Optional<Pipeline> pipelineReadyResult = underTest.getById(id);
        Assertions.assertTrue(pipelineReadyResult.isPresent());
        assertEquals(Resource.StatusEnum.ERROR, pipelineReadyResult.get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_occurred_error_during_add_pipeline_when_createPipeline_then_throw_exception() {

        ApiException exception = assertThrows(ApiException.class, () -> {
            Mockito.doThrow(new ApiException("unable to create pipeline"))
                    .when(resourceManager).addPipeline(Mockito.argThat(pipeline -> pipeline.getPath().equals(pathExpected) &&
                    pipeline.getTemplate().equals(Pipeline.TemplateEnum.PYTHON_LIBRARY)));

            mockPipelineManager(id, provider, providerId, 100);
            mockPatchPipeline(id, pipelineDb);
            mockUpdatePipeline(id, pipelineDb);

            underTest.createLibraryPipeline(library, getCallBack());
        });
        Assertions.assertEquals("unable to create pipeline", exception.getMessage());
    }
}
