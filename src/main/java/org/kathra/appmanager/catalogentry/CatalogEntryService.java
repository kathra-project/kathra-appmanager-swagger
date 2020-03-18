package org.kathra.appmanager.catalogentry;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.kathra.appmanager.catalogentrypackage.CatalogEntryPackageService;
import org.kathra.appmanager.group.GroupService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.model.CatalogEntryTemplate;
import org.kathra.appmanager.model.CatalogEntryTemplateArgument;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.catalogmanager.client.ReadCatalogEntriesClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.CatalogEntriesClient;
import org.kathra.resourcemanager.client.CatalogEntryPackagesClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;
import org.kathra.utils.Session;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CatalogEntryService extends AbstractResourceService<CatalogEntry> {

    private CatalogEntryPackageService catalogEntryPackageService;
    private CatalogEntriesClient resourceManager;
    private ImplementationService implementationService;
    private GroupService groupService;
    private CatalogEntryTemplates catalogEntryTemplates;

    public static final String METADATA_GROUP_PATH = "groupPath";
    public static final String METADATA_GROUP_ID= "groupId";

    private ReadCatalogEntriesClient catalogManager;

    public CatalogEntryService() {

    }

    public void configure(ServiceInjection service) {
        super.configure(service);
        this.resourceManager = new CatalogEntriesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.catalogEntryPackageService = service.getService(CatalogEntryPackageService.class);
        this.groupService = service.getService(GroupService.class);
        this.implementationService = service.getService(ImplementationService.class);
        this.catalogEntryTemplates = new CatalogEntryTemplates();
        this.catalogManager = new ReadCatalogEntriesClient(service.getConfig().getCatalogManagerUrl(), service.getSessionManager());
    }
    public CatalogEntryService(CatalogEntriesClient resourceManager, CatalogEntryPackageService catalogEntryPackageService, KathraSessionManager kathraSessionManager) {
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
        this.catalogEntryPackageService = catalogEntryPackageService;
        this.catalogEntryTemplates = new CatalogEntryTemplates();
    }

    @Override
    protected void patch(CatalogEntry object) throws ApiException {
        resourceManager.updateCatalogEntryAttributes(object.getId(), object);
    }

    protected void delete(CatalogEntry object) throws ApiException {
        throw new NotImplementedException("Not implemented");
    }

    @Override
    public Optional<CatalogEntry> getById(String id) throws ApiException {
        CatalogEntry o = resourceManager.getCatalogEntry(id);
        return (o == null) ? Optional.empty() : Optional.of(o);
    }

    @Override
    public List<CatalogEntry> getAll() throws ApiException {
        List<CatalogEntry> fromDb = getAllFromDb();
        List<CatalogEntry> merged = new ArrayList<>(fromDb);
        List<CatalogEntry> missingFromDb = getAllFromManager().parallelStream().filter(e -> fromDb.parallelStream().noneMatch(e2 -> e2.getName().equals(e.getName()))).collect(Collectors.toList());
        merged.addAll(missingFromDb);
        return merged;
    }

    public List<CatalogEntry> getAllFromDb() throws ApiException {
        return resourceManager.getCatalogEntries();
    }

    private List<CatalogEntry> getAllFromManager() throws ApiException {
        return catalogManager.getAllCatalogEntryPackages().stream().map(catalogEntryPackage -> {
            CatalogEntry catalogEntry = catalogEntryPackage.getCatalogEntry();
            catalogEntry.addPackagesItem(catalogEntryPackage);
            catalogEntryPackage.setCatalogEntry(null);
            return catalogEntry;
        }).collect(Collectors.toList());
    }


    public CatalogEntryService(CatalogEntriesClient resourceManager) {
        this.resourceManager = resourceManager;
    }

    private void validateTemplate(CatalogEntryTemplate template) {
        CatalogEntryTemplate templateWithContraints = catalogEntryTemplates.getTemplateByName(template.getName());
        for (CatalogEntryTemplateArgument argument :templateWithContraints.getArguments()){
            String contrainst = argument.getContrainst();
            // TODO : check contraint with template.arguments()
        }
    }


    private Optional<String> getValueOptional(CatalogEntryTemplate template, String key) {
        return template.getArguments().stream().filter(i -> key.equals(i.getKey())).map(CatalogEntryTemplateArgument::getValue).findFirst();
    }
    private String getValueOrEmpty(CatalogEntryTemplate template, String key) {
        return getValueOptional(template, key).orElse(null);
    }

    private List<CatalogEntryPackage> createPackagesFromImplementation(CatalogEntry catalogEntry, Implementation implementation, Group group) {
        List<CatalogEntryPackage>  packages = new ArrayList<>();
        // CREATE CATALOG ENTRY PACKAGE
        try {
            for(CatalogEntryPackage.PackageTypeEnum type : CatalogEntryPackage.PackageTypeEnum.values()){
                packages.add(catalogEntryPackageService.createPackageFromImplementation(catalogEntry, type, implementation, group, (catalogEntryPackage) -> onPackageIsReadyOrError(catalogEntryPackage)));
            }
        } catch(Exception e) {
            manageError(catalogEntry, e);
        }
        return packages;
    }

    private List<CatalogEntryPackage> createPackagesFromDockerImage(String imageRegistry, String imageName, String imageTag, CatalogEntry catalogEntry, Group group) {
        List<CatalogEntryPackage>  packages = new ArrayList<>();
        // CREATE CATALOG ENTRY PACKAGE
        try {
            for(CatalogEntryPackage.PackageTypeEnum type : CatalogEntryPackage.PackageTypeEnum.values()){
                packages.add(catalogEntryPackageService.createPackageFromDockerImage(catalogEntry, type, imageRegistry, imageName, imageTag, group, (catalogEntryPackage) -> onPackageIsReadyOrError(catalogEntryPackage)));
            }
        } catch(Exception e) {
            manageError(catalogEntry, e);
        }
        return packages;
    }


    public void onPackageIsReadyOrError(CatalogEntryPackage catalogEntryPackage) {
        try {
            CatalogEntry catalogEntry = resourceManager.getCatalogEntry(catalogEntryPackage.getCatalogEntry().getId());
            processOnPendingStatus(catalogEntry);
        } catch (Exception e) {
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
                if (catalogEntryPackageService.isError(packageWithDetails.get())) {
                    throw new IllegalStateException("CatalogEntryPackage "+packageWithDetails.get().getId()+" has an error");
                } else if (!catalogEntryPackageService.isReady(packageWithDetails.get())) {
                    return;
                }
            }
            // CatalogEntry is READY
            updateStatus(catalogEntryWithDetails, Resource.StatusEnum.READY);
        } catch (Exception e) {
            super.manageError(catalogEntryWithDetails, e);
        }
    }

    public Optional<CatalogEntry> getByName(String name) throws ApiException {
        return resourceManager.getCatalogEntries().stream().filter(c -> name.equals(c.getName())).findFirst();
    }

    public CatalogEntry create(CatalogEntryTemplate template) throws ApiException {
        try {
            CatalogEntryTemplate ref = catalogEntryTemplates.getTemplateByName(template.getName());
            if (ref == null) {
                throw new IllegalArgumentException("Template not found");
            } else if (StringUtils.isEmpty(getValueOrEmpty(template, "NAME"))) {
                throw new IllegalArgumentException("NAME should be defined\"");
            } else if (getByName(getValueOrEmpty(template, "NAME")).isPresent()) {
                throw new IllegalArgumentException("CatalogEntry already exists");
            } else if (StringUtils.isEmpty(getValueOrEmpty(template, "GROUP_PATH"))) {
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
                    executedPostInsertedInDb = (catalogEntry) -> createPackagesFromImplementation(catalogEntry, implementation, group);
                    break;
                case "RestApiFromDockerImage":
                    executedPostInsertedInDb = (catalogEntry) -> createPackagesFromDockerImage(getValueOrEmpty(template, "IMAGE_REGISTRY"), getValueOrEmpty(template, "IMAGE_NAME"), getValueOrEmpty(template, "IMAGE_TAG"), catalogEntry, group);
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
