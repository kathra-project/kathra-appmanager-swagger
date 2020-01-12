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

package org.kathra.appmanager;

import org.kathra.utils.ConfigManager;

/**
 * @author Jérémy Guillemot <Jeremy.Guillemot@kathra.org>
 */
public class Config extends ConfigManager {

    private String codegenUrl;
    private String sourceManagerUrl;
    private String pipelineManagerUrl;
    private String resourceManagerUrl;
    private String catalogManagerUrl;

    private String imageRegistryHost;
    private boolean deleteZipFile;

    public Config() {
        codegenUrl = getProperty("KATHRA_APPMANAGER_CODEGEN_URL");

        if (!codegenUrl.startsWith("http"))
            codegenUrl = "http://" + codegenUrl;

        sourceManagerUrl = getProperty("KATHRA_APPMANAGER_SOURCEMANAGER_URL");

        if (!sourceManagerUrl.startsWith("http"))
            sourceManagerUrl = "http://" + sourceManagerUrl;

        pipelineManagerUrl = getProperty("KATHRA_APPMANAGER_PIPELINEMANAGER_URL");
        if (!pipelineManagerUrl.startsWith("http"))
            pipelineManagerUrl = "http://" + pipelineManagerUrl;

        resourceManagerUrl = getProperty("KATHRA_APPMANAGER_RESOURCEMANAGER_URL");
        if (!resourceManagerUrl.startsWith("http"))
            resourceManagerUrl = "http://" + resourceManagerUrl;

        catalogManagerUrl = getProperty("KATHRA_APPMANAGER_CATALOGMANAGER_URL");
        if (!catalogManagerUrl.startsWith("http"))
            catalogManagerUrl = "http://" + catalogManagerUrl;

        imageRegistryHost = getProperty("IMAGE_REGISTRY_HOST");

        deleteZipFile = Boolean.valueOf(getProperty("KATHRA_APPMANAGER_DELETE_ZIP_FILE", "true"));
    }

    public String getCodegenUrl() {
        return this.codegenUrl;
    }

    public String getSourceManagerUrl() {
        return this.sourceManagerUrl;
    }

    public boolean isDeleteZipFile() {
        return deleteZipFile;
    }

    public String getPipelineManagerUrl() {
        return this.pipelineManagerUrl;
    }

    public String getResourceManagerUrl() {
        return resourceManagerUrl;
    }

    public String getImageRegistryHost() {
        return imageRegistryHost;
    }

    public String getCatalogManagerUrl() {return catalogManagerUrl;}
}
