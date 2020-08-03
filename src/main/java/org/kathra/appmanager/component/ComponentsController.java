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
package org.kathra.appmanager.component;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.service.ComponentsService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.Component;
import javassist.NotFoundException;
import org.apache.camel.cdi.ContextName;
import org.apache.commons.lang3.StringUtils;
import org.kathra.core.model.Resource;

import javax.inject.Named;
import java.util.List;

/**
 * @author julien.boubechtoula
 */
@Named("ComponentsController")
@ContextName("AppManager")
public class ComponentsController implements ComponentsService {

    private ServiceInjection serviceInjection;
    private ComponentService componentService;
    private ApiVersionService apiVersionService;

    public ComponentsController(ComponentService componentService, ApiVersionService apiVersionService) {
        this.componentService = componentService;
        this.apiVersionService = apiVersionService;
    }

    public ComponentsController() {
        serviceInjection = new ServiceInjection(new Config(), getSessionManager());
        this.componentService = serviceInjection.getService(ComponentService.class);
        this.apiVersionService = serviceInjection.getService(ApiVersionService.class);
    }

    @Override
    public Component createComponent(Component component) throws Exception {
        if (component.getMetadata().containsKey(ComponentService.METADATA_GROUP_PATH) &&
                !StringUtils.isEmpty((CharSequence) component.getMetadata().get(ComponentService.METADATA_GROUP_PATH))) {
            final String groupPath = (String) component.getMetadata().get(ComponentService.METADATA_GROUP_PATH);
            return componentService.create(new Component().name(component.getName()).title(component.getTitle()).description(component.getDescription()), groupPath);
        } else {
            throw new IllegalArgumentException("Metadata "+ComponentService.METADATA_GROUP_PATH+" should be defined into Component");
        }

    }

    @Override
    public Component deleteComponentById(String componentId) throws Exception {
        if (StringUtils.isEmpty(componentId)) throw new IllegalArgumentException("componentId must be specified");
        // Retrieving a component
        Component component = componentService.getById(componentId).orElseThrow(() -> new NotFoundException("Component not found"));
        componentService.delete(component, true, true);
        return component.status(Resource.StatusEnum.DELETED);
    }

    @Override
    public Component getComponentById(String componentId) throws Exception {
        if (StringUtils.isEmpty(componentId)) throw new IllegalArgumentException("componentId must be specified");

        // Retrieving a component
        Component component = componentService.getById(componentId).orElseThrow(() -> new NotFoundException("Component not found"));

        // Complete the component with its apiVersions
        List<ApiVersion> componentApiVersions =  apiVersionService.getApiVersions(ImmutableList.of(component));
        component.setVersions(componentApiVersions);

        return component;
    }

    @Override
    public List<Component> getComponents() throws Exception {
        return componentService.getAllComponentsWithApiVersions();
    }
}
