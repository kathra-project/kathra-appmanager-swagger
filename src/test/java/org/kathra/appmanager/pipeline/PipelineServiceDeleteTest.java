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
package org.kathra.appmanager.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.core.model.Build;
import org.kathra.core.model.ImplementationVersion;
import org.kathra.core.model.Pipeline;
import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class PipelineServiceDeleteTest extends PipelineServiceAbstractTest {

    @BeforeEach
    public void setUp() throws ApiException {
        resetMock();
    }


    @Test
    public void given_pipeline_when_delete_then_work() throws ApiException {
        Pipeline o = getPipeline();
        Mockito.when(resourceManager.getPipeline(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deletePipeline(o.getId());
        Mockito.verify(pipelineManagerClient).deletePipeline(o.getPath());
    }

    @Test
    public void given_pipeline_without_purge_when_delete_then_work() throws ApiException {
        Pipeline o = getPipeline();
        Mockito.when(resourceManager.getPipeline(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deletePipeline(o.getId());
        Mockito.verify(pipelineManagerClient, Mockito.never()).deletePipeline(o.getPath());
    }

    @Test
    public void given_pipeline_deleted_when_delete_then_do_nothing() throws ApiException {
        Pipeline o = getPipeline();
        Mockito.when(resourceManager.getPipeline(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.DELETED);
        underTest.delete(o, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deletePipeline(o.getId());
        Mockito.verify(pipelineManagerClient, Mockito.never()).deletePipeline(o.getPath());
    }

    @Test
    public void given_pipeline_with_deletingError_when_delete_then_throws_exception() throws ApiException {
        Pipeline o = getPipeline();
        Mockito.when(resourceManager.getPipeline(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deletePipeline(o.getId());
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(pipelineManagerClient).deletePipeline(o.getPath());
    }

    @Test
    public void given_pipeline_with_purgeError_when_delete_then_throws_exception() throws ApiException {
        Pipeline o = getPipeline();
        Mockito.when(resourceManager.getPipeline(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(pipelineManagerClient).deletePipeline(o.getPath());
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(pipelineManagerClient).deletePipeline(o.getPath());
        Mockito.verify(resourceManager, Mockito.never()).deletePipeline(o.getId());
    }
}
