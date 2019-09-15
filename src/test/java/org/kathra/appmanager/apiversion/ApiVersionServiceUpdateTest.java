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
package org.kathra.appmanager.apiversion;

import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.LibraryApiVersion;
import org.kathra.core.model.Resource;
import org.kathra.core.model.SourceRepositoryCommit;
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

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ApiVersionServiceUpdateTest extends AbstractApiVersionTest {

    @BeforeEach
    public void setUp() throws Exception {
        super.resetMock();
    }

    @Test
    public void given_null_apiVersion_when_update_then_throws_IllegalArgumentException() throws InterruptedException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.update(null, getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiVersion is null.", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_null_file_when_update_then_throws_IllegalArgumentException() throws InterruptedException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.update(getApiVersion(), null, getCallBack());
        });
        Assertions.assertEquals("File is null.", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_released_apiVersion_when_update_then_throws_IllegalStateException() throws InterruptedException {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            underTest.update(getApiVersion().released(true), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiVersion '"+getApiVersion().getId()+"' is already released.", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_not_ready_apiVersion_when_update_then_throws_IllegalStateException() throws InterruptedException {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            underTest.update(getApiVersion().status(Resource.StatusEnum.ERROR), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiVersion '"+getApiVersion().getId()+"' is not READY.", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_apiFile_apiVersion_version_not_equal_when_update_then_throws_IllegalArgumentException() throws InterruptedException {
        Mockito.when(openApiParser.getApiVersionFromApiFile(Mockito.any())).thenReturn(getApiVersionFromFile().version("1.0.1"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.update(getApiVersion(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiVersion and apiFile have different version. ApiVersion="+API_VERSION+" apiFile=1.0.1", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_apiFile_apiVersion_artifact_groupId_not_equal_when_update_then_throws_IllegalArgumentException() throws InterruptedException {
        Mockito.when(openApiParser.getApiVersionFromApiFile(Mockito.any())).thenReturn(getApiVersionFromFile().putMetadataItem(ApiVersionService.METADATA_API_GROUP_ID, "other"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.update(getApiVersion(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiVersion and apiFile have different artifact's groupId. ApiVersion="+ARTIFACT_GROUP+" apiFile=other", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_apiFile_apiVersion_artifact_name_not_equal_when_update_then_throws_IllegalArgumentException() throws InterruptedException {
        Mockito.when(openApiParser.getApiVersionFromApiFile(Mockito.any())).thenReturn(getApiVersionFromFile().putMetadataItem(ApiVersionService.METADATA_API_ARTIFACT_NAME, "other"));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.update(getApiVersion(), getApiFile(), getCallBack());
        });
        Assertions.assertEquals("ApiVersion and apiFile have different artifact's name. ApiVersion="+ARTIFACT_NAME+" apiFile=other", exception.getMessage());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_nominal_arg_when_update_and_wait_until_ready_then_apiVersion_is_ready() throws Exception {

        mockNominalBehavior();

        ApiVersion apiVersionReturned = underTest.update(apiVersionDb, getApiFile(), getCallBack());

        Assertions.assertEquals(Resource.StatusEnum.UPDATING, apiVersionReturned.getStatus());
        waitUntilNotUpdating(timeoutMax);
        ApiVersion readyApiVersion = underTest.getById(apiVersionReturned.getId()).get();
        Assertions.assertEquals(Resource.StatusEnum.READY, readyApiVersion.getStatus());

        for(LibraryApiVersion item:apiVersionReturned.getLibrariesApiVersions()) {
            Mockito.verify(libraryApiVersionService).update(Mockito.argThat(libraryApiVersion -> libraryApiVersion.getId().equals(item.getId())), Mockito.argThat(apiFile -> apiFile.getName().equals("swagger.yaml")), Mockito.any());
            Mockito.verify(libraryApiVersionService).build(Mockito.argThat(libraryApiVersion -> libraryApiVersion.getId().equals(item.getId())), Mockito.any());
            Assertions.assertEquals(LibraryApiVersion.PipelineStatusEnum.READY, libraryApiVersionService.getById(item.getId()).get().getPipelineStatus());
            Assertions.assertEquals(LibraryApiVersion.ApiRepositoryStatusEnum.READY, libraryApiVersionService.getById(item.getId()).get().getApiRepositoryStatus());
        }
        super.callbackIsCalled(true);
    }

    @Test
    public void given_occurred_exception_apiRepository_when_update_then_apiVersion_is_error() throws Exception {
        mockNominalBehavior();
        mockUpdateSwaggerFileIntoApiRepositoryWithException();
        ApiVersion apiVersionReturned = underTest.update(apiVersionDb, getApiFile(), getCallBack());

        Assertions.assertEquals(Resource.StatusEnum.UPDATING, apiVersionReturned.getStatus());
        waitUntilNotUpdating(timeoutMax);
        ApiVersion readyApiVersion = underTest.getById(apiVersionReturned.getId()).get();
        Assertions.assertEquals(Resource.StatusEnum.ERROR, readyApiVersion.getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_occurred_exception_build_lib_when_update_then_apiVersion_is_error() throws Exception {
        mockNominalBehavior();
        mockLibraryApiVersionBuildWithException();
        ApiVersion apiVersionReturned = underTest.update(apiVersionDb, getApiFile(), getCallBack());

        Assertions.assertEquals(Resource.StatusEnum.UPDATING, apiVersionReturned.getStatus());
        waitUntilNotUpdating(timeoutMax);
        ApiVersion readyApiVersion = underTest.getById(apiVersionReturned.getId()).get();
        Assertions.assertEquals(Resource.StatusEnum.ERROR, readyApiVersion.getStatus());
        super.callbackIsCalled(true);
    }


    private void mockNominalBehavior() throws ApiException {
        mockPatchApiVersion();
        Mockito .doReturn(new SourceRepositoryCommit().id("new commit"))
                .when(sourceRepositoryService)
                .commitFileAndTag(  Mockito.argThat(src -> src.getId().equals(SOURCE_REPOSITORY_API_ID)),
                                    Mockito.eq("dev"),
                                    Mockito.argThat(apiFile -> apiFile.getName().equals("swagger.yaml")),
                                    Mockito.eq("swagger.yaml"), Mockito.eq(API_VERSION));

        mockLibraryApiVersionServiceUpdate(500);
        mockLibraryApiVersionBuild(1000);
        apiVersionDb = getApiVersion();
        for(LibraryApiVersion item:apiVersionDb.getLibrariesApiVersions()) {
            libraryApiVersionsDb.put(item.getId(), item);
        }
    }

    private void mockLibraryApiVersionServiceUpdate(int updateSrcDuration) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            final LibraryApiVersion libraryApiVersion = invocationOnMock.getArgument(0);
            final Runnable callback = invocationOnMock.getArgument(2);
            CompletableFuture.runAsync(() -> {
               try {
                   Thread.sleep(updateSrcDuration);
                   libraryApiVersionsDb.get(libraryApiVersion.getId()).status(Resource.StatusEnum.READY);
                   callback.run();
               } catch(Exception e) {
                   e.printStackTrace();
               }
            });
            libraryApiVersionsDb.get(libraryApiVersion.getId()).status(Resource.StatusEnum.UPDATING);
            return libraryApiVersion.status(Resource.StatusEnum.UPDATING);
        }).when(libraryApiVersionService).update(Mockito.any(), Mockito.any(), Mockito.any());
    }

    public void waitUntilNotUpdating(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(getApiVersion().getId()).get().getStatus();
        while(status.equals(Resource.StatusEnum.UPDATING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(getApiVersion().getId()).get().getStatus();
        }
    }


}
