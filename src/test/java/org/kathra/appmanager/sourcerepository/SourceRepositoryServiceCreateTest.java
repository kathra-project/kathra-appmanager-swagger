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
import org.kathra.appmanager.component.ComponentServiceTest;
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
public class SourceRepositoryServiceCreateTest extends SourceRepositoryServiceAbstractTest {

    @BeforeEach
    public void setUp() {
        super.setUp();
    }


    @Test
    public void given_empty_path_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(NAME, "", deployKey, null));
        Assertions.assertEquals("SourceRepository's path is null or empty", exception.getMessage());
    }

    @Test
    public void given_empty_name_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create("", REPOSITORY_PATH, deployKey, null));
        Assertions.assertEquals("SourceRepository's name is null or empty", exception.getMessage());
    }

    @Test
    public void given_existing_repository_when_create_then_throws_exception() throws Exception {

        SourceRepository existingRepo = new SourceRepository().id(UUID.randomUUID().toString()).name(NAME).path(REPOSITORY_PATH);
        List<SourceRepository> existingRepositories = ImmutableList.of(existingRepo);
        Mockito.when(resourceManager.getSourceRepositories()).thenReturn(existingRepositories);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(NAME, REPOSITORY_PATH, deployKey, null));
        Assertions.assertEquals("SourceRepository with path 'repository name' already exists", exception.getMessage());
    }

    @Test
    public void given_name_groupPath_deploy_key_when_create_and_wait_until_ready_then_sourceRepository_is_ready() throws Exception {


        sourceRepositoryDb = getSourceRepositoryForDb();
        mockCreateAndGetFromResourceManager(sourceRepositoryDb, NAME, REPOSITORY_PATH);
        SourceRepository sourceRepositoryWithUrl = getSourceRepositoryFromSourceManager(sourceRepositoryDb);
        mockCreateRepositoryIntoSourceManager(ImmutableList.of(ComponentServiceTest.GROUP_ID), sourceRepositoryWithUrl, 500);
        mockPatchResourceManager(sourceRepositoryDb);

        SourceRepository sourceRepositoryPending = underTest.create(NAME, REPOSITORY_PATH, deployKey, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepositoryPending);
        waitUntilSrcRepositoryIsNotPending(1000);

        Optional<SourceRepository> sourceRepositoryReady = underTest.getById(sourceRepositoryDb.getId());
        Assertions.assertTrue(sourceRepositoryReady.isPresent());
        assertSourceRepositoryReady(sourceRepositoryDb, sourceRepositoryReady.get());
        super.callbackIsCalled(true);
    }


    @Test
    public void given_name_groupPath_deploy_key_when_create_and_dont_wait_until_ready_then_sourceRepository_is_pending() throws Exception {


        sourceRepositoryDb = getSourceRepositoryForDb();
        mockCreateAndGetFromResourceManager(sourceRepositoryDb, NAME, REPOSITORY_PATH);
        SourceRepository sourceRepositoryWithUrl = getSourceRepositoryFromSourceManager(sourceRepositoryDb);
        mockCreateRepositoryIntoSourceManager(ImmutableList.of(ComponentServiceTest.GROUP_ID), sourceRepositoryWithUrl, 500);
        mockPatchResourceManager(sourceRepositoryDb);

        SourceRepository sourceRepositoryPending = underTest.create(NAME, REPOSITORY_PATH, deployKey, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepositoryPending);
        Thread.sleep(100);

        sourceRepositoryIsPending(sourceRepositoryDb.getId());
        super.callbackIsCalled(false);
    }

    @Test
    public void given_occurred_error_during_call_create_ResourceManager_when_create_then_throws_api_exception() throws Exception {
        ApiException exception = assertThrows(ApiException.class, () -> {
            Mockito.when(resourceManager.addSourceRepository(Mockito.any())).thenThrow(new ApiException("Unable to addSourceRepository"));
            underTest.create(NAME, REPOSITORY_PATH, deployKey, null);
        });
        Assertions.assertEquals("Unable to addSourceRepository", exception.getMessage());
    }

    @Test
    public void given_occurred_error_during_call_create_SourceManager_when_create_then_return_repository_with_status_error() throws Exception {

        sourceRepositoryDb = getSourceRepositoryForDb();
        mockCreateAndGetFromResourceManager(sourceRepositoryDb, NAME, REPOSITORY_PATH);
        mockCreateRepositoryIntoSourceManagerWithError(ImmutableList.of(ComponentServiceTest.GROUP_ID), new ApiException("Unable to create repository"), 500);
        mockPatchResourceManager(sourceRepositoryDb);

        SourceRepository sourceRepositoryPending = underTest.create(NAME, REPOSITORY_PATH, deployKey, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepositoryPending);

        waitUntilSrcRepositoryIsNotPending(10000);

        sourceRepositoryIsError(sourceRepositoryDb.getId());
        super.callbackIsCalled(true);
    }

    @Test
    public void given_occurred_error_during_call_patch_ResourceManager_when_create_then_return_repository_with_status_error() throws Exception {

        sourceRepositoryDb = getSourceRepositoryForDb();

        mockCreateAndGetFromResourceManager(sourceRepositoryDb, NAME, REPOSITORY_PATH);

        SourceRepository sourceRepositoryWithUrl = getSourceRepositoryFromSourceManager(sourceRepositoryDb);

        mockCreateRepositoryIntoSourceManager(ImmutableList.of(ComponentServiceTest.GROUP_ID), sourceRepositoryWithUrl, 500);
        mockPatchResourceManagerWithError(new ApiException("Unable to patch resource"), sourceRepositoryDb);

        SourceRepository sourceRepositoryPending = underTest.create(NAME, REPOSITORY_PATH, deployKey, callback);

        assertPendingSourceRepository(sourceRepositoryDb, sourceRepositoryPending);

        waitUntilSrcRepositoryIsNotPending(1000);

        sourceRepositoryIsError(sourceRepositoryDb.getId());
        super.callbackIsCalled(true);
    }

}
