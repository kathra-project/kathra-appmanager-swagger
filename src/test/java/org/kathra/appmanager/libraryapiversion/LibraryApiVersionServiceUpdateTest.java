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
import org.kathra.appmanager.apiversion.OpenApiParser;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.LibraryApiVersionsClient;
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
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class LibraryApiVersionServiceUpdateTest extends LibraryApiVersionServiceAbstractTest {

    @BeforeEach
    public void setUp() throws Exception {
        reset();
        mockNominalBehavior();
    }

    @Test
    public void given_null_LibraryApiVersion_when_update_then_throws_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.update(null, getApiFile(), getCallBack()));
        Assertions.assertEquals("LibraryApiVersion is null", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_null_file_when_update_then_throws_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.update(getLibraryApiVersionWithID(), null, getCallBack()));
        Assertions.assertEquals("File is null or empty", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Disabled
    @Test
    public void given_error_LibraryApiVersion_when_update_then_throws_IllegalStateException() throws Exception {
        mockNominalBehavior();
        libraryApiVersionDb = getLibraryApiVersionWithID().status(Resource.StatusEnum.ERROR);
        libraryApiVersionDb.name("library-api-version-name");
        Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                underTest.update(getLibraryApiVersionWithID(), getApiFile(), null));
        Assertions.assertEquals("LibraryApiVersion 'library-api-version-id' 'library-api-version-name' is not READY", exception.getMessage());
        super.callbackIsCalled(false);
    }


    @Test
    public void given_nominal_args_when_update_and_wait_until_readyRepositoryStatus_is_ready_then_works() throws Exception {

        LibraryApiVersion libraryApiVersion = underTest.update(getLibraryApiVersionWithID(), getApiFile(), getCallBack());

        Assertions.assertEquals(Resource.StatusEnum.UPDATING, libraryApiVersion.getStatus());

        assertLibraryApiVersionUpdating(libraryApiVersion);
        waitUntilApiRepositoryStatusNotUpdating(5000);

        LibraryApiVersion libraryApiVersionSrcUpdated = underTest.getById(LIBRARY_API_VERSION_ID).get();
        assertLibraryApiVersionUpdatingAndReadyApiRepositoryStatus(libraryApiVersionSrcUpdated);
        super.callbackIsCalled(true);
    }

    @Test
    public void given_an_exception_occurred_generating_src_when_update_and_wait_until_readyRepositoryStatus_is_error_then_apiRepositoryStatus_Error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(1000); throw new ApiException("Unable to generate source");}).when(codegenClient).generateFromTemplate(Mockito.any());

        LibraryApiVersion libraryApiVersion = underTest.update(getLibraryApiVersionWithID(), getApiFile(), getCallBack());

        Assertions.assertEquals(Resource.StatusEnum.UPDATING, libraryApiVersion.getStatus());

        assertLibraryApiVersionUpdating(libraryApiVersion);
        waitUntilApiRepositoryStatusNotUpdating(5000);

        LibraryApiVersion libraryApiVersionSrcUpdated = underTest.getById(LIBRARY_API_VERSION_ID).get();
        assertLibraryApiVersionErrorAndErrorApiRepositoryStatus(libraryApiVersionSrcUpdated);
        super.callbackIsCalled(true);
    }

    @Test
    public void given_occurred_error_during_call_sourceManager_when_update_and_wait_until_readyRepositoryStatus_then_apiRepositoryStatus_Error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(1000); throw new ApiException("Unable to update source");}).when(sourceRepositoryService).commitArchiveAndTag(Mockito.argThat(sourceRepository -> sourceRepository.getId().equals(REPOSITORY_ID)),
                Mockito.eq("dev"), Mockito.any(), Mockito.any(), Mockito.eq(ARTIFACT_VERSION));

        LibraryApiVersion libraryApiVersion = underTest.update(getLibraryApiVersionWithID(), getApiFile(), getCallBack());

        assertLibraryApiVersionUpdating(libraryApiVersion);
        waitUntilApiRepositoryStatusNotUpdating(5000);

        LibraryApiVersion libraryApiVersionSrcError = underTest.getById(LIBRARY_API_VERSION_ID).get();
        assertLibraryApiVersionErrorAndErrorApiRepositoryStatus(libraryApiVersionSrcError);
        super.callbackIsCalled(true);
    }

    private void mockNominalBehavior() throws ApiException {

        Mockito.when(pipelineService.getById(PIPELINE_ID)).thenReturn(Optional.of(getPipeline()));
        Mockito.when(sourceRepositoryService.getById(REPOSITORY_ID)).thenReturn(Optional.of(getRepository()));
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary()));
        Mockito.when(apiVersionService.getById(API_VERSION_ID)).thenReturn(Optional.of(getApiVersion()));

        mockGetAddPatchResourceManager();

        Mockito.doReturn(getCommit()).when(sourceRepositoryService).commitArchiveAndTag(Mockito.argThat(sourceRepository -> sourceRepository.getId().equals(REPOSITORY_ID)),
                                                                                                        Mockito.eq("dev"), Mockito.any(), Mockito.any(), Mockito.eq(ARTIFACT_VERSION));
        mockCodeGenGenerateClient();
        mockCodeGenGenerateModel();
        mockCodeGenGenerateInterface();
    }


    public void waitUntilApiRepositoryStatusNotUpdating(long timeout) throws Exception {
        long start = System.currentTimeMillis();
        Thread.sleep(500);
        LibraryApiVersion.ApiRepositoryStatusEnum status = underTest.getById(LIBRARY_API_VERSION_ID).get().getApiRepositoryStatus();
        while(status.equals(LibraryApiVersion.ApiRepositoryStatusEnum.UPDATING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(LIBRARY_API_VERSION_ID).get().getApiRepositoryStatus();
        }
    }

}
