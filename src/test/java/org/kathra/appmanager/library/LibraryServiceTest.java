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
package org.kathra.appmanager.library;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryServiceTest;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.LibrariesClient;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class LibraryServiceTest extends AbstractServiceTest {

    protected static final String REPOSITORY_IDENTIFIER = "repository-identifier";
    protected static final String PIPELINE_IDENTIFIER = "pipeline-identifier";
    protected static final String LIBRARY_ID = "library-id";
    protected static final String LIBRARY_NAME = "library-name";

    LibraryService underTest;

    @Mock
    LibrariesClient resourceManager;
    @Mock
    SourceRepositoryService sourceRepositoryService;
    @Mock
    PipelineService pipelineService;
    @Mock
    ComponentService componentService;

    Library libraryDb;

    long timeout = 4000;

    @BeforeEach
    public void setUp() throws ApiException {
        Mockito.reset(resourceManager);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(pipelineService);
        callback = Mockito.mock(Runnable.class);
        underTest = new LibraryService(resourceManager, componentService, pipelineService, sourceRepositoryService, kathraSessionManager);

        libraryDb = null;
        mockNominalBehavior();
    }


    protected void mockNominalBehavior() throws ApiException {
        mockComponent();
        mockAddLibrary();
        mockPatchResourceManager();
        mockSourceRepositoryCreation(200, Resource.StatusEnum.READY);
        mockPipelineCreation(100, Resource.StatusEnum.READY);
    }

    protected void waitUntilLibraryIsNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(libraryDb.getId()).get().getStatus();
        while(status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(libraryDb.getId()).get().getStatus();
        }
    }


    protected void mockPatchResourceManager() throws ApiException {
        Mockito.doAnswer(invocation ->  {
            if (libraryDb.getId().equals(invocation.getArgument(0))) {
                Library libPatch = invocation.getArgument(1);
                if (libPatch.getPipeline() != null) {
                    logger.info("Patch library '" + libraryDb.getId() + "' with pipeline " + libPatch.getSourceRepository().getId());
                    libraryDb.pipeline(libPatch.getPipeline());
                }
                if (libPatch.getSourceRepository() != null) {
                    logger.info("Patch library '" + libraryDb.getId() + "' with sourceRepository " + libPatch.getSourceRepository().getId());
                    libraryDb.sourceRepository(libPatch.getSourceRepository());
                }
                if (libPatch.getStatus() != null) {
                    logger.info("Patch library '" + libraryDb.getId() + "' with status " + libPatch.getStatus());
                    libraryDb.status(libPatch.getStatus());
                }
                Mockito.doReturn(copy(libraryDb)).when(resourceManager).getLibrary(Mockito.eq(LIBRARY_ID));
                return copy(libraryDb);
            } else {
                return null;
            }
        }).when(resourceManager).updateLibraryAttributes(Mockito.any(), Mockito.any());
    }

    protected void mockComponent() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent())).when(componentService).getById(Mockito.any());
    }

    protected void mockAddLibrary() throws ApiException {
        Mockito.doAnswer(invocation ->  {
            Library libPost = invocation.getArgument(0);
            libraryDb = new Library().id(LIBRARY_ID)
                    .name(libPost.getName())
                    .component(libPost.getComponent())
                    .language(libPost.getLanguage())
                    .type(libPost.getType())
                    .pipeline(libPost.getPipeline())
                    .sourceRepository(libPost.getSourceRepository())
                    .status(Resource.StatusEnum.PENDING);
            Mockito.doReturn(copy(libraryDb)).when(resourceManager).getLibrary(Mockito.eq(LIBRARY_ID));
            return copy(libraryDb);
        }).when(resourceManager).addLibrary(Mockito.any());
    }
    protected void mockSourceRepositoryCreationWithException(Exception exception) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            throw exception;
        }).when(sourceRepositoryService).createLibraryRepository(Mockito.argThat(lib -> lib.getId().equals(LIBRARY_ID)), Mockito.any());
    }

    protected void mockSourceRepositoryCreation(long duration, Resource.StatusEnum finalStatus) throws ApiException {
        Mockito.doAnswer(invocation -> {
            SourceRepository sourceRepository = new SourceRepository().id(REPOSITORY_IDENTIFIER).status(Resource.StatusEnum.PENDING);
            libraryDb.setSourceRepository(sourceRepository);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(duration);
                    SourceRepository sourceRepositoryReady = new SourceRepository().sshUrl(SourceRepositoryServiceTest.SSH_URL).id(REPOSITORY_IDENTIFIER).status(finalStatus);
                    libraryDb.setSourceRepository(sourceRepositoryReady);
                    Mockito.doReturn(copy(libraryDb)).when(resourceManager).getLibrary(Mockito.eq(LIBRARY_ID));
                    Mockito.when(sourceRepositoryService.getById(Mockito.eq(REPOSITORY_IDENTIFIER))).thenReturn(Optional.of(sourceRepositoryReady));
                    ((Runnable) invocation.getArgument(1)).run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Mockito.when(sourceRepositoryService.getById(Mockito.eq(REPOSITORY_IDENTIFIER))).thenReturn(Optional.of(sourceRepository));
            Mockito.doReturn(copy(libraryDb)).when(resourceManager).getLibrary(Mockito.eq(LIBRARY_ID));
            return sourceRepository;
        }).when(sourceRepositoryService).createLibraryRepository(Mockito.argThat(lib -> lib.getId().equals(LIBRARY_ID)), Mockito.any());
    }

    protected void mockPipelineCreationWithException(Exception exception) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(500);
            throw exception;
        }).when(pipelineService).createLibraryPipeline(Mockito.argThat(lib -> lib.getId().equals(LIBRARY_ID)), Mockito.any());
    }

    protected void mockPipelineCreation(long duration, Resource.StatusEnum finalStatus) throws ApiException {
        Mockito.doAnswer(invocation -> {
            Pipeline pipeline = new Pipeline().id(PIPELINE_IDENTIFIER).status(Resource.StatusEnum.PENDING);
            libraryDb.setPipeline(pipeline);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(duration);
                    Pipeline pipelineReady = new Pipeline().id(PIPELINE_IDENTIFIER).status(finalStatus);
                    libraryDb.setPipeline(pipelineReady);
                    Mockito.when(pipelineService.getById(Mockito.eq(PIPELINE_IDENTIFIER))).thenReturn(Optional.of(pipelineReady));
                    Mockito.doReturn(copy(libraryDb)).when(resourceManager).getLibrary(Mockito.eq(LIBRARY_ID));
                    ((Runnable) invocation.getArgument(1)).run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            Mockito.when(pipelineService.getById(Mockito.eq(PIPELINE_IDENTIFIER))).thenReturn(Optional.of(pipeline));
            Mockito.doReturn(copy(libraryDb)).when(resourceManager).getLibrary(Mockito.eq(LIBRARY_ID));
            return pipeline;
        }).when(pipelineService).createLibraryPipeline(Mockito.argThat(lib -> lib.getId().equals(LIBRARY_ID)), Mockito.any());
    }

    protected void assertPendingLibrary(Library library) {
        Assertions.assertNotNull(library);
        Assertions.assertEquals(LIBRARY_ID, library.getId());
        Assertions.assertEquals(getComponent().getName()+"-"+Library.LanguageEnum.PYTHON.toString()+"-"+Library.TypeEnum.MODEL.toString(), library.getName());
        Assertions.assertEquals(Library.TypeEnum.MODEL, library.getType());
        Assertions.assertEquals(Library.LanguageEnum.PYTHON, library.getLanguage());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, library.getStatus());
        Assertions.assertEquals(ComponentServiceTest.COMPONENT_ID, library.getComponent().getId());
    }

    protected void assertReadyLibrary(Library library) throws ApiException {
        Assertions.assertNotNull(library);
        Assertions.assertEquals(LIBRARY_ID, library.getId());
        Assertions.assertEquals(Library.TypeEnum.MODEL, library.getType());
        Assertions.assertEquals(Library.LanguageEnum.PYTHON, library.getLanguage());
        Assertions.assertEquals(Resource.StatusEnum.READY, library.getStatus());
        Assertions.assertEquals(ComponentServiceTest.COMPONENT_ID, library.getComponent().getId());
        Assertions.assertNotNull(library.getSourceRepository());
        Assertions.assertNotNull(library.getSourceRepository().getId());
        Assertions.assertEquals(Resource.StatusEnum.READY, sourceRepositoryService.getById(REPOSITORY_IDENTIFIER).get().getStatus());
        Assertions.assertNotNull(library.getPipeline());
        Assertions.assertNotNull(library.getPipeline().getId());
        Assertions.assertEquals(Resource.StatusEnum.READY, pipelineService.getById(PIPELINE_IDENTIFIER).get().getStatus());
        Assertions.assertEquals(Resource.StatusEnum.READY, library.getStatus());
    }

    public Component getComponent() {
        return new ComponentServiceTest().getComponentWithId();
    }

    private Library copy(Library library) {
        return new Library().id(library.getId())
                            .name(library.getName())
                            .status(library.getStatus())
                            .type(library.getType()).language(library.getLanguage())
                            .component(library.getComponent())
                            .pipeline(library.getPipeline())
                            .sourceRepository(library.getSourceRepository())
                            .metadata(library.getMetadata());
    }
}
