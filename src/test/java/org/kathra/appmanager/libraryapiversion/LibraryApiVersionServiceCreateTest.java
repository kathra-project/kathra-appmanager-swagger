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

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.Library;
import org.kathra.core.model.LibraryApiVersion;
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

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class LibraryApiVersionServiceCreateTest extends LibraryApiVersionServiceAbstractTest {

    @BeforeEach
    public void setUp() throws Exception {
        reset();
        mockNominalBehavior();
        libraryApiVersionDb = null;
    }

    private void mockNominalBehavior() throws ApiException {
        Mockito.when(pipelineService.getById(PIPELINE_ID)).thenReturn(Optional.of(getPipeline()));
        Mockito.when(sourceRepositoryService.getById(REPOSITORY_ID)).thenReturn(Optional.of(getRepository()));
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary()));
        mockGetAddPatchResourceManager();
        Mockito.doReturn(getCommit()).when(sourceRepositoryService).commitArchiveAndTag(Mockito.argThat(sourceRepository -> sourceRepository.getId().equals(REPOSITORY_ID)),
                Mockito.eq("dev"), Mockito.any(), Mockito.any(), Mockito.eq(ARTIFACT_VERSION));
    }

    @Test
    public void given_empty_apiVersion_when_create_then_throws_IllegalArgumentException() throws InterruptedException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(null, getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("ApiVersion is null", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_empty_artifactName_when_create_then_throws_IllegalArgumentException() throws InterruptedException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getApiVersion().putMetadataItem(ApiVersionService.METADATA_API_ARTIFACT_NAME, ""), getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("ApiVersion's artifact-artifactName is null or empty", exception.getMessage());
        super.callbackIsCalled(false);
    }
    @Test
    public void given_empty_artifactGroup_when_create_then_throws_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getApiVersion().putMetadataItem(ApiVersionService.METADATA_API_GROUP_ID, ""), getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("ApiVersion's artifact-groupId is null or empty", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_empty_library_when_create_then_throws_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getApiVersion(), null, getApiFile(), getCallBack()));
        Assertions.assertEquals("Library is null", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_library_not_ready_when_create_then_throws_IllegalStateException() throws Exception {
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary().status(Resource.StatusEnum.ERROR)));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                underTest.create(getApiVersion(), getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("Library 'library-id' is not READY", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_src_not_ready_when_create_then_throws_IllegalStateException() throws Exception {
        Mockito.when(sourceRepositoryService.getById(REPOSITORY_ID)).thenReturn(Optional.of(getRepository().status(Resource.StatusEnum.ERROR)));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
                underTest.create(getApiVersion(), getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("SourceRepository 'repository-id' is not READY", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_existing_library_api_version_when_create_then_throws_IllegalArgumentException() throws Exception {

        LibraryApiVersion libApiVerExisting = new LibraryApiVersion().id("libApiVerExisting").library(getLibrary()).apiVersion(getApiVersion());
        final ApiVersion apiVersion = getApiVersion().librariesApiVersions(ImmutableList.of(libApiVerExisting));
        Mockito.doReturn(libApiVerExisting).when(resourceManager).getLibraryApiVersion(Mockito.eq("libApiVerExisting"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(apiVersion, getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("LibraryApiVersion linked ApiVersion 'api-version-id' and Library 'library-id' already exists", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_empty_apiFile_when_create_then_throws_IllegalArgumentException() throws InterruptedException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getApiVersion(), getLibrary(), null, getCallBack()));
        Assertions.assertEquals("File is null or empty", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_model_library_when_create_and_wait_until_readyRepositoryStatus_then_return_libraryApiVersion_has_readyRepositoryStatus() throws Exception {

        mockCodeGenGenerateModel();
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary().type(Library.TypeEnum.MODEL)));
        LibraryApiVersion libraryApiVersion = underTest.create(getApiVersion(), getLibrary().type(Library.TypeEnum.MODEL), getApiFile(), getCallBack());

        assertLibraryApiVersionPending(libraryApiVersion);
        waitUntilApiRepositoryStatusNotPending(5000);
        assertLibraryApiVersionPendingAndReadyApiRepositoryStatus(libraryApiVersionDb);
        super.callbackIsCalled(true);
    }

    @Test
    public void given_client_library_when_create_and_wait_until_readyRepositoryStatus_then_return_libraryApiVersion_has_readyRepositoryStatus() throws Exception {

        mockCodeGenGenerateClient();
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary().type(Library.TypeEnum.CLIENT)));
        LibraryApiVersion libraryApiVersion = underTest.create(getApiVersion(), getLibrary().type(Library.TypeEnum.CLIENT), getApiFile(), getCallBack());

        assertLibraryApiVersionPending(libraryApiVersion);
        waitUntilApiRepositoryStatusNotPending(5000);
        assertLibraryApiVersionPendingAndReadyApiRepositoryStatus(libraryApiVersionDb);
        super.callbackIsCalled(true);
    }
    @Test
    public void given_interface_library_when_create_and_wait_until_readyRepositoryStatus_then_return_libraryApiVersion_has_readyRepositoryStatus() throws Exception {

        mockCodeGenGenerateInterface();
        Mockito.when(libraryService.getById(LIBRARY_ID)).thenReturn(Optional.of(getLibrary().type(Library.TypeEnum.INTERFACE)));
        LibraryApiVersion libraryApiVersion = underTest.create(getApiVersion(), getLibrary().type(Library.TypeEnum.INTERFACE), getApiFile(), getCallBack());

        assertLibraryApiVersionPending(libraryApiVersion);
        waitUntilApiRepositoryStatusNotPending(5000);
        assertLibraryApiVersionPendingAndReadyApiRepositoryStatus(libraryApiVersionDb);
        super.callbackIsCalled(true);
    }


    @Test
    public void given_occurred_error_during_add_libraryApiVersion_when_create_then_throws_ApiException() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("Unable to add LibraryApiVersion");}).when(resourceManager).addLibraryApiVersion(Mockito.any());
        ApiException exception = assertThrows(ApiException.class, () ->
                underTest.create(getApiVersion(), getLibrary(), getApiFile(), getCallBack()));
        Assertions.assertEquals("Unable to add LibraryApiVersion", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_occurred_error_during_call_codeGen_when_create_and_wait_until_readyRepositoryStatus_then_apiVersion_has_status_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("Unable to generate source");}).when(codegenClient).generateFromTemplate(Mockito.any());
        underTest.create(getApiVersion(), getLibrary(), getApiFile(), getCallBack());
        waitUntilNotPending(5000);
        assertLibraryApiVersionErrorAndErrorApiRepositoryStatus(libraryApiVersionDb);
        super.callbackIsCalled(false);
    }

    @Test
    public void given_occurred_error_during_call_sourceManager_when_create_and_wait_until_readyRepositoryStatus_then_apiVersion_has_status_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("Unable to update source");}).when(sourceRepositoryService).commitArchiveAndTag(Mockito.argThat(sourceRepository -> sourceRepository.getId().equals(REPOSITORY_ID)),
                Mockito.eq("dev"), Mockito.any(), Mockito.any(), Mockito.eq(ARTIFACT_VERSION));

        underTest.create(getApiVersion(), getLibrary(), getApiFile(), getCallBack());
        waitUntilNotPending(5000);
        assertLibraryApiVersionErrorAndErrorApiRepositoryStatus(libraryApiVersionDb);
        super.callbackIsCalled(false);
    }

    public void waitUntilApiRepositoryStatusNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        LibraryApiVersion.ApiRepositoryStatusEnum status = underTest.getById(LIBRARY_API_VERSION_ID).get().getApiRepositoryStatus();
        while(status.equals(LibraryApiVersion.ApiRepositoryStatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(LIBRARY_API_VERSION_ID).get().getApiRepositoryStatus();
        }
    }

    public void waitUntilNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(LIBRARY_API_VERSION_ID).get().getStatus();
        while(status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(LIBRARY_API_VERSION_ID).get().getStatus();
        }
    }
}
