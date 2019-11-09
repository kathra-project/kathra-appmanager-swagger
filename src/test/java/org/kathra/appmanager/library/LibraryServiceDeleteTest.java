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
package org.kathra.appmanager.library;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryServiceTest;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.LibrariesClient;
import org.kathra.utils.ApiException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

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
public class LibraryServiceDeleteTest extends LibraryServiceTest {


    @BeforeEach
    public void setUp() throws ApiException {
        super.setUp();
    }

    @Test
    public void given_Library_when_delete_then_work() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, false, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteLibrary(o.getId());
        Mockito.verify(pipelineService).delete(o.getPipeline(), false);
        Mockito.verify(sourceRepositoryService).delete(o.getSourceRepository(), false);
    }

    private Library getLibrary() {
        return new Library().id("lib").status(Resource.StatusEnum.READY).sourceRepository(new SourceRepository()).pipeline(new Pipeline());
    }

    @Test
    public void given_Library_without_purge_when_delete_then_work() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, false, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteLibrary(o.getId());
        Mockito.verify(pipelineService).delete(o.getPipeline(), false);
        Mockito.verify(sourceRepositoryService).delete(o.getSourceRepository(), false);
    }

    @Test
    public void given_Library_with_purge_when_delete_then_work() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, false, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteLibrary(o.getId());
        Mockito.verify(pipelineService).delete(o.getPipeline(), true);
        Mockito.verify(sourceRepositoryService).delete(o.getSourceRepository(), true);
    }


    @Test
    public void given_Library_deleted_when_delete_then_do_nothing() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.DELETED);
        underTest.delete(o, false, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteLibrary(o.getId());
        Mockito.verify(pipelineService, Mockito.never()).delete(o.getPipeline(), false);
        Mockito.verify(sourceRepositoryService, Mockito.never()).delete(o.getSourceRepository(), false);
    }

    @Test
    public void given_Library_with_deletingError_when_delete_then_throws_exception() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deleteLibrary(o.getId());
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(resourceManager).deleteLibrary(o.getId());
        Mockito.verify(pipelineService).delete(o.getPipeline(), true);
        Mockito.verify(sourceRepositoryService).delete(o.getSourceRepository(), true);
    }

    @Test
    public void given_Library_with_deletingPipelineError_when_delete_then_throws_exception() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(pipelineService).delete(o.getPipeline(), true);
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteLibrary(o.getId());
        Mockito.verify(sourceRepositoryService, Mockito.never()).delete(o.getSourceRepository(), true);
    }

    @Test
    public void given_Library_with_deletingSrcRepoError_when_delete_then_throws_exception() throws ApiException {
        Library o = getLibrary();
        Mockito.when(resourceManager.getLibrary(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(sourceRepositoryService).delete(o.getSourceRepository(), true);
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteLibrary(o.getId());
        Mockito.verify(pipelineService).delete(o.getPipeline(), true);
    }

}
