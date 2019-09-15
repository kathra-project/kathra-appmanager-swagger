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
package org.kathra.appmanager.implementation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.model.ImplementationParameters;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.ImplementationsService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.ImplementationVersion;
import org.kathra.utils.KathraException;
import org.apache.camel.cdi.ContextName;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Named;
import java.util.List;
import java.util.regex.Pattern;

/**
 *
 */
@Named("ImplementationsController")
@ContextName("AppManager")
public class ImplementationsController implements ImplementationsService {

    private ServiceInjection serviceInjection;
    private final ImplementationService implementationService;
    private final ImplementationVersionService implementationVersionService;
    private final ComponentService componentService;
    private final SourceRepositoryService sourceRepositoryService;
    private final PipelineService pipelineService;
    private final ApiVersionService apiVersionService;

    public ImplementationsController() {
        serviceInjection = new ServiceInjection(new Config(), getSessionManager());
        this.apiVersionService = serviceInjection.getService(ApiVersionService.class);
        this.implementationService = serviceInjection.getService(ImplementationService.class);
        this.implementationVersionService = serviceInjection.getService(ImplementationVersionService.class);
        this.componentService = serviceInjection.getService(ComponentService.class);
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        this.pipelineService = serviceInjection.getService(PipelineService.class);
    }

    public ImplementationsController(ImplementationService implementationService, ImplementationVersionService implementationVersionService, ApiVersionService apiVersionService, ComponentService componentService, SourceRepositoryService sourceRepositoryService, PipelineService pipelineService) {
        this.implementationService = implementationService;
        this.implementationVersionService = implementationVersionService;
        this.apiVersionService = apiVersionService;
        this.componentService = componentService;
        this.sourceRepositoryService = sourceRepositoryService;
        this.pipelineService = pipelineService;
    }

    @Override
    public Implementation createImplementation(ImplementationParameters implementationParameters) throws Exception {
        Implementation.LanguageEnum lang = Implementation.LanguageEnum.fromValue(implementationParameters.getLanguage());
        if (lang == null) {
            throw new IllegalArgumentException("Language "+implementationParameters.getLanguage()+" not found");
        }
        String regex = "[0-9a-z]+";
        Pattern patternImpl = Pattern.compile(regex);
        if (StringUtils.isEmpty(implementationParameters.getName()) || !patternImpl.matcher(implementationParameters.getName()).find()) {
            throw new IllegalArgumentException("Implementation's name should respect regex pattern " + regex);
        }
        if (implementationParameters.getApiVersion() == null) {
            throw new IllegalArgumentException("ApiVersion is null");
        }
        return implementationService.create(implementationParameters.getName(), lang, implementationParameters.getApiVersion(),implementationParameters.getDesc());
    }

    @Override
    public List<Implementation> getComponentImplementations(String componentId) throws Exception {

        // gets all the necessary data with one calls by resource type
        List<Implementation> implementations = implementationService.getComponentImplementations(componentId);
        List<ImplementationVersion> implementationVersions = implementationVersionService.getImplementationVersions(implementations);
        List<ApiVersion> apiVersions =  apiVersionService.getApiVersionsForImplementationVersion(implementationVersions);

        // Complete implementationVersions with apiVersions
        implementationVersions = implementationVersionService.fillImplementationVersionWithApiVersion(implementationVersions,apiVersions);

        // Complete implementations with implementationVersions
        return implementationService.fillImplementationWithVersions(implementations, implementationVersions);
    }

    @Override
    public Implementation getImplementationById(String implementationId) throws Exception {

        Implementation implementation = implementationService.getById(implementationId).orElseThrow(() -> new KathraException("Unable to find Implementation '"+implementationId+"'", null, KathraException.ErrorCode.NOT_FOUND));
        // Retrieve versions
        List<ImplementationVersion> implementationVersions = implementationVersionService.getImplementationVersions(ImmutableList.of(implementation));

        // Retrieve details
        implementation.setComponent(componentService.getById(implementation.getComponent().getId()).get());
        if (implementation.getSourceRepository() != null && StringUtils.isNotEmpty(implementation.getSourceRepository().getId())) {
            implementation.setSourceRepository(sourceRepositoryService.getById(implementation.getSourceRepository().getId()).get());
        }
        if (implementation.getPipeline() != null && StringUtils.isNotEmpty(implementation.getPipeline().getId())) {
            implementation.setPipeline(pipelineService.getById(implementation.getPipeline().getId()).get());
        }

        return implementationService.fillImplementationWithVersions(ImmutableList.of(implementation), implementationVersions).get(0);
    }

    @Override
    public List<Implementation> getImplementations() throws Exception {

        // Only one call to retrieve components ImplementationVersions
        List<Implementation> implementations = implementationService.getAll();
        List<ImplementationVersion> implementationVersions = implementationVersionService.getImplementationVersions(implementations);

        return implementationService.fillImplementationWithVersions(implementations, implementationVersions);

    }



}