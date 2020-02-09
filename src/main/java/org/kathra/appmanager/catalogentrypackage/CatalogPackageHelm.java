package org.kathra.appmanager.catalogentrypackage;

import org.kathra.appmanager.Config;
import org.kathra.appmanager.binaryrepository.BinaryRepositoryService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.binaryrepositorymanager.model.Credential;
import org.kathra.codegen.model.CodeGenTemplateArgument;
import org.kathra.core.model.BinaryRepository;
import org.kathra.core.model.CatalogEntry;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.SourceRepository;
import org.kathra.utils.ApiException;

import java.util.*;

public class CatalogPackageHelm {

    private final Config config;
    private final BinaryRepositoryService binaryRepositoryService;

    public CatalogPackageHelm(Config config, BinaryRepositoryService binaryRepositoryService) {
        this.binaryRepositoryService = binaryRepositoryService;
        this.config = config;
    }

    public List<CodeGenTemplateArgument> getTemplateSettingsFromImplementation(String catalogEntryName, Implementation implementation, BinaryRepository binaryRepositoryHelm, BinaryRepository binaryRepositoryApp, SourceRepository sourceRepository, String version) {
        List<CodeGenTemplateArgument> template = new ArrayList<>();
        template.add(new CodeGenTemplateArgument().key("CHART_NAME").value(catalogEntryName));
        template.add(new CodeGenTemplateArgument().key("CHART_DESCRIPTION").value(implementation.getDescription()));
        template.add(new CodeGenTemplateArgument().key("CHART_VERSION").value(version));
        template.add(new CodeGenTemplateArgument().key("APP_VERSION").value(version));
        template.add(new CodeGenTemplateArgument().key("IMAGE_NAME").value(implementation.getName().toLowerCase()));
        template.add(new CodeGenTemplateArgument().key("IMAGE_TAG").value(ImplementationVersionService.DEFAULT_BRANCH));
        template.add(new CodeGenTemplateArgument().key("SOURCE_URL").value(sourceRepository.getHttpUrl()));
        template.add(new CodeGenTemplateArgument().key("ICON_URL").value(""));
        template.add(new CodeGenTemplateArgument().key("HOME_URL").value(sourceRepository.getHttpUrl()));
        template.add(new CodeGenTemplateArgument().key("IMAGE_REGISTRY").value(binaryRepositoryApp.getUrl()));
        template.add(new CodeGenTemplateArgument().key("REGISTRY_HOST").value(binaryRepositoryHelm.getUrl()));
        return template;
    }


    public List<CodeGenTemplateArgument> getTemplateSettingsFromDocker(CatalogEntry catalogEntry, String imageRegistry, String imageName, String imageTag, BinaryRepository binaryRepository, String version) {
        List<CodeGenTemplateArgument> template = new ArrayList<>();
        template.add(new CodeGenTemplateArgument().key("CHART_NAME").value(catalogEntry.getName()));
        template.add(new CodeGenTemplateArgument().key("CHART_DESCRIPTION").value(catalogEntry.getDescription()));
        template.add(new CodeGenTemplateArgument().key("CHART_VERSION").value(version));
        template.add(new CodeGenTemplateArgument().key("APP_VERSION").value(version));
        template.add(new CodeGenTemplateArgument().key("IMAGE_NAME").value(imageName));
        template.add(new CodeGenTemplateArgument().key("IMAGE_TAG").value(imageTag));
        template.add(new CodeGenTemplateArgument().key("IMAGE_REGISTRY").value(imageRegistry));
        template.add(new CodeGenTemplateArgument().key("REGISTRY_HOST").value(binaryRepository.getUrl()));
        template.add(new CodeGenTemplateArgument().key("SOURCE_URL").value(""));
        template.add(new CodeGenTemplateArgument().key("ICON_URL").value(""));
        template.add(new CodeGenTemplateArgument().key("HOME_URL").value(""));
        return template;
    }

    public Map<String, Object> getSettingsForPipeline(BinaryRepository binaryRepository) throws ApiException {
        Map<String, Object> extra = new HashMap<>();
        Optional<BinaryRepository> binaryRepositoryWithDetails = binaryRepositoryService.getById(binaryRepository.getId());
        Credential credentialBinaryRepository = binaryRepositoryService.getCredential(binaryRepositoryWithDetails.get());
        extra.put("BINARY_REPOSITORY_USERNAME", credentialBinaryRepository.getUsername());
        extra.put("BINARY_REPOSITORY_PASSWORD", credentialBinaryRepository.getPassword());
        extra.put("BINARY_REPOSITORY_URL", binaryRepositoryWithDetails.get().getUrl());
        extra.put("KATHRA_WEBHOOK_URL", this.config.getWebHookPipelineUrl());
        return extra;
    }

}
