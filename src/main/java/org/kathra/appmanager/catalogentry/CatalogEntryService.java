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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    public static final String METADATA_TEMPLATE= "template";

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
            List<CatalogEntryPackage> existings = getCatalogEntryPackagesWithDetails(catalogEntry);
            for(CatalogEntryPackage.PackageTypeEnum type : CatalogEntryPackage.PackageTypeEnum.values()){
                if (existings.stream().noneMatch(catalogEntryPackage -> catalogEntryPackage.getPackageType().equals(type))) {
                    packages.add(catalogEntryPackageService.createPackageFromImplementation(catalogEntry, type, implementation, group, (catalogEntryPackage) -> onPackageIsReadyOrError(catalogEntryPackage)));
                }
            }
        } catch(Exception e) {
            manageError(catalogEntry, e);
        }
        return packages;
    }

    private List<CatalogEntryPackage> getCatalogEntryPackagesWithDetails(CatalogEntry catalogEntry) {
        return catalogEntry.getPackages().stream().map(catalogEntryPackage -> {
                    try {
                        return this.catalogEntryPackageService.getById(catalogEntryPackage.getId());
                    } catch (ApiException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).filter(catalogEntryPackage -> catalogEntryPackage != null && catalogEntryPackage.isPresent()).map(Optional::get).collect(Collectors.toList());
    }

    private List<CatalogEntryPackage> createPackagesFromDockerImage(String imageRegistry, String imageName, String imageTag, CatalogEntry catalogEntry, Group group) {
        List<CatalogEntryPackage>  packages = new ArrayList<>();
        // CREATE CATALOG ENTRY PACKAGE
        try {
            List<CatalogEntryPackage> existings = getCatalogEntryPackagesWithDetails(catalogEntry);
            for(CatalogEntryPackage.PackageTypeEnum type : CatalogEntryPackage.PackageTypeEnum.values()){
                if (existings.stream().noneMatch(catalogEntryPackage -> catalogEntryPackage.getPackageType().equals(type))) {
                    packages.add(catalogEntryPackageService.createPackageFromDockerImage(catalogEntry, type, imageRegistry, imageName, imageTag, group, (catalogEntryPackage) -> onPackageIsReadyOrError(catalogEntryPackage)));
                }
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

            final Consumer<CatalogEntry> executedPostInsertedInDb = generateFromTemplate(template, group);
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
        } catch (JsonProcessingException e) {
            throw new ApiException(e);
        }
    }

    private Consumer<CatalogEntry> generateFromTemplate(CatalogEntryTemplate template, Group group) throws ApiException {
        switch(template.getName()) {
            case "RestApiFromImplementation":
                if (StringUtils.isEmpty(getValueOrEmpty(template, "IMPLEMENTATION_ID"))) {
                    throw new IllegalArgumentException("IMPLEMENTATION_ID should be defined");
                }
                Implementation implementation = implementationService.getById(getValueOrEmpty(template, "IMPLEMENTATION_ID")).orElseThrow(() -> new IllegalArgumentException("Implementation with id '" + getValueOrEmpty(template, "IMPLEMENTATION_ID") + "' not found"));
                return (catalogEntry) -> createPackagesFromImplementation(catalogEntry, implementation, group);
            case "RestApiFromDockerImage":
                return (catalogEntry) -> createPackagesFromDockerImage(getValueOrEmpty(template, "IMAGE_REGISTRY"), getValueOrEmpty(template, "IMAGE_NAME"), getValueOrEmpty(template, "IMAGE_TAG"), catalogEntry, group);
            default:
                throw new NotImplementedException("Template '"+template.getName()+"' not implemented");
        }
    }

    private CatalogEntry createInDb(CatalogEntryTemplate template, Group group) throws ApiException, JsonProcessingException {
        // CREATE CATALOG ENTRY
        CatalogEntry catalogEntryToAdd = new CatalogEntry().name(getValueOrEmpty(template, "NAME"))
                .packageTemplate(CatalogEntry.PackageTemplateEnum.SERVICE)
                .description(getValueOrEmpty(template, "DESCRIPTION"))
                .putMetadataItem(METADATA_GROUP_ID, group.getId())
                .putMetadataItem(METADATA_GROUP_PATH, group.getPath())
                .putMetadataItem(METADATA_TEMPLATE, new ObjectMapper().writeValueAsString(template))
                .status(Resource.StatusEnum.PENDING);
        try {

            final CatalogEntry catalogEntryAdded = resourceManager.addCatalogEntry(catalogEntryToAdd);
            try {
                if (StringUtils.isEmpty(catalogEntryAdded.getId())) {
                    throw new IllegalStateException("Component's id should be defined");
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

    public void tryToReconcile(CatalogEntry entry) throws Exception {

        if (StringUtils.isEmpty(entry.getName())) {
            throw new IllegalStateException("Name null or empty");
        }
        if (entry.getPackageTemplate() == null) {
            throw new IllegalStateException("Package template is null");
        }
        if (isReady(entry) || isPending(entry) || isDeleted(entry)) {
            return;
        }

        final Group group = groupService.getById((String) entry.getMetadata().get(METADATA_GROUP_ID)).orElseThrow(() -> new IllegalArgumentException("Group not found"));
        final Consumer<CatalogEntry> executedPostInsertedInDb = generateFromTemplate(new ObjectMapper().readValue(((String) entry.getMetadata().get(METADATA_TEMPLATE)), CatalogEntryTemplate.class), group);
        executedPostInsertedInDb.accept(entry);

        CatalogEntry entryUpdated = this.getById(entry.getId()).get();
        List<CatalogEntryPackage> catalogEntryPackages = getCatalogEntryPackagesWithDetails(entryUpdated);
        for(CatalogEntryPackage catalogEntryPackage : catalogEntryPackages) {
            if (!this.catalogEntryPackageService.isReady(catalogEntryPackage)) {
                throw new Exception("CatalogEntryPackage is not ready");
            }
        }
        this.updateStatus(entry, Resource.StatusEnum.READY);
    }
}
