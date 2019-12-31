package org.kathra.appmanager.catalogentry;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.kathra.appmanager.catalogentrypackage.CatalogEntryPackageService;
import org.kathra.appmanager.group.GroupService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.model.CatalogEntryTemplate;
import org.kathra.appmanager.model.CatalogEntryTemplateArgument;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.CatalogEntriesClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;
import org.kathra.utils.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class CatalogEntryService extends AbstractResourceService<CatalogEntry> {

    private CatalogEntryPackageService catalogEntryPackageService;
    private CatalogEntriesClient resourceManager;
    private ImplementationService implementationService;
    private GroupService groupService;

    public static final String METADATA_GROUP_PATH = "groupPath";
    public static final String METADATA_GROUP_ID= "groupId";

    public static final String METADATA_DEPLOY_KEY = "deployKey";

    private static final Pattern PATTERN_NAME = Pattern.compile("^[0-9A-Za-z_\\-]+$");

    public void configure(ServiceInjection service) {
        super.configure(service);
        this.resourceManager = new CatalogEntriesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.catalogEntryPackageService = service.getService(CatalogEntryPackageService.class);
    }
    public CatalogEntryService(CatalogEntriesClient resourceManager, CatalogEntryPackageService catalogEntryPackageService, KathraSessionManager kathraSessionManager) {
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
        this.catalogEntryPackageService = catalogEntryPackageService;
    }

    @Override
    protected void patch(CatalogEntry object) throws ApiException {
        resourceManager.updateCatalogEntryAttributes(object.getId(), object);;
    }

    @Override
    public Optional<CatalogEntry> getById(String id) throws ApiException {
        CatalogEntry o = resourceManager.getCatalogEntry(id);
        return (o == null) ? Optional.empty() : Optional.of(o);
    }

    @Override
    public List<CatalogEntry> getAll() throws ApiException {
        return resourceManager.getCatalogEntries();
    }

    public CatalogEntryService(CatalogEntriesClient resourceManager) {
        this.resourceManager = resourceManager;
    }

    public List<CatalogEntryTemplate> getTemplates() {
        return ImmutableList.of(new CatalogEntryTemplate().name("RestApiFromImplementation")
                            .addArgumentsItem(new CatalogEntryTemplateArgument().key("NAME"))
                            .addArgumentsItem(new CatalogEntryTemplateArgument().key("DESCRIPTION"))
                            .addArgumentsItem(new CatalogEntryTemplateArgument().key("GROUP_PATH"))
                            .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_ID"))
                            .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_VERSION"))
        );
    }

    private void validateTemplate(CatalogEntryTemplate template) {
        CatalogEntryTemplate templateWithContraints = getTemplateByName(template.getName());
        for (CatalogEntryTemplateArgument argument :templateWithContraints.getArguments()){
            String contrainst = argument.getContrainst();
            // TODO : check contraint with template.arguments()
        }
    }

    public CatalogEntryTemplate getTemplateByName(String name) {
        return getTemplates().stream().filter(t -> t.getName().equals(name)).findFirst().orElse(null);
    }

    private Optional<String> getValueOptional(CatalogEntryTemplate template, String key) {
        return template.getArguments().stream().filter(i -> key.equals(i.getKey())).map(CatalogEntryTemplateArgument::getValue).findFirst();
    }
    private String getValueOrEmpty(CatalogEntryTemplate template, String key) {
        return getValueOptional(template, key).orElse(null);
    }

    public List<CatalogEntryPackage> createPackagesFromImplementation(CatalogEntryTemplate template, CatalogEntry catalogEntry, Implementation implementation, Group group) {
        List<CatalogEntryPackage>  packages = new ArrayList<>();
        // CREATE CATALOG ENTRY PACKAGE
        try {
            for(CatalogEntryPackage.PackageTypeEnum type : CatalogEntryPackage.PackageTypeEnum.values()){
                packages.add(catalogEntryPackageService.createPackageFromImplementation(catalogEntry, type, implementation, group, (catalogEntryPackage) -> onPackageIsReady(catalogEntryPackage)));
            }
            patch(new CatalogEntry().id(catalogEntry.getId()).packages(catalogEntry.getPackages()));
        } catch(Exception e) {
            manageError(catalogEntry, e);
        }
        return packages;
    }

    public void onPackageIsReady(CatalogEntryPackage catalogEntryPackage) {
        try {
            CatalogEntry catalogEntry = resourceManager.getCatalogEntry(catalogEntryPackage.getCatalogEntry().getId());
            processOnPendingStatus(catalogEntry);
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    private void processOnPendingStatus(CatalogEntry catalogEntryWithDetails) {
        try {
            // Check if all packages are created
            if (catalogEntryWithDetails.getPackages().size() != CatalogEntryPackage.PackageTypeEnum.values().length) {
                return;
            }
            // Check all packages are READY
            for(CatalogEntryPackage p : catalogEntryWithDetails.getPackages()) {
                Optional<CatalogEntryPackage> packageWithDetails = catalogEntryPackageService.getById(p.getId());
                if (!catalogEntryPackageService.isReady(packageWithDetails.get())) {
                    return;
                }
            }
            // CatalogEntry is READY
            updateStatus(catalogEntryWithDetails, Resource.StatusEnum.READY);
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    public Optional<CatalogEntry> getByName(String name) throws ApiException {
        return resourceManager.getCatalogEntries().stream().filter(c -> name.equals(c.getName())).findFirst();
    }

    public CatalogEntry create(CatalogEntryTemplate template) throws ApiException {
        try {
            CatalogEntryTemplate ref = getTemplateByName(template.getName());
            if (ref == null) {
                throw new IllegalArgumentException("Template not found");
            }
            if (StringUtils.isEmpty(getValueOrEmpty(template, "NAME"))) {
                throw new IllegalArgumentException("NAME should be defined\"");
            }
            if (getByName(getValueOrEmpty(template, "NAME")).isPresent()) {
                throw new IllegalArgumentException("Component already exists");
            }
            if (StringUtils.isEmpty(getValueOrEmpty(template, "GROUP_PATH"))) {
                throw new IllegalArgumentException("GROUP_PATH should be defined");
            }
            final Group group = groupService.findByPath(getValueOrEmpty(template, "GROUP_PATH")).orElseThrow(() -> new IllegalArgumentException("Group not found"));

            Consumer<CatalogEntry> executedPostInsertedInDb;
            switch(template.getName()) {
                case "RestApiFromImplementation":
                    if (StringUtils.isEmpty(getValueOrEmpty(template, "IMPLEMENTATION_ID"))) {
                        throw new IllegalArgumentException("IMPLEMENTATION_ID should be defined");
                    }
                    Implementation implementation = implementationService.getById(getValueOrEmpty(template, "IMPLEMENTATION_ID")).orElseThrow(() -> new IllegalArgumentException("Implementation with id '" + getValueOrEmpty(template, "IMPLEMENTATION_ID") + "' not found"));
                    executedPostInsertedInDb = (catalogEntry) -> createPackagesFromImplementation(template, catalogEntry, implementation, group);
                    break;
                default:
                    throw new NotImplementedException("Template '"+template.getName()+"' not implemented");
            }

            final CatalogEntry catalogEntry = createInDb(template, group);
            final Session session = kathraSessionManager.getCurrentSession();
            CompletableFuture.runAsync(() -> {
                kathraSessionManager.handleSession(session);
                executedPostInsertedInDb.accept(catalogEntry);
            });

            return catalogEntry;
        } catch (ApiException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private CatalogEntry createInDb(CatalogEntryTemplate template, Group group) throws ApiException {
        // CREATE CATALOG ENTRY
        CatalogEntry catalogEntryToAdd = new CatalogEntry().name(getValueOrEmpty(template, "NAME"))
                .packageTemplate(CatalogEntry.PackageTemplateEnum.SERVICE)
                .description(getValueOrEmpty(template, "DESCRIPTION"))
                .putMetadataItem(METADATA_GROUP_ID, group.getId())
                .putMetadataItem(METADATA_GROUP_PATH, group.getPath())
                .status(Resource.StatusEnum.PENDING);
        try {

            final CatalogEntry catalogEntryAdded = resourceManager.addCatalogEntry(catalogEntryToAdd);
            try {
                if (StringUtils.isEmpty(catalogEntryAdded.getId())) {
                    throw new IllegalStateException("Component'id should be defined");
                } else if (StringUtils.isEmpty((CharSequence) catalogEntryAdded.getMetadata().get("groupId"))) {
                    throw new IllegalStateException("Metadata '" + METADATA_GROUP_ID + "' of component should be defined");
                } else if (StringUtils.isEmpty((CharSequence) catalogEntryAdded.getMetadata().get("groupPath"))) {
                    throw new IllegalStateException("Metadata '" + METADATA_GROUP_PATH + "' of component should be defined");
                }
            } catch(Exception e) {
                manageError(catalogEntryAdded, e);
                throw e;
            }
            return catalogEntryAdded;
        } catch(Exception e) {
            manageError(catalogEntryToAdd, e);
            throw e;
        }
    }
}
