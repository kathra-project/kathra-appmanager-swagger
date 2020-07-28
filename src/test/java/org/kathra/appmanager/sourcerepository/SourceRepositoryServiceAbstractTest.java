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
import org.junit.jupiter.api.BeforeEach;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.core.model.Resource;
import org.kathra.core.model.SourceRepository;
import org.kathra.resourcemanager.client.SourceRepositoriesClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.utils.ApiException;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SourceRepositoryServiceAbstractTest extends AbstractServiceTest {

    public static final String HTTP_URL = "https://mygit.org/mon-repo.git";
    public static final String SSH_URL  = "ssh://mygit.org/mon-repo.git";
    public static final String WEB_URL  = "http://mygit.org/mon-repo/";
    public static final List<String> BRANCHES = ImmutableList.of("master", "dev");
    public static final String NAME = "repository name";
    public static final String REPOSITORY_PATH = "repository name";
    public static final String DEPLOY_KEY = ComponentServiceTest.GROUP_ID;
    public static final String[] deployKey = { DEPLOY_KEY };

    Logger logger = LoggerFactory.getLogger(SourceRepositoryServiceAbstractTest.class);

    SourceRepositoryService underTest = new SourceRepositoryService();

    @Mock
    SourceManagerClient sourceManager;
    @Mock
    LibraryService libraryService;
    @Mock
    ComponentService componentService;
    @Mock
    SourceRepositoriesClient resourceManager;

    SourceRepository sourceRepositoryDb;
    String repositoryParentPath;
    String repositoryPathExpected;
    String[] deploysKeys;

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
        sourceRepositoryDb = getSourceRepositoryForDb();
    }

    static public SourceRepository getSourceRepositoryForDb() {
        return new SourceRepository().id(UUID.randomUUID().toString())
                .name(NAME)
                .path(REPOSITORY_PATH)
                .status(Resource.StatusEnum.PENDING);
    }

    public void sourceRepositoryIsPending(String id) throws ApiException {
        Optional<SourceRepository> sourceRepositoryError = underTest.getById(id);
        Assertions.assertTrue(sourceRepositoryError.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, sourceRepositoryError.get().getStatus());
    }
    public void sourceRepositoryIsError(String id) throws ApiException {
        Optional<SourceRepository> sourceRepositoryError = underTest.getById(id);
        Assertions.assertTrue(sourceRepositoryError.isPresent());
        Assertions.assertEquals(Resource.StatusEnum.ERROR, sourceRepositoryError.get().getStatus());
    }


    public SourceRepository getSourceRepositoryFromSourceManager(SourceRepository sourceRepositoryDb) {
        return new SourceRepository().id(sourceRepositoryDb.getId())
                .path(REPOSITORY_PATH)
                .httpUrl(HTTP_URL)
                .sshUrl(SSH_URL)
                .webUrl(WEB_URL)
                .branchs(BRANCHES);
    }

    protected void assertSourceRepositoryReady(SourceRepository sourceRepositoryDb, SourceRepository sourceRepositoryReady) {
        Assertions.assertEquals(sourceRepositoryDb.getId(), sourceRepositoryReady.getId());
        Assertions.assertEquals(sourceRepositoryDb.getName(), sourceRepositoryReady.getName());
        Assertions.assertEquals(REPOSITORY_PATH, sourceRepositoryReady.getPath());
        Assertions.assertEquals(HTTP_URL, sourceRepositoryReady.getHttpUrl());
        Assertions.assertEquals(WEB_URL, sourceRepositoryReady.getWebUrl());
        Assertions.assertEquals(SSH_URL, sourceRepositoryReady.getSshUrl());
        Assertions.assertEquals(BRANCHES, sourceRepositoryReady.getBranchs());
        Assertions.assertEquals(Resource.StatusEnum.READY, sourceRepositoryReady.getStatus());
    }

    protected void assertPendingSourceRepository(SourceRepository sourceRepositoryDb, SourceRepository sourceRepositoryPending) {
        Assertions.assertNotNull(sourceRepositoryPending);
        Assertions.assertEquals(sourceRepositoryDb.getId(), sourceRepositoryPending.getId());
        Assertions.assertEquals(sourceRepositoryDb.getName(), sourceRepositoryPending.getName());
        Assertions.assertEquals(sourceRepositoryDb.getPath(), sourceRepositoryPending.getPath());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, sourceRepositoryPending.getStatus());
    }

    protected void mockCreateRepositoryIntoSourceManagerWithError(List<String> deployKey, Exception exception, int creationDuration) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(creationDuration);
            throw exception;
        }).when(sourceManager).createSourceRepository(  Mockito.argThat(src -> src.getPath().equals(REPOSITORY_PATH)),
                Mockito.eq(deployKey));
    }

    protected void mockCreateRepositoryIntoSourceManager(List<String> deployKey, SourceRepository sourceRepositoryWithUrl, int creationDuration) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Thread.sleep(creationDuration);
            return sourceRepositoryWithUrl;
        }).when(sourceManager).createSourceRepository(  Mockito.argThat(src -> src.getPath().equals(REPOSITORY_PATH)),
                                                        Mockito.eq(deployKey));
    }

    protected void mockCreateAndGetFromResourceManager(SourceRepository sourceRepositoryDb, String nameExpected, String pathExcepted) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> sourceRepositoryDb).when(resourceManager).addSourceRepository(Mockito.argThat(src -> src.getName().equals(nameExpected) && src.getPath().equals(pathExcepted)));

        Mockito.when(resourceManager.getSourceRepository(sourceRepositoryDb.getId())).thenReturn(sourceRepositoryDb);
    }

    protected void mockPatchResourceManagerWithError(Exception exception, SourceRepository sourceRepository) throws ApiException {
        Mockito.doAnswer(invocation -> {
            throw exception;
        }).when(resourceManager).updateSourceRepositoryAttributes(Mockito.eq(sourceRepository.getId()), Mockito.argThat(src -> src.getStatus().equals(Resource.StatusEnum.READY)));

    }

    protected void mockPatchResourceManager(SourceRepository sourceRepositoryDb) throws ApiException {
        Mockito.doAnswer(invocation -> {
            SourceRepository resourceToPatch = invocation.getArgument(1);
            sourceRepositoryDb.path(resourceToPatch.getPath());
            sourceRepositoryDb.httpUrl(resourceToPatch.getHttpUrl());
            sourceRepositoryDb.sshUrl(resourceToPatch.getSshUrl());
            sourceRepositoryDb.webUrl(resourceToPatch.getWebUrl());
            sourceRepositoryDb.branchs(resourceToPatch.getBranchs());
            sourceRepositoryDb.setStatus(resourceToPatch.getStatus());
            return sourceRepositoryDb;
        }).when(resourceManager).updateSourceRepositoryAttributes(Mockito.eq(sourceRepositoryDb.getId()), Mockito.argThat(src -> src.getStatus().equals(Resource.StatusEnum.READY)));
    }


    protected void waitUntilSrcRepositoryIsNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = underTest.getById(sourceRepositoryDb.getId()).get().getStatus();
        while(status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = underTest.getById(sourceRepositoryDb.getId()).get().getStatus();
        }
    }
}
