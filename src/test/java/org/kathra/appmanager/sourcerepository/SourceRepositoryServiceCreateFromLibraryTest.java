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
package org.kathra.appmanager.sourcerepository;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.core.model.Asset;
import org.kathra.core.model.Component;
import org.kathra.core.model.Library;
import org.kathra.core.model.SourceRepository;
import org.kathra.utils.ApiException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
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
public class SourceRepositoryServiceCreateFromLibraryTest extends SourceRepositoryServiceAbstractTest {


    Library library;

    @BeforeEach
    public void setUp() {
        Mockito.reset(sourceManager);
        Mockito.reset(resourceManager);
        Mockito.reset(libraryService);
        Mockito.reset(componentService);
        callback = Mockito.mock(Runnable.class);
        underTest = new SourceRepositoryService(resourceManager, sourceManager, componentService, libraryService, kathraSessionManager);
        underTest.setLibraryService(libraryService);
        underTest.setResourceManager(resourceManager);
        underTest.setSourceManagerClient(sourceManager);

        library = getLibrary();
        sourceRepositoryDb = getSourceRepositoryForDb();
        repositoryParentPath = getComponent().getMetadata().get("groupPath")+"/components/"+getComponent().getName()+"/"+library.getLanguage();
        repositoryPathExpected = repositoryParentPath+"/"+library.getType();
        deploysKeys = new String[]{(String) getComponent().getMetadata().get("groupId")};
    }

    protected void mockNominalBehaviorToCreateLibraryRepository() throws ApiException {
        mockCreateAndGetFromResourceManager(sourceRepositoryDb, library.getName(), repositoryPathExpected);
        mockPatchResourceManager(sourceRepositoryDb);
        mockCreateRepositoryIntoSourceManager(Arrays.asList(deploysKeys), getSourceRepositoryFromSourceManager(sourceRepositoryDb), 500);
        mockComponent();
    }

    private void mockComponent() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent())).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
    }

    @Test
    public void given_no_component_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryRepository(getLibrary().component(null), null));
        Assertions.assertEquals("Component's library is null", exception.getMessage());
    }

    @Test
    public void given_name_component_empty_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Library lib = getLibrary();
            Mockito.doReturn(Optional.of(getComponent().name(""))).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
            underTest.createLibraryRepository(lib, null);
        });
        Assertions.assertEquals("Component name is null or empty", exception.getMessage());
    }
    @Test
    public void given_groupPath_component_empty_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Library lib = getLibrary();
            Mockito.doReturn(Optional.of(getComponent().putMetadataItem("groupPath", ""))).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
            underTest.createLibraryRepository(lib, null);
        });
        Assertions.assertEquals("Component groupPath is null or empty", exception.getMessage());
    }
    @Test
    public void given_groupId_component_empty_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Library lib = getLibrary();
            Mockito.doReturn(Optional.of(getComponent().putMetadataItem("groupId", ""))).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
            underTest.createLibraryRepository(lib, null);
        });
        Assertions.assertEquals("Component groupId is null or empty", exception.getMessage());
    }

    @Test
    public void given_no_lib_type_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryRepository(getLibrary().type(null), null));
        Assertions.assertEquals("Type's library is null", exception.getMessage());
    }

    @Test
    public void given_language_when_createLibraryRepository_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.createLibraryRepository(getLibrary().language(null), null));
        Assertions.assertEquals("Programming language's library is null", exception.getMessage());
    }

    private Library getLibrary(){
        return new Library().id(UUID.randomUUID().toString())
                            .name("component-name - " + Library.LanguageEnum.PYTHON+" - " + Library.TypeEnum.MODEL)
                            .component(new Component()  .id(ComponentServiceTest.COMPONENT_ID))
                            .language(Library.LanguageEnum.PYTHON)
                            .type(Library.TypeEnum.MODEL);
    }

    private Component getComponent() {
        return new ComponentServiceTest().getComponentWithId();
    }

    @Test
    public void given_library_when_createLibraryRepository_then_return_sourceRepository_with_status_pending() throws Exception {
        ApiException exception = assertThrows(ApiException.class, () -> {
            Library library = getLibrary();
            mockComponent();
            Mockito.when(resourceManager.addSourceRepository(Mockito.any())).thenThrow(new ApiException("Unable to addSourceRepository"));
            underTest.createLibraryRepository(library, null);
        });
        Assertions.assertEquals("Unable to addSourceRepository", exception.getMessage());
    }


    @Test
    public void given_library_when_createLibraryRepository_and_wait_until_is_ready_then_sourceRepo_is_ready() throws Exception {

        mockNominalBehaviorToCreateLibraryRepository();

        SourceRepository sourceRepository = underTest.createLibraryRepository(library, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepository);

        Mockito.verify(libraryService).patch(Mockito.argThat(lib -> lib.getId().equals(library.getId()) && lib.getSourceRepository().equals(sourceRepository)));

        waitUntilSrcRepositoryIsNotPending(1000);

        Optional<SourceRepository> sourceRepositoryReady = underTest.getById(sourceRepositoryDb.getId());
        Assertions.assertTrue(sourceRepositoryReady.isPresent());
        assertSourceRepositoryReady(sourceRepositoryDb, sourceRepositoryReady.get());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_library_when_createLibraryRepository_and_dont_wait_until_is_ready_then_sourceRepo_still_pending() throws Exception {

        mockNominalBehaviorToCreateLibraryRepository();

        SourceRepository sourceRepository = underTest.createLibraryRepository(library, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepository);

        Mockito.verify(libraryService).patch(Mockito.argThat(lib -> lib.getId().equals(library.getId()) && lib.getSourceRepository().equals(sourceRepository)));

        Thread.sleep(0);

        Optional<SourceRepository> sourceRepositoryPending = underTest.getById(sourceRepositoryDb.getId());
        Assertions.assertTrue(sourceRepositoryPending.isPresent());
        assertPendingSourceRepository(sourceRepositoryDb, sourceRepositoryPending.get());
        super.callbackIsCalled(false);
    }


    @Test
    public void given_an_occurred_error_during_call_sourceManager_when_createLibraryRepository_then_sourceRepository_has_state_error() throws Exception {

        mockComponent();
        mockCreateAndGetFromResourceManager(sourceRepositoryDb, library.getName(), repositoryPathExpected);
        mockPatchResourceManager(sourceRepositoryDb);
        mockCreateRepositoryIntoSourceManagerWithError(ImmutableList.of(ComponentServiceTest.GROUP_ID), new ApiException("Unable to create repository"), 500);

        SourceRepository sourceRepository = underTest.createLibraryRepository(library, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepository);

        Mockito.verify(libraryService).patch(Mockito.argThat(lib -> lib.getId().equals(library.getId()) && lib.getSourceRepository().equals(sourceRepository)));

        waitUntilSrcRepositoryIsNotPending(15000);
        sourceRepositoryIsError(sourceRepositoryDb.getId());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_an_occurred_error_during_patch_sourceRepository_when_createLibraryRepository_then_sourceRepository_has_state_error() throws Exception {

        mockComponent();
        mockCreateAndGetFromResourceManager(sourceRepositoryDb, library.getName(), repositoryPathExpected);
        mockPatchResourceManagerWithError(new Exception("Patch error"), sourceRepositoryDb);
        mockCreateRepositoryIntoSourceManager(ImmutableList.of(ComponentServiceTest.GROUP_ID), getSourceRepositoryFromSourceManager(sourceRepositoryDb), 500);

        SourceRepository sourceRepository = underTest.createLibraryRepository(library, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepository);

        Mockito.verify(libraryService).patch(Mockito.argThat(lib -> lib.getId().equals(library.getId()) && lib.getSourceRepository().equals(sourceRepository)));

        waitUntilSrcRepositoryIsNotPending(1000);

        sourceRepositoryIsError(sourceRepositoryDb.getId());
        super.callbackIsCalled(true);
    }
}
