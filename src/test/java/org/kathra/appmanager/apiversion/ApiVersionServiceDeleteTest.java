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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.core.model.*;
import org.kathra.utils.ApiException;
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
public class ApiVersionServiceDeleteTest extends AbstractApiVersionTest {

    @BeforeEach
    public void setUp() throws Exception {
        super.resetMock();
    }

    @Test
    public void given_component_without_implV_when_delete_then_works() throws ApiException {
        ApiVersion apiVersion = getApiVersion();
        apiVersion.setImplementationsVersions(ImmutableList.of());
        apiVersion.setLibrariesApiVersions(ImmutableList.of(new LibraryApiVersion().id("impl-version"), new LibraryApiVersion().id("impl-version-2")));
        Mockito.when(resourceManager.getApiVersion(Mockito.eq(API_VERSION_ID))).thenReturn(apiVersion);
        underTest.delete(apiVersion, true, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, apiVersion.getStatus());
        Mockito.verify(libraryApiVersionService).delete(apiVersion.getLibrariesApiVersions().get(0), true);
        Mockito.verify(libraryApiVersionService).delete(apiVersion.getLibrariesApiVersions().get(1), true);
        Mockito.verify(resourceManager).deleteApiVersion(apiVersion.getId());
    }

    @Test
    public void given_component_with_implV_and_Force_when_delete_then_works() throws ApiException {
        ApiVersion apiVersion = getApiVersion();
        apiVersion.setImplementationsVersions(ImmutableList.of(new ImplementationVersion().id("impl-version"), new ImplementationVersion().id("impl-version-2")));
        apiVersion.setLibrariesApiVersions(ImmutableList.of(new LibraryApiVersion().id("impl-version"), new LibraryApiVersion().id("impl-version-2")));
        Mockito.when(resourceManager.getApiVersion(Mockito.eq(API_VERSION_ID))).thenReturn(apiVersion);
        underTest.delete(apiVersion, true, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, apiVersion.getStatus());
        Mockito.verify(implementationVersionService).delete(apiVersion.getImplementationsVersions().get(0), true);
        Mockito.verify(implementationVersionService).delete(apiVersion.getImplementationsVersions().get(1), true);
        Mockito.verify(libraryApiVersionService).delete(apiVersion.getLibrariesApiVersions().get(0), true);
        Mockito.verify(libraryApiVersionService).delete(apiVersion.getLibrariesApiVersions().get(1), true);
        Mockito.verify(resourceManager).deleteApiVersion(apiVersion.getId());
    }

    @Test
    public void given_component_with_implV_noForce_when_delete_then_throws_illegalstateexception() throws ApiException {
        ApiVersion apiVersion = getApiVersion();
        apiVersion.setImplementationsVersions(ImmutableList.of(new ImplementationVersion().id("impl-version"), new ImplementationVersion().id("impl-version-2")));
        apiVersion.setLibrariesApiVersions(ImmutableList.of(new LibraryApiVersion().id("impl-version"), new LibraryApiVersion().id("impl-version-2")));

        Mockito.when(resourceManager.getApiVersion(Mockito.eq(API_VERSION_ID))).thenReturn(apiVersion);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            underTest.delete(apiVersion, true, false);;
        });
        Assertions.assertEquals("ApiVersion "+apiVersion.getId()+" is used by some versions of implementations, delete its versions implementations before", exception.getMessage());

        Assertions.assertEquals(Resource.StatusEnum.READY, apiVersion.getStatus());
        Mockito.verify(implementationVersionService, Mockito.never()).delete(apiVersion.getImplementationsVersions().get(0), true);
        Mockito.verify(implementationVersionService, Mockito.never()).delete(apiVersion.getImplementationsVersions().get(1), true);
        Mockito.verify(libraryApiVersionService, Mockito.never()).delete(apiVersion.getLibrariesApiVersions().get(0), true);
        Mockito.verify(libraryApiVersionService, Mockito.never()).delete(apiVersion.getLibrariesApiVersions().get(1), true);
        Mockito.verify(resourceManager, Mockito.never()).deleteApiVersion(apiVersion.getId());
    }

    @Test
    public void given_component_with_errorDelete_and_Force_when_delete_then_throws_apiException() throws ApiException {
        ApiVersion apiVersion = getApiVersion();
        apiVersion.setImplementationsVersions(ImmutableList.of(new ImplementationVersion().id("impl-version"), new ImplementationVersion().id("impl-version-2")));
        apiVersion.setLibrariesApiVersions(ImmutableList.of(new LibraryApiVersion().id("impl-version"), new LibraryApiVersion().id("impl-version-2")));

        Mockito.when(resourceManager.getApiVersion(Mockito.eq(API_VERSION_ID))).thenReturn(apiVersion);

        Mockito.doThrow(new ApiException("internal error")).when(resourceManager).getApiVersion(Mockito.eq(API_VERSION_ID));
        assertThrows(ApiException.class, () -> {
            underTest.delete(apiVersion, true, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteApiVersion(apiVersion.getId());
    }

    @Test
    public void given_component_with_deleteImplVersionError_and_Force_when_delete_then_throws_apiException() throws ApiException {
        ApiVersion apiVersion = getApiVersion();
        apiVersion.setImplementationsVersions(ImmutableList.of(new ImplementationVersion().id("impl-version"), new ImplementationVersion().id("impl-version-2")));
        apiVersion.setLibrariesApiVersions(ImmutableList.of(new LibraryApiVersion().id("impl-version"), new LibraryApiVersion().id("impl-version-2")));

        Mockito.when(resourceManager.getApiVersion(Mockito.eq(API_VERSION_ID))).thenReturn(apiVersion);

        Mockito.doThrow(new ApiException("internal error")).when(implementationVersionService).delete(Mockito.any(), Mockito.anyBoolean());
        assertThrows(ApiException.class, () -> {
            underTest.delete(apiVersion, true, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteApiVersion(apiVersion.getId());
    }

    @Test
    public void given_component_with_deleteApiLibVersionError_and_Force_when_delete_then_throws_apiException() throws ApiException {
        ApiVersion apiVersion = getApiVersion();
        apiVersion.setImplementationsVersions(ImmutableList.of(new ImplementationVersion().id("impl-version"), new ImplementationVersion().id("impl-version-2")));
        apiVersion.setLibrariesApiVersions(ImmutableList.of(new LibraryApiVersion().id("impl-version"), new LibraryApiVersion().id("impl-version-2")));

        Mockito.when(resourceManager.getApiVersion(Mockito.eq(API_VERSION_ID))).thenReturn(apiVersion);

        Mockito.doThrow(new ApiException("internal error")).when(libraryApiVersionService).delete(Mockito.any(), Mockito.anyBoolean());
        assertThrows(ApiException.class, () -> {
            underTest.delete(apiVersion, true, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, apiVersion.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteApiVersion(apiVersion.getId());
    }
}
