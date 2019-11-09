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
package org.kathra.appmanager.sourcerepository;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.core.model.Pipeline;
import org.kathra.core.model.Resource;
import org.kathra.core.model.SourceRepository;
import org.kathra.utils.ApiException;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test SourceRepositoryService
 *
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class SourceRepositoryServiceDeleteTest extends SourceRepositoryServiceAbstractTest {

    @BeforeEach
    public void setUp() {
        super.setUp();
    }

    @Test
    public void given_sourceRepo_when_delete_then_work() throws ApiException {
        SourceRepository o = getSourceRepositoryForDb();
        Mockito.when(resourceManager.getSourceRepository(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteSourceRepository(o.getId());
        Mockito.verify(sourceManager).deleteSourceRepository(o.getPath());
    }

    @Test
    public void given_sourceRepo_without_purge_when_delete_then_work() throws ApiException {
        SourceRepository o = getSourceRepositoryForDb();
        Mockito.when(resourceManager.getSourceRepository(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteSourceRepository(o.getId());
        Mockito.verify(sourceManager, Mockito.never()).deleteSourceRepository(o.getPath());
    }

    @Test
    public void given_sourceRepo_deleted_when_delete_then_do_nothing() throws ApiException {
        SourceRepository o = getSourceRepositoryForDb();
        Mockito.when(resourceManager.getSourceRepository(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.DELETED);
        underTest.delete(o, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteSourceRepository(o.getId());
        Mockito.verify(sourceManager, Mockito.never()).deleteSourceRepository(o.getPath());
    }

    @Test
    public void given_sourceRepo_with_deletingError_when_delete_then_throws_exception() throws ApiException {
        SourceRepository o = getSourceRepositoryForDb();
        Mockito.when(resourceManager.getSourceRepository(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deleteSourceRepository(o.getId());
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(sourceManager).deleteSourceRepository(o.getPath());
    }

    @Test
    public void given_sourceRepo_with_purgeError_when_delete_then_throws_exception() throws ApiException {
        SourceRepository o = getSourceRepositoryForDb();
        Mockito.when(resourceManager.getSourceRepository(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(sourceManager).deleteSourceRepository(o.getPath());
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
        Mockito.verify(sourceManager).deleteSourceRepository(o.getPath());
        Mockito.verify(resourceManager, Mockito.never()).deleteSourceRepository(o.getId());
    }

}
