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

package org.kathra.appmanager.catalogentry;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.model.CatalogEntryTemplate;
import org.kathra.appmanager.model.CatalogEntryTemplateArgument;

import java.util.List;

public class CatalogEntryTemplates {

    public List<CatalogEntryTemplate> getTemplates() {
        org.kathra.appmanager.model.CatalogEntryTemplate restApiFromImplementation =  new org.kathra.appmanager.model.CatalogEntryTemplate().name("RestApiFromImplementation")
                .label("Rest API from Implementation")
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("NAME").label("Catalog entry name"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("DESCRIPTION").label("Description owner"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("GROUP_PATH").label("Group owner"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_ID").label("Implementation"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_VERSION").label("Implementation version"));

        org.kathra.appmanager.model.CatalogEntryTemplate restApiFromImage =  new org.kathra.appmanager.model.CatalogEntryTemplate().name("RestApiFromDockerImage")
                .label("Rest API from Docker Image")
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("NAME").label("Catalog entry name"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("DESCRIPTION").label("Description"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("GROUP_PATH").label("Group owner"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMAGE_NAME").label("Docker image name"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMAGE_TAG").label("Docker image tag"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMAGE_REGISTRY").label("Docker registry host"));

        return ImmutableList.of(restApiFromImplementation, restApiFromImage);
    }

    public CatalogEntryTemplate getTemplateByName(String name) {
        return getTemplates().stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

}
