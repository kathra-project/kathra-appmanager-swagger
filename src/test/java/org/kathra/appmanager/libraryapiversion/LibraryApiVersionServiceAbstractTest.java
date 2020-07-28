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

import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.LibraryApiVersionsClient;
import org.kathra.utils.ApiException;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class LibraryApiVersionServiceAbstractTest  extends AbstractServiceTest {

    protected static final String BUILD_NUMBER = "build-id";
    protected static String LIBRARY_API_VERSION_ID = "library-api-version-id";
    protected static String LIBRARY_ID = "library-id";
    protected static String API_VERSION_ID = "api-version-id";
    protected static String PIPELINE_ID = "pipeline-id";
    protected static String REPOSITORY_ID = "repository-id";
    protected final static String ARTIFACT_NAME = "artifact-name";
    protected final static String ARTIFACT_GROUP = "artifact-group";
    protected final static String ARTIFACT_VERSION = "1.0.0";


    @Mock
    LibraryApiVersionsClient resourceManager;
    @Mock
    SourceRepositoryService sourceRepositoryService;
    @Mock
    LibraryService libraryService;
    @Mock
    PipelineService pipelineService;
    @Mock
    CodegenClient codegenClient;
    @Mock
    ApiVersionService apiVersionService;

    LibraryApiVersionService underTest;

    LibraryApiVersion libraryApiVersionDb;

    public void reset() {
        Mockito.reset(resourceManager);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(libraryService);
        Mockito.reset(resourceManager);
        Mockito.reset(pipelineService);
        Mockito.reset(codegenClient);
        Mockito.reset(apiVersionService);
        underTest = new LibraryApiVersionService(resourceManager, codegenClient, libraryService, pipelineService, sourceRepositoryService, apiVersionService, kathraSessionManager);
        callback = Mockito.mock(Runnable.class);
    }
    public SourceRepository getRepository() {
        return new SourceRepository().id(REPOSITORY_ID).status(Resource.StatusEnum.READY);
    }

    public Pipeline getPipeline() {
        return new Pipeline().id(PIPELINE_ID).status(Resource.StatusEnum.READY).sourceRepository(getRepository());
    }

    public ApiVersion getApiVersion() {
        return new ApiVersion().version(ARTIFACT_VERSION).id(API_VERSION_ID).component(getComponent()).released(false)
                .librariesApiVersions(new ArrayList<>())
                .putMetadataItem(ApiVersionService.METADATA_API_ARTIFACT_NAME, ARTIFACT_NAME)
                .putMetadataItem(ApiVersionService.METADATA_API_GROUP_ID, ARTIFACT_GROUP);
    }

    public Component getComponent() {
        return new ComponentServiceTest().getComponentWithId();
    }

    public LibraryApiVersion getLibraryApiVersionWithID() {
        return new LibraryApiVersion()  .id(LIBRARY_API_VERSION_ID)
                .library(getLibrary())
                .apiVersion(getApiVersion())
                .apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.READY)
                .pipelineStatus(LibraryApiVersion.PipelineStatusEnum.READY)
                .status(Resource.StatusEnum.READY);
    }

    public File getApiFile() throws IOException {
        File tempFile = File.createTempFile("prefix-", "-suffix");
        return tempFile;
    }

    public Library getLibrary() {
        return new Library().id(LIBRARY_ID)
                .component(getComponent())
                .language(Library.LanguageEnum.JAVA)
                .type(Library.TypeEnum.MODEL)
                .status(Resource.StatusEnum.READY)
                .sourceRepository(getRepository())
                .pipeline(getPipeline());
    }


    public void mockCodeGenGenerateModel() throws ApiException {
        Mockito.doAnswer(invocation -> {
            Thread.sleep(1000);
            return getSrcGenerated();
        }).when(codegenClient).generateFromTemplate(Mockito.argThat(t -> t.getName().equals("LIBRARY_"+Library.LanguageEnum.JAVA.toString()+"_REST_MODEL")));
    }
    public void mockCodeGenGenerateClient() throws ApiException {
        Mockito.doAnswer(invocation -> {
            Thread.sleep(1000);
            return getSrcGenerated();
        }).when(codegenClient).generateFromTemplate(Mockito.argThat(t -> t.getName().equals("LIBRARY_"+Library.LanguageEnum.JAVA.toString()+"_REST_CLIENT")));
    }
    public void mockCodeGenGenerateInterface() throws ApiException {
        Mockito.doAnswer(invocation -> {
            Thread.sleep(1000);
            return getSrcGenerated();
        }).when(codegenClient).generateFromTemplate(Mockito.argThat(t -> t.getName().equals("LIBRARY_"+Library.LanguageEnum.JAVA.toString()+"_REST_INTERFACE")));
    }

    public File getSrcGenerated() {
        return Mockito.mock(File.class);
    }


    public SourceRepositoryCommit getCommit() {
        return new SourceRepositoryCommit().id(UUID.randomUUID().toString());
    }

    public void mockGetAddPatchResourceManager() throws ApiException {
        libraryApiVersionDb = getLibraryApiVersionWithID();

        Mockito.doAnswer(invocationOnMock -> {
            return libraryApiVersionDb;
        }).when(resourceManager).getLibraryApiVersion(LIBRARY_API_VERSION_ID);

        Mockito.doAnswer(addInvocation -> {
            LibraryApiVersion libraryApiVersion = addInvocation.getArgument(0);
            libraryApiVersionDb =  new LibraryApiVersion()  .id(LIBRARY_API_VERSION_ID)
                    .library(libraryApiVersion.getLibrary())
                    .apiVersion(libraryApiVersion.getApiVersion())
                    .apiRepositoryStatus(libraryApiVersion.getApiRepositoryStatus())
                    .pipelineStatus(libraryApiVersion.getPipelineStatus())
                    .status(Resource.StatusEnum.PENDING);
            Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
            return libraryApiVersionDb;
        }).when(resourceManager).addLibraryApiVersion(Mockito.any());

        Mockito.doAnswer(updateInvocation -> {
            LibraryApiVersion libraryApiVersion = updateInvocation.getArgument(1);
            if (updateInvocation.getArgument(0).equals(libraryApiVersionDb.getId())) {
                libraryApiVersionDb .status(libraryApiVersion.getStatus())
                        .library(libraryApiVersion.getLibrary())
                        .apiVersion(libraryApiVersion.getApiVersion())
                        .pipelineStatus(libraryApiVersion.getPipelineStatus())
                        .apiRepositoryStatus(libraryApiVersion.getApiRepositoryStatus())
                        .metadata(libraryApiVersion.getMetadata());
                Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
            }
            return libraryApiVersionDb;
        }).when(resourceManager).updateLibraryApiVersion(Mockito.any(), Mockito.any());

        Mockito.doAnswer(patchInvocation -> {
            LibraryApiVersion libraryApiVersion = patchInvocation.getArgument(1);
            if (patchInvocation.getArgument(0).equals(libraryApiVersionDb.getId())) {
                if (libraryApiVersion.getStatus() != null) {
                    libraryApiVersionDb.status(libraryApiVersion.getStatus());
                }
                if (libraryApiVersion.getLibrary() != null) {
                    libraryApiVersionDb.library(libraryApiVersion.getLibrary());
                }
                if (libraryApiVersion.getApiVersion() != null) {
                    libraryApiVersionDb.apiVersion(libraryApiVersion.getApiVersion());
                }
                if (libraryApiVersion.getPipelineStatus() != null) {
                    libraryApiVersionDb.pipelineStatus(libraryApiVersion.getPipelineStatus());
                }
                if (libraryApiVersion.getApiRepositoryStatus() != null) {
                    libraryApiVersionDb.apiRepositoryStatus(libraryApiVersion.getApiRepositoryStatus());
                }
                if (libraryApiVersion.getMetadata() != null) {
                    libraryApiVersionDb.metadata(libraryApiVersion.getMetadata());
                }
                Mockito.when(resourceManager.getLibraryApiVersion(LIBRARY_API_VERSION_ID)).thenReturn(libraryApiVersionDb);
            }
            return libraryApiVersionDb;
        }).when(resourceManager).updateLibraryApiVersionAttributes(Mockito.any(), Mockito.any());
    }

    public void assertLibraryApiVersionUpdating(LibraryApiVersion libraryApiVersion) {
        Assertions.assertNotNull(libraryApiVersion);
        Assertions.assertEquals(LIBRARY_API_VERSION_ID, libraryApiVersion.getId());
        Assertions.assertEquals(Resource.StatusEnum.UPDATING, libraryApiVersion.getStatus());
        Assertions.assertEquals(LIBRARY_ID, libraryApiVersion.getLibrary().getId());
        Assertions.assertEquals(API_VERSION_ID, libraryApiVersion.getApiVersion().getId());
    }
    public void assertLibraryApiVersionUpdatingAndReadyApiRepositoryStatus(LibraryApiVersion libraryApiVersion) {
        Assertions.assertNotNull(libraryApiVersion);
        Assertions.assertEquals(LIBRARY_API_VERSION_ID, libraryApiVersion.getId());
        Assertions.assertEquals(Resource.StatusEnum.UPDATING, libraryApiVersion.getStatus());
        Assertions.assertEquals(LIBRARY_ID, libraryApiVersion.getLibrary().getId());
        Assertions.assertEquals(API_VERSION_ID, libraryApiVersion.getApiVersion().getId());
        Assertions.assertEquals(LibraryApiVersion.ApiRepositoryStatusEnum.READY, libraryApiVersion.getApiRepositoryStatus());
    }
    public void assertLibraryApiVersionErrorAndErrorApiRepositoryStatus(LibraryApiVersion libraryApiVersion) {
        Assertions.assertNotNull(libraryApiVersion);
        Assertions.assertEquals(LIBRARY_API_VERSION_ID, libraryApiVersion.getId());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, libraryApiVersion.getStatus());
        Assertions.assertEquals(LIBRARY_ID, libraryApiVersion.getLibrary().getId());
        Assertions.assertEquals(API_VERSION_ID, libraryApiVersion.getApiVersion().getId());
        Assertions.assertEquals(LibraryApiVersion.ApiRepositoryStatusEnum.ERROR, libraryApiVersion.getApiRepositoryStatus());
    }

    public void assertLibraryApiVersionPending(LibraryApiVersion libraryApiVersion) {
        Assertions.assertNotNull(libraryApiVersion);
        Assertions.assertEquals(LIBRARY_API_VERSION_ID, libraryApiVersion.getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, libraryApiVersion.getStatus());
        Assertions.assertEquals(LIBRARY_ID, libraryApiVersion.getLibrary().getId());
        Assertions.assertEquals(API_VERSION_ID, libraryApiVersion.getApiVersion().getId());
    }
    public void assertLibraryApiVersionPendingAndReadyApiRepositoryStatus(LibraryApiVersion libraryApiVersion) {
        Assertions.assertNotNull(libraryApiVersion);
        Assertions.assertEquals(LIBRARY_API_VERSION_ID, libraryApiVersion.getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, libraryApiVersion.getStatus());
        Assertions.assertEquals(LIBRARY_ID, libraryApiVersion.getLibrary().getId());
        Assertions.assertEquals(API_VERSION_ID, libraryApiVersion.getApiVersion().getId());
        Assertions.assertEquals(LibraryApiVersion.ApiRepositoryStatusEnum.READY, libraryApiVersion.getApiRepositoryStatus());
    }


}
