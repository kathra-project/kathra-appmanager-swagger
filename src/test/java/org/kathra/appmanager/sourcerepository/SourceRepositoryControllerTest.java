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
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.model.Commit;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.SourceRepository;
import org.kathra.core.model.SourceRepositoryCommit;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test SourceRepositoryController
 *
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class SourceRepositoryControllerTest {

    Logger logger = LoggerFactory.getLogger(SourceRepositoryControllerTest.class);

    SourceRepositoriesController underTest;

    @Mock
    SourceRepositoryService sourceRepositoryService;
    @Mock
    ImplementationService implementationService;

    private static final String REPOSITORY_ID = "repository-id";

    @BeforeEach
    public void setUp() throws ApiException {
        underTest = new SourceRepositoriesController(sourceRepositoryService, implementationService);
        mockExistingRepository();
        mockExistingImplementation();
    }

    private void mockExistingRepository() throws ApiException {
        Mockito.doReturn(Optional.of(getRepository())).when(sourceRepositoryService).getById(Mockito.eq(REPOSITORY_ID));
    }
    private void mockExistingImplementation() throws ApiException {
        Mockito.doReturn(ImmutableList.of(getImplementation())).when(implementationService).getAll();
    }

    private SourceRepository getRepository() {
        return new SourceRepository().id(REPOSITORY_ID);
    }
    private Implementation getImplementation() {
        return new Implementation().sourceRepository(getRepository());
    }

    @Test
    public void when_getAll_then_return_items() throws Exception {
        List<String> branchsExisting = ImmutableList.of("master","dev");
        Mockito.doReturn(branchsExisting).when(sourceRepositoryService).getBranchs(Mockito.argThat(srcRepository -> srcRepository.getId().equals(REPOSITORY_ID)));
        List<String> branchs = underTest.getRepositoryBranches(REPOSITORY_ID);
        Assertions.assertEquals(branchsExisting, branchs);
    }

    @Test
    public void given_no_authorized_repository_when_getAll_then_throw_exception() throws ApiException {
        List<String> branchsExisting = ImmutableList.of("master","dev");
        Mockito.doReturn(branchsExisting).when(sourceRepositoryService).getBranchs(Mockito.argThat(srcRepository -> srcRepository.getId().equals(REPOSITORY_ID)));
        Mockito.doReturn(ImmutableList.of()).when(implementationService).getAll();
        KathraException exception = assertThrows(KathraException.class, () -> {
            underTest.getRepositoryBranches(REPOSITORY_ID);
        });
        Assertions.assertEquals("SourceRepository's implementation 'repository-id' forbidden", exception.getMessage());
        Assertions.assertEquals(KathraException.ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    public void when_getRepositoryCommitsForBranch_then_return_commits() throws Exception {
        ImmutableList<SourceRepositoryCommit> expected = ImmutableList.of(getCommit("123456"), getCommit("987654"));
        Mockito.doReturn(expected).when(sourceRepositoryService).getCommits(Mockito.argThat(srcRepository -> srcRepository.getId().equals(REPOSITORY_ID)), Mockito.eq("dev"));
        List<Commit> commits = underTest.getRepositoryCommitsForBranch(REPOSITORY_ID, "dev");
        Assertions.assertEquals(expected.get(0).getId(), commits.get(0).getId());
        Assertions.assertEquals(expected.get(0).getAuthorName(), commits.get(0).getAuthor());
        Assertions.assertEquals(expected.get(0).getCreatedAt(), commits.get(0).getDate());
        Assertions.assertEquals(expected.get(0).getMessage(), commits.get(0).getMessage());


        Assertions.assertEquals(expected.get(1).getId(), commits.get(1).getId());
        Assertions.assertEquals(expected.get(1).getAuthorName(), commits.get(1).getAuthor());
        Assertions.assertEquals(expected.get(1).getCreatedAt(), commits.get(1).getDate());
        Assertions.assertEquals(expected.get(1).getMessage(), commits.get(1).getMessage());
    }

    @Test
    public void given_no_authorized_repository_when_getRepositoryCommitsForBranch_then_throw_exception() throws ApiException {
        ImmutableList<SourceRepositoryCommit> expected = ImmutableList.of(getCommit("123456"), getCommit("987654"));
        Mockito.doReturn(expected).when(sourceRepositoryService).getCommits(Mockito.argThat(srcRepository -> srcRepository.getId().equals(REPOSITORY_ID)), Mockito.eq("dev"));
        Mockito.doReturn(ImmutableList.of()).when(implementationService).getAll();
        KathraException exception = assertThrows(KathraException.class, () -> {
            underTest.getRepositoryCommitsForBranch(REPOSITORY_ID, "dev");
        });
        Assertions.assertEquals("SourceRepository's implementation 'repository-id' forbidden", exception.getMessage());
        Assertions.assertEquals(KathraException.ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    private SourceRepositoryCommit getCommit(String s) {
        return new SourceRepositoryCommit().id(s).authorEmail("author"+s).message(" msg "+s).createdAt("01/03/2019"+s);
    }
}
