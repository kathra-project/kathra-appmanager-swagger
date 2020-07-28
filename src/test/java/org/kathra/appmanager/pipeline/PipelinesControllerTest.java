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
package org.kathra.appmanager.pipeline;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryControllerTest;
import org.kathra.core.model.Build;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.Pipeline;
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
 * Test PipelinesController
 *
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PipelinesControllerTest {

    Logger logger = LoggerFactory.getLogger(SourceRepositoryControllerTest.class);

    PipelinesController underTest;

    @Mock
    PipelineService pipelineService;
    @Mock
    ImplementationService implementationService;

    private static final String PIPELINE_ID = "pipeline-id";

    @BeforeEach
    public void setUp() throws ApiException {
        underTest = new PipelinesController(pipelineService, implementationService);
        mockExistingPipeline();
        mockExistingImplementation();
    }
    private void mockExistingPipeline() throws ApiException {
        Mockito.doReturn(Optional.of(getPipeline())).when(pipelineService).getById(Mockito.eq(PIPELINE_ID));
    }
    private void mockExistingImplementation() throws ApiException {
        Mockito.doReturn(ImmutableList.of(getImplementation())).when(implementationService).getAll();
    }

    private Pipeline getPipeline() {
        return new Pipeline().id(PIPELINE_ID);
    }
    private Implementation getImplementation() {
        return new Implementation().pipeline(getPipeline());
    }

    @Test
    public void given_pipeline_when_getBuilds_then_return_build() throws Exception {
        List<Build> builds = ImmutableList.of(new Build().buildNumber("0"), new Build().buildNumber("1"), new Build().buildNumber("2"));
        Mockito.doReturn(builds).when(pipelineService).getBuildsByBranch(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq("dev"));
        List result = underTest.getPipelineBuildsForBranch(PIPELINE_ID, "dev");
        Assertions.assertEquals(builds, result);
    }

    @Test
    public void given_no_authorized_pipeline_when_getBuilds_then_throw_exception() throws ApiException {
        Mockito.doReturn(ImmutableList.of()).when(implementationService).getAll();
        KathraException exception = assertThrows(KathraException.class, () -> underTest.getPipelineBuildsForBranch(PIPELINE_ID, "dev"));
        Assertions.assertEquals("Pipeline's implementation 'pipeline-id' forbidden", exception.getMessage());
        Assertions.assertEquals(KathraException.ErrorCode.FORBIDDEN, exception.getErrorCode());
    }

    @Test
    public void given_pipeline_when_executePipeline_then_return_build() throws Exception {
        Build build = new Build().buildNumber("0");
        Mockito.doReturn(build).when(pipelineService).build(Mockito.argThat(pipeline -> pipeline.getId().equals(PIPELINE_ID)), Mockito.eq("dev"), Mockito.isNull(), Mockito.isNull());
        Build result = underTest.executePipeline(PIPELINE_ID, "dev");
        Assertions.assertEquals(build, result);
    }


}
