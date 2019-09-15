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

import org.kathra.appmanager.Config;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.service.PipelinesService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.Build;
import org.kathra.core.model.Pipeline;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.apache.camel.cdi.ContextName;

import javax.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author julien.boubechtoula
 */
@Named("PipelinesController")
@ContextName("AppManager")
public class PipelinesController implements PipelinesService {

    private ServiceInjection serviceInjection;
    private ImplementationService implementationService;
    private PipelineService pipelineService;

    public PipelinesController(PipelineService pipelineService, ImplementationService implementationService) {
        this.pipelineService = pipelineService;
        this.implementationService = implementationService;
    }

    public PipelinesController() {
        serviceInjection = new ServiceInjection(new Config(), getSessionManager());
        this.pipelineService = serviceInjection.getService(PipelineService.class);
        this.implementationService = serviceInjection.getService(ImplementationService.class);

    }

    @Override
    public Build executePipeline(String pipelineId, String branch) throws Exception {
        Pipeline implementationPipeline = getPipeline(pipelineId);
        return pipelineService.build(implementationPipeline, branch, null, null);
    }

    @Override
    public List<Build> getPipelineBuildsForBranch(String pipelineId, String branch) throws Exception {
        return pipelineService.getBuildsByBranch(getPipeline(pipelineId), branch).stream().map(build -> new Build().buildNumber(build.getBuildNumber()).status(build.getStatus()).creationDate(build.getCreationDate()).duration(build.getDuration())).collect(Collectors.toList());
    }

    private Pipeline getPipeline(String pipelineId) throws ApiException, KathraException {
        Pipeline pipeline = pipelineService.getById(pipelineId).orElseThrow(() -> new KathraException("Pipeline "+pipelineId+" not found", null,KathraException.ErrorCode.NOT_FOUND));
        boolean pipelineImplementationAuthorized = implementationService.getAll().stream().anyMatch(implementation -> implementation.getPipeline() != null && implementation.getPipeline().getId() != null && implementation.getPipeline().getId().equals(pipelineId));
        if (!pipelineImplementationAuthorized) {
            throw new KathraException("Pipeline's implementation '"+pipelineId+"' forbidden", null,KathraException.ErrorCode.FORBIDDEN);
        }
        return pipeline;
    }
}
