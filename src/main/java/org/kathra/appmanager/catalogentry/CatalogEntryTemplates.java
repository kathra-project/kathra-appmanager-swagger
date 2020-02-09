package org.kathra.appmanager.catalogentry;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.model.CatalogEntryTemplate;
import org.kathra.appmanager.model.CatalogEntryTemplateArgument;

import java.util.List;

public class CatalogEntryTemplates {

    public List<CatalogEntryTemplate> getTemplates() {
        org.kathra.appmanager.model.CatalogEntryTemplate restApiFromImplementation =  new org.kathra.appmanager.model.CatalogEntryTemplate().name("RestApiFromImplementation")
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("NAME"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("DESCRIPTION"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("GROUP_PATH"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_ID"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_VERSION"));

        org.kathra.appmanager.model.CatalogEntryTemplate restApiFromImage =  new org.kathra.appmanager.model.CatalogEntryTemplate().name("RestApiFromDockerImage")
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("NAME"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("DESCRIPTION"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("GROUP_PATH"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMAGE_NAME"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMAGE_TAG"))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMAGE_REGISTRY"));

        return ImmutableList.of(restApiFromImplementation, restApiFromImage);
    }

    public CatalogEntryTemplate getTemplateByName(String name) {
        return getTemplates().stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

}
