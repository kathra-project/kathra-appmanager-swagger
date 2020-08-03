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
package org.kathra.appmanager.component;


import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.core.model.Component;
import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;

/**
 * Test ComponentService
 *
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ComponentServiceRemoveTest extends ComponentServiceTest {

    @BeforeEach
    public void setUp() {
        super.setUp();
    }

    @Test
    public void given_component_without_impl_when_delete_then_works() throws ApiException {
        Component component = getComponentFullyInitialized();
        component.setImplementations(ImmutableList.of());
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        underTest.delete(component, false, false);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, component.getStatus());
        Mockito.verify(sourceRepositoryService).delete(component.getApiRepository(), false);
        Mockito.verify(libraryService).delete(component.getLibraries().get(0), false, false);
        Mockito.verify(libraryService).delete(component.getLibraries().get(1), false, false);
        Mockito.verify(apiVersionService).delete(component.getVersions().get(0), false, false);
        Mockito.verify(apiVersionService).delete(component.getVersions().get(1), false, false);
        Mockito.verify(resourceManager).deleteComponent(component.getId());
    }

    @Test
    public void given_component_with_impl_and_force_and_purge_when_delete_then_works() throws ApiException {
        Component component = getComponentFullyInitialized();
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        underTest.delete(component, true, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, component.getStatus());
        Mockito.verify(sourceRepositoryService).delete(component.getApiRepository(), true);
        Mockito.verify(libraryService).delete(component.getLibraries().get(0), true, true);
        Mockito.verify(libraryService).delete(component.getLibraries().get(1), true, true);
        Mockito.verify(apiVersionService).delete(component.getVersions().get(0), true, true);
        Mockito.verify(apiVersionService).delete(component.getVersions().get(1), true, true);
        Mockito.verify(resourceManager).deleteComponent(component.getId());
    }

    @Test
    public void given_component_with_impl_when_delete_then_throws_exception() throws ApiException {
        Component component = getComponentFullyInitialized();
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            underTest.delete(component, false, true);
        });
        Assertions.assertEquals("Component component-id contains implementations, delete its implementations before", exception.getMessage());
        Assertions.assertEquals(Resource.StatusEnum.READY, component.getStatus());
        Mockito.verify(sourceRepositoryService, never()).delete(component.getApiRepository(), true);
        Mockito.verify(libraryService, never()).delete(component.getLibraries().get(0), true, true);
        Mockito.verify(libraryService, never()).delete(component.getLibraries().get(1), true, true);
        Mockito.verify(apiVersionService, never()).delete(component.getVersions().get(0), true, true);
        Mockito.verify(apiVersionService, never()).delete(component.getVersions().get(1), true, true);
        Mockito.verify(resourceManager, never()).deleteComponent(component.getId());
    }

    @Test
    public void given_component_with_errorDeleting_when_delete_then_component_has_error_status() throws ApiException {
        Component component = getComponentFullyInitialized();
        component.setImplementations(ImmutableList.of());
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deleteComponent(COMPONENT_ID);
        assertThrows(ApiException.class, () -> {
            underTest.delete(component, false, false);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, component.getStatus());
    }

    @Test
    public void given_component_with_sourceRepoDeleting_when_delete_then_component_has_error_status() throws ApiException {
        Component component = getComponentFullyInitialized();
        component.setImplementations(ImmutableList.of());
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        Mockito.doThrow(new ApiException("Internal error")).when(sourceRepositoryService).delete(component.getApiRepository(), false);
        assertThrows(ApiException.class, () -> {
            underTest.delete(component, false, false);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, component.getStatus());
    }
    @Test
    public void given_component_with_apiVersionErrorDeleting_when_delete_then_component_has_error_status() throws ApiException {
        Component component = getComponentFullyInitialized();
        component.setImplementations(ImmutableList.of());
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        Mockito.doThrow(new ApiException("Internal error")).when(apiVersionService).delete(component.getVersions().get(0), false, false);
        assertThrows(ApiException.class, () -> {
            underTest.delete(component, false, false);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, component.getStatus());
    }
    @Test
    public void given_component_with_libErrorDeleting_when_delete_then_component_has_error_status() throws ApiException {
        Component component = getComponentFullyInitialized();
        component.setImplementations(ImmutableList.of());
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        Mockito.doThrow(new ApiException("Internal error")).when(libraryService).delete(component.getLibraries().get(0), false, false);
        assertThrows(ApiException.class, () -> {
            underTest.delete(component, false, false);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, component.getStatus());
    }
    @Test
    public void given_component_with_implErrorDeleting_when_delete_then_component_has_error_status() throws ApiException {
        Component component = getComponentFullyInitialized();
        Mockito.when(resourceManager.getComponent(Mockito.eq(COMPONENT_ID))).thenReturn(component);
        Mockito.doThrow(new ApiException("Internal error")).when(implementationService).delete(component.getImplementations().get(0), false);
        assertThrows(ApiException.class, () -> {
            underTest.delete(component, true, false);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, component.getStatus());
    }
}