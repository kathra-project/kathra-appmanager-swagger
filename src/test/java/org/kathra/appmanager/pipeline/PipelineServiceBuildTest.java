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
package org.kathra.appmanager.pipeline;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.kathra.core.model.Build;
import org.kathra.core.model.Resource;
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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class PipelineServiceBuildTest extends PipelineServiceAbstractTest {

    @BeforeEach
    public void setUp() throws ApiException {
        resetMock();
        mockNominalBehaviorBuild();
    }


    @Test
    public void given_nominal_args_when_build_and_wait_until_finished_then_build_is_success() throws Exception {
        String branch = "dev";
        Build build = underTest.build(pipelineDb, branch, ImmutableMap.of("key","value"), getCallBack());
        buildScheduledAssertions(branch, build);

        waitUntilBuildIsFinished(PIPELINE_PROVIDER_ID, build, timeout);

        Assertions.assertEquals(Build.StatusEnum.SUCCESS, build.getStatus());
        callbackIsCalled(true);
    }

    @Test
    public void given_build_error_when_build_and_wait_until_finished_then_build_is_error() throws Exception {
        mockBuild(Build.StatusEnum.FAILED, 1000);

        String branch = "dev";
        Build build = underTest.build(pipelineDb, branch, ImmutableMap.of("key","value"), getCallBack());
        buildScheduledAssertions(branch, build);

        waitUntilBuildIsFinished(PIPELINE_PROVIDER_ID, build, timeout);

        Assertions.assertEquals(Build.StatusEnum.FAILED, build.getStatus());
        callbackIsCalled(true);
    }

    private void buildScheduledAssertions(String branch, Build build) {
        Assertions.assertNotNull(build);
        Assertions.assertEquals(BUILD_NUMBER, build.getBuildNumber());
        Assertions.assertEquals(Build.StatusEnum.SCHEDULED, build.getStatus());
        Assertions.assertEquals("GIT_BRANCH", build.getBuildArguments().get(0).getKey());
        Assertions.assertEquals(branch, build.getBuildArguments().get(0).getValue());
        Assertions.assertEquals("GIT_URL", build.getBuildArguments().get(1).getKey());
        Assertions.assertEquals(SRC_SSH_URL, build.getBuildArguments().get(1).getValue());
        Assertions.assertEquals("key", build.getBuildArguments().get(2).getKey());
        Assertions.assertEquals("value", build.getBuildArguments().get(2).getValue());
    }

    @Test
    public void given_too_long_build_when_build_and_wait_until_finished_then_pipeline_is_unstable() throws Exception {
        mockBuild(Build.StatusEnum.SUCCESS, 5000);
        underTest.setIntervalTimeoutMs(500);
        underTest.build(pipelineDb, "dev", ImmutableMap.of("key","value"), getCallBack());
        Thread.sleep(1000);
        Assertions.assertEquals(Resource.StatusEnum.UNSTABLE, underTest.getById(PIPELINE_ID).get().getStatus());
        callbackIsCalled(false);
    }

    private void mockBuild(Build.StatusEnum failed, long duration) throws ApiException {
        Mockito.doAnswer(invocation -> {
            Build build = invocation.getArgument(0);
            build.buildNumber(BUILD_NUMBER);
            build.status(Build.StatusEnum.SCHEDULED);
            Mockito.doReturn(build).when(pipelineManagerClient).getBuild(PIPELINE_PROVIDER_ID, BUILD_NUMBER);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(duration);
                    build.status(failed);
                    Mockito.doReturn(build).when(pipelineManagerClient).getBuild(PIPELINE_PROVIDER_ID, BUILD_NUMBER);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return build;
        }).when(pipelineManagerClient).createBuild(Mockito.any());
    }

    @Test
    public void given_exception_during_createBuild_when_build_then_throw_IllegalArgumentException() throws Exception {
        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("Exception call pipelineManager");}).when(pipelineManagerClient).createBuild(Mockito.any());
        ApiException exception = assertThrows(ApiException.class, () -> {
            underTest.build(pipelineDb, "dev", null, getCallBack());
        });
        Assertions.assertEquals("Exception call pipelineManager", exception.getMessage());
        callbackIsCalled(false);
    }

    @Test
    public void given_pipeline_without_path_when_build_then_throw_IllegalArgumentException() throws Exception {
        pipelineDb.path("");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            underTest.build(pipelineDb, "dev", null, getCallBack());
        });
        Assertions.assertEquals("Pipeline's path is null or empty", exception.getMessage());
        callbackIsCalled(false);
    }

    @Test
    public void given_pipeline_without_sourceRepository_when_build_then_throw_IllegalArgumentException() throws Exception {
        pipelineDb.sourceRepository(null);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.build(pipelineDb, "dev", null, getCallBack()));
        Assertions.assertEquals("Pipeline's SourceRepository is null", exception.getMessage());
        callbackIsCalled(false);
    }

    @Test
    public void given_sourceRepository_without_sshurl_when_build_then_throw_IllegalArgumentException() throws Exception {
        pipelineDb.getSourceRepository().sshUrl("");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.build(pipelineDb, "dev", null, getCallBack()));
        Assertions.assertEquals("SourceRepository's sshUrl is null or empty", exception.getMessage());
        callbackIsCalled(false);
    }

    @Test
    public void given_branch_when_build_then_throw_IllegalArgumentException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.build(pipelineDb, null, null, getCallBack()));
        Assertions.assertEquals("branch or tag is null or empty", exception.getMessage());
        callbackIsCalled(false);
    }

    @Test
    public void given_pipeline_and_branch_when_getBuilds_then_works() throws Exception {
        List<Build> builds = ImmutableList.of(new Build().buildNumber("0"), new Build().buildNumber("1"), new Build().buildNumber("2"));
        Mockito.doReturn(builds).when(pipelineManagerClient).getBuilds(Mockito.eq(PIPELINE_PROVIDER_ID), Mockito.eq("dev"), Mockito.isNull());
        List<Build> returns = underTest.getBuildsByBranch(pipelineDb, "dev");
        Assertions.assertEquals(builds, returns);
    }

    private void mockNominalBehaviorBuild() throws ApiException {
        pipelineDb = getPipeline();
        mockSourceRepository(pipelineDb.getSourceRepository());
        mockBuild(Build.StatusEnum.SUCCESS, 1000);
        mockPatchPipeline(PIPELINE_ID, pipelineDb);
    }


    private void waitUntilBuildIsFinished(String providerId, Build build, long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Build.StatusEnum status = pipelineManagerClient.getBuild(providerId, build.getBuildNumber()).getStatus();
        while(status.equals(Build.StatusEnum.SCHEDULED) || status.equals(Build.StatusEnum.PROCESSING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = pipelineManagerClient.getBuild(providerId, build.getBuildNumber()).getStatus();
        }
    }
}
