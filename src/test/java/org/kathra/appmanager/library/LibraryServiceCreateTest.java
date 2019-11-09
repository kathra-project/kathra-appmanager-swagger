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
public class LibraryServiceCreateTest extends LibraryServiceTest {

    @BeforeEach
    public void setUp() throws ApiException {
        super.setUp();
    }

    @Test
    public void when_getAll_then_return_items() throws Exception {
        Library item1 = new Library().name(LIBRARY_NAME+" 1");
        Library item2 = new Library().name(LIBRARY_NAME+" 2");
        Mockito.when(resourceManager.getLibraries()).thenReturn(ImmutableList.of(item1, item2));
        List<Library> items = underTest.getAll();
        Assertions.assertEquals(items.get(0), item1);
        Assertions.assertEquals(items.get(1), item2);
    }

    @Test
    public void given_null_component_when_add_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.add(null, Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, null));
        Assertions.assertEquals("Component is null", exception.getMessage());
    }

    @Test
    public void given_null_language_programming_when_add_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.add(getComponent(), null, Library.TypeEnum.MODEL, null));
        Assertions.assertEquals("Programming language is null", exception.getMessage());
    }

    @Test
    public void given_null_type_lib_when_add_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.add(getComponent(), Library.LanguageEnum.PYTHON, null, null));
        Assertions.assertEquals("Library's type is null", exception.getMessage());
    }

    @Test
    public void given_existing_lib_when_add_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
        {
            Library existing = new Library().id("existing").component(getComponent()).type(Library.TypeEnum.MODEL).language(Asset.LanguageEnum.PYTHON);
            Mockito.when(resourceManager.getLibrary("existing")).thenReturn(existing);
            Mockito.doReturn(Optional.of(getComponent().addLibrariesItem(existing))).when(componentService).getById(Mockito.eq(getComponent().getId()));
            underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, null);
        });
        Assertions.assertEquals("This library component already exists", exception.getMessage());
    }

    @Test
    public void given_component_lang_type_lib_and_wait_until_lib_is_ready_then_lib_is_ready() throws Exception {

        Library libraryPending = underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, callback);

        assertPendingLibrary(libraryPending);

        waitUntilLibraryIsNotPending(timeout);

        Optional<Library> libraryReady = underTest.getById(libraryPending.getId());
        Assertions.assertTrue(libraryReady.isPresent());
        assertReadyLibrary(libraryReady.get());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_sourceRepository_has_status_error_and_wait_until_lib_is_ready_then_lib_has_status_error() throws Exception {

        mockSourceRepositoryCreation(200, Resource.StatusEnum.ERROR);

        Library libraryPending = underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, callback);

        assertPendingLibrary(libraryPending);

        waitUntilLibraryIsNotPending(timeout);

        Optional<Library> libraryReady = underTest.getById(libraryPending.getId());
        Assertions.assertTrue(libraryReady.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, libraryReady.get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_pipeline_has_status_error_and_wait_until_lib_is_ready_then_lib_has_status_error() throws Exception {

        mockPipelineCreation(100, Resource.StatusEnum.ERROR);

        Library libraryPending = underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, callback);

        assertPendingLibrary(libraryPending);

        waitUntilLibraryIsNotPending(timeout);

        Optional<Library> libraryReady = underTest.getById(libraryPending.getId());
        Assertions.assertTrue(libraryReady.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, libraryReady.get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_component_lang_type_lib_and_dont_wait_until_lib_is_ready_then_lib_still_pending() throws Exception {

        Library libraryPending = underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, callback);

        assertPendingLibrary(libraryPending);

        Assertions.assertEquals(Resource.StatusEnum.PENDING, libraryDb.getStatus());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_an_occurred_error_calling_add_pipeline_and_wait_until_lib_is_ready_then_lib_is_error() throws Exception {

        mockPipelineCreationWithException(new Exception("pipeline creating error"));

        Library libraryPending = underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, callback);

        assertPendingLibrary(libraryPending);

        waitUntilLibraryIsNotPending(timeout);

        Optional<Library> libraryReady = underTest.getById(libraryPending.getId());
        Assertions.assertTrue(libraryReady.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, libraryReady.get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_an_occurred_error_calling_add_sourceRepository_and_wait_until_lib_is_ready_then_lib_is_error() throws Exception {

        mockSourceRepositoryCreationWithException(new Exception("source repository creating error"));

        Library libraryPending = underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, callback);

        assertPendingLibrary(libraryPending);

        waitUntilLibraryIsNotPending(timeout);

        Optional<Library> libraryReady = underTest.getById(libraryPending.getId());
        Assertions.assertTrue(libraryReady.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, libraryReady.get().getStatus());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_an_occurred_error_calling_add_library_and_wait_until_lib_is_ready_then_throws_exception() throws Exception {
        ApiException exception = assertThrows(ApiException.class, () -> {
            Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("Unable to add lib");}).when(resourceManager).addLibrary(Mockito.any());
            underTest.add(getComponent(), Library.LanguageEnum.PYTHON, Library.TypeEnum.MODEL, null);
        });
        Assertions.assertEquals("Unable to add lib", exception.getMessage());
    }
}
