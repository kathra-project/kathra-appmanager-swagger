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
import org.kathra.core.model.SourceRepository;
import org.kathra.core.model.SourceRepositoryCommit;
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

import javax.activation.FileDataSource;
import java.io.File;
import java.util.List;

/**
 * Test SourceRepositoryService
 *
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class SourceRepositoryServiceTest extends SourceRepositoryServiceAbstractTest {

    @BeforeEach
    public void setUp() {
        Mockito.reset(sourceManager);
        Mockito.reset(resourceManager);
        Mockito.reset(libraryService);
        Mockito.reset(componentService);
        underTest = new SourceRepositoryService(resourceManager, sourceManager, componentService, libraryService, kathraSessionManager);
    }

    @Test
    public void when_getAll_then_return_items() throws Exception {

        SourceRepository item1 = new SourceRepository().name("new component 1").putMetadataItem("groupPath", "group name");
        SourceRepository item2 = new SourceRepository().name("new component 2").putMetadataItem("groupPath", "group name");
        Mockito.when(resourceManager.getSourceRepositories()).thenReturn(ImmutableList.of(item1, item2));
        List<SourceRepository> items = underTest.getAll();

        Assertions.assertEquals(items.get(0), item1);
        Assertions.assertEquals(items.get(1), item2);
    }

    @Test
    public void given_file_commit_with_tag_when_commitArchiveAndTag_then_works() throws ApiException {
        File file = getFile();
        Mockito.doReturn(new SourceRepositoryCommit().id("commit-id")).when(sourceManager).createCommit(Mockito.eq(getSourceRepositoryForDb().getPath()), Mockito.eq("dev"), Mockito.eq(file), Mockito.eq("my-file.zip"), Mockito.eq(true),Mockito.eq("1.0.2"),Mockito.eq(true));
        SourceRepositoryCommit commit = underTest.commitArchiveAndTag(getSourceRepositoryForDb(), "dev", file, "my-file.zip", "1.0.2");
        Assertions.assertNotNull(commit);
        Assertions.assertEquals("commit-id",commit.getId());
    }

    @Test
    public void given_file_commit_with_tag_when_commitFileAndTag_then_works() throws ApiException {
        File file = getFile();
        Mockito.doReturn(new SourceRepositoryCommit().id("commit-id")).when(sourceManager).createCommit(Mockito.eq(getSourceRepositoryForDb().getPath()), Mockito.eq("dev"), Mockito.eq(file), Mockito.eq("my-file.zip"), Mockito.eq(false),Mockito.eq("1.0.2"),Mockito.eq(false));
        SourceRepositoryCommit commit = underTest.commitFileAndTag(getSourceRepositoryForDb(), "dev", file, "my-file.zip", "1.0.2");
        Assertions.assertNotNull(commit);
        Assertions.assertEquals("commit-id",commit.getId());
    }

    private File getFile() {
        return Mockito.mock(File.class);
    }

    @Test
    public void given_src_repo_when_getBranchs_then_return_existing_branchs() throws ApiException {
        Mockito.doReturn(ImmutableList.of("master", "dev")).when(sourceManager).getBranches(Mockito.eq(REPOSITORY_PATH));
        List<String> branches = underTest.getBranchs(new SourceRepository().path(REPOSITORY_PATH));
        Assertions.assertEquals(ImmutableList.of("master", "dev"), branches);
    }

    @Test
    public void given_src_repo_with_branch_when_getCommits_then_return_existing_commits() throws ApiException {
        ImmutableList<SourceRepositoryCommit> expected = ImmutableList.of(getCommit("123456"), getCommit("987654"));
        Mockito.doReturn(expected).when(sourceManager).getCommits(Mockito.eq(REPOSITORY_PATH), Mockito.eq("dev"));
        List<SourceRepositoryCommit> commits = underTest.getCommits(new SourceRepository().path(REPOSITORY_PATH), "dev");
        Assertions.assertEquals(expected, commits);
    }

    private SourceRepositoryCommit getCommit(String s) {
        return new SourceRepositoryCommit().id(s).authorEmail("");
    }

    @Test
    public void given_nominal_args_when_getfile_then_works() throws ApiException{
        File mockFile = getFile();
        Mockito.when(sourceManager.getFile("A/B","dev","swagger.yaml")).thenReturn(mockFile);
        File resultFile = underTest.getFile(new SourceRepository().path("A/B"), "dev","swagger.yaml");
        Assertions.assertEquals(mockFile,resultFile);
    }

    @Test
    public void given_empty_args_when_getfile_then_throws_exception() throws ApiException{
        File mockFile = getFile();
        Mockito.when(sourceManager.getFile("A/B","dev","swagger.yaml")).thenReturn(mockFile);
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            underTest.getFile(new SourceRepository(), "dev","swagger.yaml");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            underTest.getFile(new SourceRepository().path("A/B"), "","swagger.yaml");
        });
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            underTest.getFile(new SourceRepository().path("A/B"), "dev","");
        });

    }

}
