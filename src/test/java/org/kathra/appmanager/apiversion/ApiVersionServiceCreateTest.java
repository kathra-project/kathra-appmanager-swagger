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

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.core.model.*;
import org.kathra.utils.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ApiVersionServiceCreateTest extends AbstractApiVersionTest {

    @BeforeEach
    public void setUp() throws Exception {
        super.resetMock();
    }

    @Test
    public void given_nominal_args_when_create_and_dont_wait_until_ready_then_apiVersion_still_pending() throws Exception {
        mockNominalBehavior();
        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);
    }

    @Test
    public void given_empty_identifier_component_when_create_then_throws_IllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.create("", getApiFile(), getCallBack());
        });
        Assertions.assertEquals("componentId is null or empty", exception.getMessage());
    }

    @Test
    public void given_no_existing_component_when_create_then_throws_IllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.create("not-existing-component", getApiFile(), getCallBack());
        });
        Assertions.assertEquals("Component 'not-existing-component' doesn't exist", exception.getMessage());
    }

    @Test
    public void given_component_not_ready_when_create_then_throws_IllegalStateException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            Mockito.doReturn(Optional.of(getComponent().status(Resource.StatusEnum.ERROR))).when(componentService).getById(Mockito.eq(COMPONENT_ID));
            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("Component '"+COMPONENT_ID+"' is not READY.", exception.getMessage());
    }

    @Test
    public void given_malformated_apiFile_when_create_then_throws_IllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Mockito .doAnswer(invocationOnMock -> {
                throw new IllegalArgumentException("ApiFile doesn't respect OpenApi specifications");
            }).when(openApiParser).getApiVersionFromApiFile(Mockito.any());
            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiFile doesn't respect OpenApi specifications", exception.getMessage());
    }

    @Test
    public void given_malformated_version_apiFile_when_create_then_throws_IllegalArgumentException() {
        Mockito.when(openApiParser.getApiVersionFromApiFile(Mockito.any())).thenReturn(getApiVersionFromFile().version("1.2.0-SNAPSHOT"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("Version do not respect nomenclature X.X.X (ex: 1.0.1)", exception.getMessage());
    }

    @Test
    public void given_apiVersionExisting_when_create_then_throws_IllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            List<ApiVersion> existing = ImmutableList.of(new ApiVersion().component(getComponent()).version(API_VERSION));
            Mockito.when(resourceManager.getApiVersions()).thenReturn(existing);
            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("Component with version '1.0.0' already existing", exception.getMessage());
    }

    @Test
    public void given_artifactIdExistingInAnotherComponent_when_create_then_throws_IllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            List<ApiVersion> existing = ImmutableList.of(new ApiVersion()   .component(getComponent().id("another-component"))
                    .version(API_VERSION)
                    .putMetadataItem(ApiVersionService.METADATA_API_GROUP_ID, ARTIFACT_GROUP )
                    .putMetadataItem(ApiVersionService.METADATA_API_ARTIFACT_NAME, ARTIFACT_NAME ));
            Mockito.when(resourceManager.getApiVersions()).thenReturn(existing);
            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("A another component 'another-component' using the same groupId and artifactId", exception.getMessage());
    }



    @Test
    public void given_an_occurred_error_during_add_resourceManager_when_create_then_throws_ApiException() {
        ApiException exception = assertThrows(ApiException.class, () -> {
            Mockito.doAnswer(invocation -> {
                throw new ApiException("apiVersion creation error");
            }).when(resourceManager).addApiVersion(Mockito.any());

            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("apiVersion creation error", exception.getMessage());
    }

    @Test
    public void given_apiFile_dont_have_version_during_add_resourceManager_when_create_then_throws_IllegalArgumentException() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Mockito.when(openApiParser.getApiVersionFromApiFile(Mockito.any())).thenReturn(new ApiVersion().version("").metadata(new HashMap<>()));
            underTest.create(getComponent(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("Version should be defined", exception.getMessage());
    }

    @Test
    public void given_nominal_args_when_create_and_wait_until_ready_then_apiVersion_is_ready() throws Exception {

        mockNominalBehavior();

        ArgumentCaptor<Component> componentPatchedCaptor = ArgumentCaptor.forClass(Component.class);
        Mockito.doNothing().when(componentService).patch(componentPatchedCaptor.capture());

        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);
        Component componentPatched = componentPatchedCaptor.getValue();
        assertEquals(ARTIFACT_GROUP, componentPatched.getMetadata().get(ComponentService.METADATA_API_GROUP_ID));
        assertEquals(ARTIFACT_NAME, componentPatched.getMetadata().get(ComponentService.METADATA_API_ARTIFACT_NAME));

        waitUntilNotPending(timeoutMax);
        assertionApiVersionReady(apiVersionDb);
    }


    private void mockNominalBehavior() throws ApiException {
        mockAddApiVersion();
        mockPatchApiVersion();
        mockUpdateApiVersion();

        mockUpdateSwaggerFileIntoApiRepository();
        mockApiVersionLibraryService(200);
        mockLibraryApiVersionBuild(200);
    }

    @Test
    public void given_an_occurred_exception_during_update_source_apirepository_when_create_and_dont_wait_until_ready_then_apiVersion_is_error() throws Exception {

        mockNominalBehavior();

        mockUpdateSwaggerFileIntoApiRepositoryWithException();

        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);

        waitUntilNotPending(timeoutMax);

        Optional<ApiVersion> apiVersion = underTest.getById(API_VERSION_ID);
        Assertions.assertTrue(apiVersion.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.get().getStatus());
    }

    @Test
    public void given_an_occurred_exception_during_create_apiVersionLib_when_create_and_dont_wait_until_ready_then_apiVersion_is_error() throws Exception {

        mockNominalBehavior();
        mockApiVersionLibraryServiceWithException();

        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);

        waitUntilNotPending(timeoutMax);

        Optional<ApiVersion> apiVersion = underTest.getById(API_VERSION_ID);
        Assertions.assertTrue(apiVersion.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.get().getStatus());
    }

    @Test
    public void given_an_occurred_error_during_create_apiVersionLib_when_create_and_dont_wait_until_ready_then_apiVersion_is_error() throws Exception {

        mockNominalBehavior();
        mockApiVersionLibraryServiceWithError(200);

        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);

        waitUntilNotPending(timeoutMax);

        Optional<ApiVersion> apiVersion = underTest.getById(API_VERSION_ID);
        Assertions.assertTrue(apiVersion.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.get().getStatus());
    }

    @Test
    public void given_an_occurred_error_during_execute_pipeline_apiVersionLib_when_create_and_dont_wait_until_ready_then_apiVersion_is_error()  throws Exception {

        mockNominalBehavior();
        mockLibraryApiVersionBuildWithError(200);

        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);

        waitUntilNotPending(timeoutMax);

        Optional<ApiVersion> apiVersion = underTest.getById(API_VERSION_ID);
        Assertions.assertTrue(apiVersion.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.get().getStatus());
    }

    @Test
    public void given_an_occurred_exception_during_execute_pipeline_apiVersionLib_when_create_and_dont_wait_until_ready_then_apiVersion_is_error()  throws Exception {

        mockNominalBehavior();
        mockLibraryApiVersionBuildWithException();

        ApiVersion apiVersionPending = underTest.create(getComponent(), getApiFile(), getCallBack());
        assertionApiVersionPending(apiVersionPending);

        waitUntilNotPending(timeoutMax);

        Optional<ApiVersion> apiVersion = underTest.getById(API_VERSION_ID);
        Assertions.assertTrue(apiVersion.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.get().getStatus());
    }

    public void waitUntilNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(API_VERSION_ID).get().getStatus();
        while(status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(API_VERSION_ID).get().getStatus();
        }
    }

    @Test
    public void given_an_occurred_error_during_update_apiVersion_when_create_and_dont_wait_until_ready_then_apiVersion_is_error()  throws Exception {

    }

    private void mockApiVersionLibraryService(long creatingDuration) throws ApiException {

        Mockito.doAnswer(invocationOnMock -> {
            try {
                LibraryApiVersion libraryApiVersionPending = getLibraryApiVersion(invocationOnMock.getArgument(0), invocationOnMock.getArgument(1));
                mockGetApiVersionLibrary(libraryApiVersionPending);
                Runnable callback = invocationOnMock.getArgument(3);
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(creatingDuration);
                        LibraryApiVersion libraryApiVersionReady = new LibraryApiVersion()
                                .apiVersion(libraryApiVersionPending.getApiVersion())
                                .library(libraryApiVersionPending.getLibrary())
                                .apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.READY)
                                .status(Resource.StatusEnum.PENDING)
                                .id(libraryApiVersionPending.getId());
                        mockGetApiVersionLibrary(libraryApiVersionReady);
                        callback.run();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
                return libraryApiVersionPending;
            } catch(Exception e) {
                e.printStackTrace();
                throw e;
            }
        }).when(libraryApiVersionService).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void mockApiVersionLibraryServiceWithError(long creatingDuration) throws ApiException {

        Mockito.doAnswer(invocationOnMock -> {
            LibraryApiVersion libraryApiVersionPending = getLibraryApiVersion(invocationOnMock.getArgument(0), invocationOnMock.getArgument(1));
            Runnable callback = invocationOnMock.getArgument(3);
            mockGetApiVersionLibrary(libraryApiVersionPending);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(creatingDuration);
                    LibraryApiVersion libraryApiVersionReady = new LibraryApiVersion()
                            .apiVersion(libraryApiVersionPending.getApiVersion())
                            .library(libraryApiVersionPending.getLibrary())
                            .library(libraryApiVersionPending.getLibrary())
                            .apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.ERROR)
                            .status(Resource.StatusEnum.ERROR)
                            .id(libraryApiVersionPending.getId());
                    mockGetApiVersionLibrary(libraryApiVersionReady);
                    callback.run();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            return libraryApiVersionPending;
        }).when(libraryApiVersionService).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private LibraryApiVersion getLibraryApiVersion(ApiVersion apiVersion, Library library) {
        return new LibraryApiVersion()
                .apiVersion(apiVersion)
                .library(library)
                .id(library.getId()+"_"+apiVersion.getVersion()+UUID.randomUUID().toString())
                .pipelineStatus(LibraryApiVersion.PipelineStatusEnum.PENDING)
                .apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.PENDING)
                .status(Resource.StatusEnum.PENDING);
    }

    private void mockApiVersionLibraryServiceWithException() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(libraryApiVersionService).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void mockAddApiVersion() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            ApiVersion apiVersion = invocationOnMock.getArgument(0);
            apiVersionDb = new ApiVersion().id(API_VERSION_ID)
                            .version(apiVersion.getVersion())
                            .name(apiVersion.getName())
                            .component(apiVersion.getComponent())
                            .librariesApiVersions(apiVersion.getLibrariesApiVersions())
                            .apiRepositoryStatus(apiVersion.getApiRepositoryStatus())
                            .status(Resource.StatusEnum.PENDING)
                            .released(apiVersion.getReleased())
                            .metadata(apiVersion.getMetadata())
                            .implementationsVersions(apiVersion.getImplementationsVersions());
            Mockito.doReturn(copy(apiVersionDb)).when(resourceManager).getApiVersion(Mockito.anyString());
            return copy(apiVersionDb);
        }).when(resourceManager).addApiVersion(Mockito.argThat(i -> i.getVersion().equals(API_VERSION)));
    }



    private void assertionApiVersionReady(ApiVersion apiVersion) {
        Assertions.assertEquals(API_VERSION_ID, apiVersion.getId());
        Assertions.assertEquals(API_NAME, apiVersion.getName());
        Assertions.assertEquals(API_VERSION, apiVersion.getVersion());
        Assertions.assertEquals(Resource.StatusEnum.READY, apiVersion.getStatus());
        Assertions.assertEquals(COMPONENT_ID, apiVersion.getComponent().getId());
        Assertions.assertEquals(Library.TypeEnum.values().length * Library.LanguageEnum.values().length, apiVersion.getLibrariesApiVersions().size());

        for(Library.LanguageEnum lang : Library.LanguageEnum.values()) {
            for(Library.TypeEnum type : Library.TypeEnum.values()) {
                Assertions.assertEquals(true, apiVersion.getLibrariesApiVersions().stream().anyMatch(lib ->  lib.getLibrary().getType().equals(type) &&
                        lib.getLibrary().getLanguage().equals(lang)));
            }
        }
    }

    private void assertionApiVersionPending(ApiVersion apiVersion) {
        Assertions.assertNotNull(apiVersion);
        Assertions.assertEquals(API_VERSION_ID, apiVersion.getId());
        Assertions.assertEquals(API_NAME, apiVersion.getName());
        Assertions.assertEquals(API_VERSION, apiVersion.getVersion());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, apiVersion.getStatus());
        Assertions.assertEquals(COMPONENT_ID, apiVersion.getComponent().getId());
    }
}
