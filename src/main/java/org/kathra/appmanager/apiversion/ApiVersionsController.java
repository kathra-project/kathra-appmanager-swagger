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
package org.kathra.appmanager.apiversion;

import org.kathra.appmanager.Config;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.service.ApiVersionsService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.Component;
import org.kathra.core.model.SourceRepository;
import org.kathra.utils.KathraException;
import javassist.NotFoundException;
import org.apache.camel.cdi.ContextName;

import javax.activation.FileDataSource;
import javax.inject.Named;
import java.io.File;
import java.security.InvalidParameterException;

/**
 * @author julien.boubechtoula
 */
@Named("ApiVersionsController")
@ContextName("AppManager")
public class ApiVersionsController implements ApiVersionsService {

    private static final String apiFilePath="swagger.yaml";
    private ServiceInjection serviceInjection;
    private final ApiVersionService apiVersionService;
    private final ComponentService componentService;
    private final SourceRepositoryService sourceRepositoryService;

    public ApiVersionsController(ApiVersionService apiVersionService, ComponentService componentService, SourceRepositoryService sourceRepositoryService) {
        this.apiVersionService = apiVersionService;
        this.componentService = componentService;
        this.sourceRepositoryService = sourceRepositoryService;
    }

    public ApiVersionsController() {
        serviceInjection = new ServiceInjection(new Config(), getSessionManager());
        this.apiVersionService = serviceInjection.getService(ApiVersionService.class);
        this.componentService = serviceInjection.getService(ComponentService.class);
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
    }

    @Override
    public ApiVersion createApiVersion(String componentId, FileDataSource openApiFile) throws Exception {
        //checkContentType(openApiFile);
        if(openApiFile == null) throw new InvalidParameterException("File source is not present");
        return apiVersionService.create(componentId, openApiFile.getFile(), null);
    }

    @Override
    public FileDataSource getApiFile(String apiVersionId) throws Exception {

        ApiVersion apiVersion = apiVersionService.getById(apiVersionId).orElseThrow(() -> new NotFoundException("ApiVersion not found"));

        Component component = componentService.getById(apiVersion.getComponent().getId()).orElseThrow(() -> new NotFoundException("Component not found"));

        SourceRepository sourceRepository =  sourceRepositoryService.getById(component.getApiRepository().getId()).orElseThrow(() -> new NotFoundException("Component not found"));

        File file;
        try {
            file = sourceRepositoryService.getFile(sourceRepository, apiVersion.getVersion(), "swagger.yaml");
        } catch(Exception e) {
            file = sourceRepositoryService.getFile(sourceRepository, apiVersion.getVersion(), "swagger.yml");
        }
        return new FileDataSource(file);
    }

    @Override
    public ApiVersion getApiVersionById(String apiVersionId) throws Exception {
        return apiVersionService.getById(apiVersionId).orElseThrow(() -> new KathraException("Unable to find Api Version '"+ apiVersionId +"'", null, KathraException.ErrorCode.NOT_FOUND));
    }

    @Override
    public ApiVersion updateApiVersion(String apiVersionId, FileDataSource openApiFile) throws Exception {
        if(openApiFile == null) throw new InvalidParameterException("File source is not present");
        ApiVersion apiVersion = apiVersionService.getById(apiVersionId).orElseThrow(() -> new NotFoundException("ApiVersion not found"));
        return apiVersionService.update(apiVersion, openApiFile.getFile(), null);
    }

    /*
    private ApiFileValidationResponse validation() {

    }

    private void checkContentType(FileDataSource apiFile) {
        ApiFileValidationResponse apiFileValidationResponse = new ApiFileValidationResponse();
        String contentType = apiFile.getContentType();
        if (contentType.equalsIgnoreCase("application/x-yaml") || contentType.equalsIgnoreCase("text/yaml") || contentType.equalsIgnoreCase("application/octet-stream"))
            apiFileValidationResponse.format(ApiFileValidationResponse.FormatEnum.YAML);
        else if (contentType.equalsIgnoreCase("application/json") || contentType.equalsIgnoreCase("text/json"))
            apiFileValidationResponse.format(ApiFileValidationResponse.FormatEnum.JSON);
        else apiFileValidationResponse.format(ApiFileValidationResponse.FormatEnum.INVALID);
        return apiFileValidationResponse;
    }
*/
}
