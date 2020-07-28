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
package org.kathra.appmanager.apiversion;

import org.kathra.core.model.ApiVersion;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;

import java.io.File;

/**
 * @author julien.boubechtoula
 */
public class OpenApiParser {

    private SwaggerParser parser;

    public OpenApiParser() {
        parser = new SwaggerParser();
    }

    public OpenApiParser(SwaggerParser parser) {
        this.parser = parser;
    }

    public ApiVersion getApiVersionFromApiFile(File file) {
        try {
            Swagger swagger = parser.read(file.getPath());
            return new ApiVersion() .name(swagger.getInfo().getTitle())
                                    .version(swagger.getInfo().getVersion())
                                    .putMetadataItem(ApiVersionService.METADATA_API_GROUP_ID, swagger.getInfo().getVendorExtensions().get("x-groupId"))
                                    .putMetadataItem(ApiVersionService.METADATA_API_ARTIFACT_NAME, swagger.getInfo().getVendorExtensions().get("x-artifactName"));
        } catch(Exception e){
            throw new IllegalArgumentException("ApiFile doesn't respect OpenApi specifications");
        }
    }
}
