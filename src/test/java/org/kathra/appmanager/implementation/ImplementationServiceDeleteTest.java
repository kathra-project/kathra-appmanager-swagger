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
package org.kathra.appmanager.implementation;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.core.model.Asset;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.ImplementationVersion;
import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test ImplementationService
 *
 * @author quentin.semanne
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ImplementationServiceDeleteTest extends ImplementationServiceTest {

    @BeforeEach
    void setUpEach() throws Exception {
        super.setUpEach();
    }

    public Implementation getImplementation() {
        return generateImplementationExample(Implementation.LanguageEnum.JAVA).versions(ImmutableList.of(new ImplementationVersion().id("id_version_impl")));
    }

    @Test
    public void given_implVersion_when_delete_then_work() throws ApiException {
        Implementation implV = getImplementation();
        Mockito.when(resourceManager.getImplementation(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        underTest.delete(implV, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, implV.getStatus());
        Mockito.verify(implementationVersionService).delete(implV.getVersions().get(0), true);
        Mockito.verify(pipelineService).delete(implV.getPipeline(), true);
        Mockito.verify(sourceRepositoryService).delete(implV.getSourceRepository(), true);
        Mockito.verify(resourceManager).deleteImplementation(implV.getId());
    }

    @Test
    public void given_implVersion_deleted_when_delete_then_do_nothing() throws ApiException {
        Implementation implV = getImplementation();
        Mockito.when(resourceManager.getImplementation(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.DELETED);
        underTest.delete(implV, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, implV.getStatus());
        Mockito.verify(implementationVersionService, Mockito.never()).delete(implV.getVersions().get(0), true);
        Mockito.verify(resourceManager, Mockito.never()).deleteImplementation(implV.getId());
        Mockito.verify(pipelineService, Mockito.never()).delete(implV.getPipeline(), true);
        Mockito.verify(sourceRepositoryService, Mockito.never()).delete(implV.getSourceRepository(), true);
    }

    @Test
    public void given_implVersion_with_deletingError_when_delete_then_throws_exception() throws ApiException {
        Implementation implV = getImplementation();
        Mockito.when(resourceManager.getImplementation(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deleteImplementation(implV.getId());
        assertThrows(ApiException.class, () -> {
            underTest.delete(implV, true);
        });
        Mockito.verify(implementationVersionService).delete(implV.getVersions().get(0), true);
        Mockito.verify(pipelineService).delete(implV.getPipeline(), true);
        Mockito.verify(sourceRepositoryService).delete(implV.getSourceRepository(), true);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implV.getStatus());
    }

    @Test
    public void given_implVersion_with_deletingPipelineError_when_delete_then_throws_exception() throws ApiException {
        Implementation implV = getImplementation();
        Mockito.when(resourceManager.getImplementation(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(pipelineService).delete(implV.getPipeline(), true);
        assertThrows(ApiException.class, () -> {
            underTest.delete(implV, true);
        });
        Mockito.verify(implementationVersionService).delete(implV.getVersions().get(0), true);
        Mockito.verify(sourceRepositoryService, Mockito.never()).delete(implV.getSourceRepository(), true);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implV.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteImplementation(implV.getId());
    }

    @Test
    public void given_implVersion_with_deletingSrcRepoError_when_delete_then_throws_exception() throws ApiException {
        Implementation implV = getImplementation();
        Mockito.when(resourceManager.getImplementation(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(sourceRepositoryService).delete(implV.getSourceRepository(), true);
        assertThrows(ApiException.class, () -> {
            underTest.delete(implV, true);
        });
        Mockito.verify(implementationVersionService).delete(implV.getVersions().get(0), true);
        Mockito.verify(pipelineService).delete(implV.getPipeline(), true);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implV.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteImplementation(implV.getId());
    }

    @Test
    public void given_implVersion_with_deletingImplVersionError_when_delete_then_throws_exception() throws ApiException {
        Implementation implV = getImplementation();
        Mockito.when(resourceManager.getImplementation(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(implementationVersionService).delete(implV.getVersions().get(0), true);
        assertThrows(ApiException.class, () -> {
            underTest.delete(implV, true);
        });
        Mockito.verify(pipelineService, Mockito.never()).delete(implV.getPipeline(), true);
        Mockito.verify(sourceRepositoryService, Mockito.never()).delete(implV.getSourceRepository(), true);
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implV.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteImplementation(implV.getId());
    }

}
