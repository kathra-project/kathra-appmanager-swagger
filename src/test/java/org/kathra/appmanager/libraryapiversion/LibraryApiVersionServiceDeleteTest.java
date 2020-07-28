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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.core.model.*;
import org.kathra.utils.ApiException;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class LibraryApiVersionServiceDeleteTest extends LibraryApiVersionServiceAbstractTest {

    @BeforeEach
    public void setUp() {
        super.reset();
    }

    @Test
    public void given_libraryApiVersion_when_delete_then_work() throws ApiException {
        LibraryApiVersion o = getLibraryApiVersionWithID();
        Mockito.when(resourceManager.getLibraryApiVersion(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteLibraryApiVersion(o.getId());
    }

    @Test
    public void given_libraryApiVersion_without_purge_when_delete_then_work() throws ApiException {
        LibraryApiVersion o = getLibraryApiVersionWithID();
        Mockito.when(resourceManager.getLibraryApiVersion(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        underTest.delete(o, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager).deleteLibraryApiVersion(o.getId());
    }

    @Test
    public void given_libraryApiVersion_deleted_when_delete_then_do_nothing() throws ApiException {
        LibraryApiVersion o = getLibraryApiVersionWithID();
        Mockito.when(resourceManager.getLibraryApiVersion(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.DELETED);
        underTest.delete(o, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, o.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteLibraryApiVersion(o.getId());
    }

    @Test
    public void given_libraryApiVersion_with_deletingError_when_delete_then_throws_exception() throws ApiException {
        LibraryApiVersion o = getLibraryApiVersionWithID();
        Mockito.when(resourceManager.getLibraryApiVersion(o.getId())).thenReturn(o);
        o.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deleteLibraryApiVersion(o.getId());
        assertThrows(ApiException.class, () -> {
            underTest.delete(o, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, o.getStatus());
    }

}
