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

package org.kathra.appmanager.catalogentrypackage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.binaryrepository.BinaryRepositoryService;
import org.kathra.appmanager.catalogentry.CatalogEntryService;
import org.kathra.appmanager.codegen.CodeGenProxyService;
import org.kathra.appmanager.group.GroupService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.catalogmanager.client.ReadCatalogEntriesClient;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.codegen.model.CodeGenTemplate;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.CatalogEntryPackagesClient;
import org.kathra.utils.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CatalogEntryPackageService extends AbstractResourceService<CatalogEntryPackage> {

    private SourceRepositoryService sourceRepositoryService;
    private BinaryRepositoryService binaryRepositoryService;
    private GroupService groupService;
    private PipelineService pipelineService;
    private CodeGenProxyService codeGenProxyService;
    private CatalogEntryPackagesClient resourceManager;
    private ReadCatalogEntriesClient catalogManager;
    private Config config;
    private CatalogEntryService catalogEntryService;
    private CatalogPackageHelm catalogPackageHelm;
    private CatalogEntryUtils catalogEntryUtils;

    public static final String DEFAULT_BRANCH = "dev";
    public static final String FIRST_VERSION = "1.0.0";

    public static final String METADATA_DEPLOY_KEY = "deployKey";
    public static final String METADATA_GROUP_ID = "groupId";
    public static final String METADATA_GROUP_PATH = "groupPath";
    public static final String METADATA_CODEGEN_TEMPLATE = "codeGenTemplate";
    public static final String METADATA_CODEGEN_PROVIDER = "codeGenTemplate";
    public static final String METADATA_PIPELINE_TEMPLATE = "pipelineTemplate";

    public CatalogEntryPackageService() {

    }

    public CatalogEntryPackageService(CatalogEntryPackagesClient resourceManager, KathraSessionManager kathraSessionManager, GroupService groupService, SourceRepositoryService sourceRepositoryService, BinaryRepositoryService binaryRepositoryService, PipelineService pipelineService, CodeGenProxyService codeGenProxyService, ReadCatalogEntriesClient
            catalogManager, CatalogEntryService catalogEntryService, Config config) {
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
        this.sourceRepositoryService = sourceRepositoryService;
        this.binaryRepositoryService = binaryRepositoryService;
        this.pipelineService = pipelineService;
        this.codeGenProxyService = codeGenProxyService;
        this.catalogManager = catalogManager;
        this.catalogEntryService = catalogEntryService;
        this.groupService = groupService;
        this.config = config;
        this.catalogPackageHelm = new CatalogPackageHelm(config, binaryRepositoryService);
        this.catalogEntryUtils = new CatalogEntryUtils(kathraSessionManager, binaryRepositoryService, catalogEntryService, this, null);
    }

    public void configure(ServiceInjection service) {
        super.configure(service);
        this.config = service.getConfig();
        this.resourceManager = new CatalogEntryPackagesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.codeGenProxyService = service.getService(CodeGenProxyService.class);
        this.pipelineService = service.getService(PipelineService.class);
        this.sourceRepositoryService = service.getService(SourceRepositoryService.class);
        this.binaryRepositoryService = service.getService(BinaryRepositoryService.class);
        this.catalogEntryService = service.getService(CatalogEntryService.class);
        this.groupService = service.getService(GroupService.class);
        this.catalogManager = new ReadCatalogEntriesClient(service.getConfig().getCatalogManagerUrl(), service.getSessionManager());
        this.catalogPackageHelm = new CatalogPackageHelm(config, binaryRepositoryService);
        this.catalogEntryUtils = new CatalogEntryUtils(kathraSessionManager, binaryRepositoryService, catalogEntryService, this, null);
    }

    @Override
    protected void patch(CatalogEntryPackage object) throws ApiException {
        resourceManager.updateCatalogEntryPackageAttributes(object.getId(), object);
    }


    @Override
    public Optional<CatalogEntryPackage> getById(String id) throws ApiException {
        CatalogEntryPackage o = resourceManager.getCatalogEntryPackage(id);
        return (o == null) ? Optional.empty() : Optional.of(o);
    }
    public List<CatalogEntryPackage> getAllFromDb() throws ApiException {
        return this.resourceManager.getCatalogEntryPackages();
    }

    @Override
    public List<CatalogEntryPackage> getAll() throws ApiException {
        final Session session = kathraSessionManager.getCurrentSession();

        Map<String,CatalogEntry> allCatalogEntries;
        final List<CatalogEntryPackage> allEntriesPackagesFromCatalogManager;
        final List<CatalogEntryPackage> allEntriesPackagesFromResourceManager;

        try {
            ExecutorService executorService = Executors.newFixedThreadPool(3);
            Future<List<CatalogEntry>> allCatalogEntriesExec = executorService.submit(() -> this.catalogEntryService.getAllFromDb());
            Future<List<CatalogEntryPackage>> allEntriesPackagesFromCatalogManagerExec = executorService.submit(() -> this.catalogManager.getAllCatalogEntryPackages());
            Future<List<CatalogEntryPackage>> allEntriesPackagesFromResourceManagerExec = executorService.submit(() -> this.resourceManager.getCatalogEntryPackages());
            executorService.shutdown();
            executorService.awaitTermination(3, TimeUnit.SECONDS);
            allCatalogEntries = allCatalogEntriesExec.get().stream().filter(i -> i.getId() != null).collect(Collectors.toMap(CatalogEntry::getId, e -> e));
            allEntriesPackagesFromCatalogManager = allEntriesPackagesFromCatalogManagerExec.get();
            allEntriesPackagesFromResourceManager = allEntriesPackagesFromResourceManagerExec.get();
        } catch(Exception e) {
            e.printStackTrace();
            throw new ApiException(KathraApiResponse.HttpStatusCode.INTERNAL_SERVER_ERROR.getCode(), "Unable to get data from CatalogManager and/or ResourceManager");
        }
        final List<CatalogEntryPackage> allPackagesEntries;

        // MERGE DATA FROM SEVERAL SOURCES
        ConcurrentHashMap<String,BinaryRepository> binaryRepositories = new ConcurrentHashMap();
        ConcurrentHashMap<String,CatalogEntry> catalogEntries = new ConcurrentHashMap();
        allPackagesEntries = allEntriesPackagesFromCatalogManager.parallelStream().map(entry -> {
            kathraSessionManager.handleSession(session);
            return this.catalogEntryUtils.enrichWithResourceManager(entry, allEntriesPackagesFromResourceManager, binaryRepositories, catalogEntries);
        }).collect(Collectors.toList());

        List<CatalogEntryPackage> missingFromCatalogManager = allEntriesPackagesFromResourceManager .parallelStream()
                                                                                            .filter(entry -> allPackagesEntries.parallelStream()
                                                                                            .map(CatalogEntryPackage::getProviderId)
                                                                                            .noneMatch(providerId -> providerId.equals(entry.getProviderId())))
                                                                                            .collect(Collectors.toList());

        allPackagesEntries.addAll(missingFromCatalogManager);
        allPackagesEntries.parallelStream().forEach(catalogEntryPackage -> {
            if (catalogEntryPackage.getCatalogEntry() != null && catalogEntryPackage.getCatalogEntry().getId() != null) {
                CatalogEntry catalogEntry = allCatalogEntries.get(catalogEntryPackage.getCatalogEntry().getId());
                if (catalogEntry != null) {
                    catalogEntryPackage.setCatalogEntry(catalogEntry);
                }
            }
        });

        return allPackagesEntries;
    }

    public CatalogEntryPackage createPackageFromImplementation(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, Implementation implementation, Group group, Consumer<CatalogEntryPackage> callback) throws ApiException {
        Pair<CodegenClient, CodeGenTemplate> templateWithClient = getCodeGenClientAndCodeGenTemplateFromTemplate(type, "RestApiService");
        switch (type) {
            case HELM:
                BinaryRepository binaryRepository = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.HELM).stream().findFirst().orElse(null);
                BinaryRepository binaryRepositoryDocker = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.DOCKER_IMAGE).stream().findFirst().orElse(null);
                SourceRepository sourceRepository = sourceRepositoryService.getById(implementation.getSourceRepository().getId()).get();
                templateWithClient.getRight().arguments(catalogPackageHelm.getTemplateSettingsFromImplementation(catalogEntry.getName(), implementation, binaryRepository, binaryRepositoryDocker, sourceRepository, FIRST_VERSION));
                return generate(catalogEntry, type, group, callback, templateWithClient, Pipeline.TemplateEnum.HELM_PACKAGE, binaryRepository);
            default:
                throw new NotImplementedException("Type '"+type.getValue()+"' not implemented");
        }
    }

    public CatalogEntryPackage createPackageFromDockerImage(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, String imageRegistry, String imageName, String imageTag, Group group, Consumer<CatalogEntryPackage> callback) throws ApiException {
        Pair<CodegenClient, CodeGenTemplate> templateWithClient = getCodeGenClientAndCodeGenTemplateFromTemplate(type, "RestApiService");
        switch (type) {
            case HELM:
                BinaryRepository binaryRepository = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.HELM).stream().findFirst().orElse(null);
                templateWithClient.getRight().arguments(catalogPackageHelm.getTemplateSettingsFromDocker(catalogEntry, imageRegistry, imageName, imageTag, binaryRepository, FIRST_VERSION));
                return generate(catalogEntry, type, group, callback, templateWithClient, Pipeline.TemplateEnum.HELM_PACKAGE, binaryRepository);
            default:
                throw new NotImplementedException("Type '"+type.getValue()+"' not implemented");
        }
    }

    private CatalogEntryPackage generate(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, Group group, Consumer<CatalogEntryPackage> callback, Pair<CodegenClient, CodeGenTemplate> templateWithClient, Pipeline.TemplateEnum pipelineTemplate, BinaryRepository binaryRepository) throws ApiException {
        File generatedSource = templateWithClient.getKey().generateFromTemplate(templateWithClient.getValue());
        try {
            CatalogEntryPackage catalogEntryPackage = new CatalogEntryPackage().name(catalogEntry.getName() + "-" + type.getValue())
                    .putMetadataItem(METADATA_DEPLOY_KEY, group.getId())
                    .putMetadataItem(METADATA_GROUP_ID, group.getId())
                    .putMetadataItem(METADATA_GROUP_PATH, group.getPath())
                    .putMetadataItem(METADATA_CODEGEN_TEMPLATE, new ObjectMapper().writeValueAsString(templateWithClient.getValue()))
                    .putMetadataItem(METADATA_CODEGEN_PROVIDER, this.codeGenProxyService.getProviders().entrySet().stream().filter(entry -> entry.getValue().equals(templateWithClient.getRight())).map(Map.Entry::getKey))
                    .putMetadataItem(METADATA_PIPELINE_TEMPLATE, pipelineTemplate)
                    .packageType(type)
                    .catalogEntry(catalogEntry)
                    .binaryRepository(binaryRepository);

            final CatalogEntryPackage catalogEntryPackageAdded = resourceManager.addCatalogEntryPackage(catalogEntryPackage);
            try {
                if (StringUtils.isEmpty(catalogEntryPackageAdded.getId())) {
                    throw new IllegalStateException("CatalogEntry id should be defined");
                }
            } catch (Exception e) {
                manageError(catalogEntryPackageAdded, e);
                callback.accept(catalogEntryPackageAdded);
            }

            initSrcRepoAndPipeline(catalogEntryPackageAdded, generatedSource, callback, pipelineTemplate, group);
            return catalogEntryPackageAdded;
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void initSrcRepoAndPipeline(final CatalogEntryPackage catalogEntryPackage, File generatedSource, Consumer<CatalogEntryPackage> callback, Pipeline.TemplateEnum pipelineTemplate, Group group) {

        final Session session = kathraSessionManager.getCurrentSession();

        final Runnable afterPipelineCreated = () -> {
            try {
                kathraSessionManager.handleSession(session);
                build(catalogEntryPackage, DEFAULT_BRANCH, ImmutableMap.of(), (i) -> onBuildDone(catalogEntryPackage, callback));
            } catch (Exception e) {
                manageError(catalogEntryPackage, e);
            }
        };

        final Consumer<Pair<CatalogEntryPackage, Session>> createPipeline = (item) -> {
            try {
                kathraSessionManager.handleSession(item.getRight());
                initPipeline(item.getLeft(), group, pipelineTemplate, afterPipelineCreated);
            } catch (Exception e) {
                manageError(catalogEntryPackage, e);
            }
        };

        final Runnable pushSource = () -> {
            try {
                kathraSessionManager.handleSession(session);
                sourceRepositoryService.commitArchiveAndTag(catalogEntryPackage.getSourceRepository(), DEFAULT_BRANCH, generatedSource, null, FIRST_VERSION);
                // CREATE PIPELINE
                createPipeline.accept(Pair.of(catalogEntryPackage, session));
            } catch (ApiException e) {
                if (e.getCode() != KathraException.ErrorCode.NOT_MODIFIED.getCode()) {
                    manageError(catalogEntryPackage, e);
                }
            } catch (Exception e) {
                manageError(catalogEntryPackage, e);
            }
        };

        final Consumer<Pair<CatalogEntryPackage, Session>> createSourceRepository = (item) -> {
            try {
                kathraSessionManager.handleSession(item.getRight());
                // CREATE REPOSITORY AND PUSH SOURCE WHEN IT IS READY
                initSourceRepository(item.getLeft(), group, pushSource);
            } catch (Exception e) {
                manageError(catalogEntryPackage, e);
            }
        };

        createSourceRepository.accept(Pair.of(catalogEntryPackage, session));
    }

    private void initPipeline(CatalogEntryPackage catalogEntryPackage, Group group, Pipeline.TemplateEnum pipelineTemplate, Runnable callback) throws ApiException {
        Map<String, Object> extra = catalogPackageHelm.getSettingsForPipeline(catalogEntryPackage.getBinaryRepository());
        Optional<SourceRepository> sourceRepository = sourceRepositoryService.getById(catalogEntryPackage.getSourceRepository().getId());
        String deployKey = (String) catalogEntryPackage.getMetadata().get(METADATA_DEPLOY_KEY);
        String pathPipeline = group.getPath() + "/packages/" + catalogEntryPackage.getName();
        Pipeline pipeline = pipelineService.create(catalogEntryPackage.getName(), pathPipeline, sourceRepository.get(), pipelineTemplate, deployKey, callback, extra);
        catalogEntryPackage.pipeline(pipeline);
        this.patch(new CatalogEntryPackage().id(catalogEntryPackage.getId()).pipeline(pipeline));
    }

    private void initSourceRepository(CatalogEntryPackage catalogEntryPackage, Group group, Runnable callback) throws ApiException {
        String[] deployKeys = {(String) catalogEntryPackage.getMetadata().get(METADATA_DEPLOY_KEY)};
        SourceRepository sourceRepository = sourceRepositoryService.create(catalogEntryPackage.getName(), group.getPath() + "/packages/" + catalogEntryPackage.getName(), deployKeys, callback);
        catalogEntryPackage.sourceRepository(sourceRepository);
        this.patch(new CatalogEntryPackage().id(catalogEntryPackage.getId()).sourceRepository(sourceRepository));
    }

    public Build build(CatalogEntryPackage catalogEntryPackage, String branch, Map<String, String> extraArgs, Consumer<CatalogEntryPackage> onSuccess) throws ApiException {
        Pipeline pipeline = pipelineService.getById(catalogEntryPackage.getPipeline().getId()).get();
        Build build = pipelineService.build(pipeline, branch, extraArgs, () -> onBuildDone(catalogEntryPackage, onSuccess));
        patch(new CatalogEntryPackage().id(catalogEntryPackage.getId()).putMetadataItem("LATEST_BUILD_ID", build.getBuildNumber()));
        return build;
    }

    public void onBuildDone(CatalogEntryPackage catalogEntryPackage, Consumer<CatalogEntryPackage> onSuccess) {
        try {
            CatalogEntryPackage catalogEntryPackageWithDetails = resourceManager.getCatalogEntryPackage(catalogEntryPackage.getId());
            Optional<Pipeline> pipeline = pipelineService.getById(catalogEntryPackageWithDetails.getPipeline().getId());
            String buildId = (String) catalogEntryPackageWithDetails.getMetadata().get("LATEST_BUILD_ID");
            Build latestBuild = pipelineService.getBuild(pipeline.get(), buildId);
            switch(latestBuild.getStatus()) {
                case SUCCESS:
                    updateStatus(catalogEntryPackage, Resource.StatusEnum.READY);
                    break;
                case FAILED:
                    throw new IllegalStateException("Job "+buildId+" of pipeline "+pipeline.get().getPath()+ " have failed.");
            }
        } catch (Exception e) {
            super.manageError(catalogEntryPackage, e);
        } finally {
            onSuccess.accept(catalogEntryPackage);
        }
    }

    private Pair<CodegenClient, CodeGenTemplate> getCodeGenClientAndCodeGenTemplateFromTemplate(CatalogEntryPackage.PackageTypeEnum typeEnum, String templateName) throws ApiException {
        return codeGenProxyService.getAllTemplates().stream()
                .filter(i -> i.getLeft().equals(typeEnum.getValue()))
                .map(i -> Pair.of(i.getMiddle(), i.getRight().stream().filter(t -> t.getName().equals(templateName)).findFirst().get()))
                .findFirst().get();
    }

    public List<CatalogEntryPackageVersion> getVersions(CatalogEntryPackage catalogEntryPackage) throws ApiException, KathraException {
        List<CatalogEntryPackageVersion> entryWithVersions = null;
        try {
            entryWithVersions = this.catalogManager.getCatalogEntryPackageVersions(catalogEntryPackage.getProviderId());
        } catch (ApiException e) {
            e.printStackTrace();
            if (e.getCode() == KathraException.ErrorCode.NOT_FOUND.getCode()) {
                throw new KathraException("CatalogEntryPackage with providerId '" + catalogEntryPackage.getProviderId() + "' not found in manager", null, KathraException.ErrorCode.NOT_FOUND);
            }
        }

        List<CatalogEntryPackage> allEntriesFromResourceManager = this.resourceManager.getCatalogEntryPackages();
        ConcurrentHashMap<String,BinaryRepository> binaryRepositories = new ConcurrentHashMap();
        ConcurrentHashMap<String,CatalogEntry> catalogEntries = new ConcurrentHashMap();
        entryWithVersions.parallelStream().map(entry -> this.catalogEntryUtils.enrichWithResourceManager(entry, allEntriesFromResourceManager, binaryRepositories, catalogEntries)).collect(Collectors.toList());
        return entryWithVersions;
    }

    public CatalogEntryPackageVersion getVersionWithDetails(CatalogEntryPackage catalogEntryPackage, String version) throws KathraException, ApiException {
        CatalogEntryPackageVersion entryVersionWithDetails = null;
        try {
            entryVersionWithDetails = this.catalogManager.getCatalogEntryFromVersion(catalogEntryPackage.getProviderId(), version);
        } catch (ApiException e) {
            e.printStackTrace();
            if (e.getCode() == KathraException.ErrorCode.NOT_FOUND.getCode()) {
                throw new KathraException("CatalogEntryPackage with providerId '" + catalogEntryPackage.getProviderId() + "' not found in manager", null, KathraException.ErrorCode.NOT_FOUND);
            }
        }
        List<CatalogEntryPackage> allEntriesFromResourceManager = this.resourceManager.getCatalogEntryPackages();
        ConcurrentHashMap<String,BinaryRepository> binaryRepositories = new ConcurrentHashMap();
        ConcurrentHashMap<String,CatalogEntry> catalogEntries = new ConcurrentHashMap();
        return catalogEntryUtils.enrichWithResourceManager(entryVersionWithDetails, allEntriesFromResourceManager, binaryRepositories, catalogEntries);
    }

    public CatalogEntryPackage getByProviderId(String providerId) throws KathraException, ApiException {
        CatalogEntryPackage entry = null;
        try {
            entry = this.catalogManager.getCatalogEntryPackage(providerId);
        } catch (ApiException e) {
            e.printStackTrace();
            if (e.getCode() == KathraException.ErrorCode.NOT_FOUND.getCode()) {
                throw new KathraException("CatalogEntryPackage with providerId '" + providerId + "' not found in manager", null, KathraException.ErrorCode.NOT_FOUND);
            }
        }
        List<CatalogEntryPackage> allEntriesFromResourceManager = this.resourceManager.getCatalogEntryPackages();
        ConcurrentHashMap<String,BinaryRepository> binaryRepositories = new ConcurrentHashMap();
        ConcurrentHashMap<String,CatalogEntry> catalogEntries = new ConcurrentHashMap();
        return this.catalogEntryUtils.enrichWithResourceManager(entry.providerId(providerId), allEntriesFromResourceManager, binaryRepositories, catalogEntries);
    }

    public void tryToReconcile(CatalogEntryPackage packageEntryPackage) throws Exception {

        if (StringUtils.isEmpty(packageEntryPackage.getName())) {
            throw new IllegalStateException("Name null or empty");
        }
        if (packageEntryPackage.getCatalogEntry() == null) {
            throw new IllegalStateException("Catalog entry is null");
        }
        if (packageEntryPackage.getPackageType() == null) {
            throw new IllegalStateException("Package type is null");
        }
        if (packageEntryPackage.getBinaryRepository() == null) {
            throw new IllegalStateException("BinaryRepository is null");
        }
        if (isReady(packageEntryPackage) || isPending(packageEntryPackage) || isDeleted(packageEntryPackage)) {
            return;
        }

        Group group = this.groupService.getById((String) packageEntryPackage.getMetadata().get(METADATA_GROUP_ID)).orElseThrow(() -> new IllegalStateException("Group not found"));

        // CHECK REPO
        Optional<SourceRepository> sourceRepository = Optional.empty();
        if (packageEntryPackage.getSourceRepository() != null && packageEntryPackage.getSourceRepository().getId() != null) {
            sourceRepository = sourceRepositoryService.getById(packageEntryPackage.getSourceRepository().getId());
        }
        Exception inconsistency = null;
        if (sourceRepository.isEmpty()) {
            Runnable pushSource= () -> {
                try {
                    File generatedSource = this.codeGenProxyService.getProviders().get(packageEntryPackage.getMetadata().get(METADATA_CODEGEN_PROVIDER)).generateFromTemplate(new ObjectMapper().readValue((String) packageEntryPackage.getMetadata().get(METADATA_CODEGEN_TEMPLATE), CodeGenTemplate.class));
                    sourceRepositoryService.commitArchiveAndTag(packageEntryPackage.getSourceRepository(), DEFAULT_BRANCH, generatedSource, null, FIRST_VERSION);
                } catch (ApiException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            };
            initSourceRepository(packageEntryPackage, group, pushSource);
            throw new IllegalStateException("Source repository missing");
        } else if (!sourceRepositoryService.isReady(sourceRepository.get())) {
            inconsistency = new IllegalStateException("Library source repository '"+sourceRepository.get().getId()+"' not ready");
        }

        // CHECK PIPELINE
        Optional<Pipeline> pipeline = Optional.empty();
        if (packageEntryPackage.getPipeline() != null && packageEntryPackage.getPipeline().getId() != null) {
            pipeline = pipelineService.getById(packageEntryPackage.getPipeline().getId());
        }
        if (pipeline.isEmpty() && sourceRepository.isPresent()) {
            inconsistency = new IllegalStateException("Library pipeline missing");
            initPipeline(packageEntryPackage, group, Pipeline.TemplateEnum.valueOf((String) packageEntryPackage.getMetadata().get(METADATA_PIPELINE_TEMPLATE)), null);

        } else if (!pipelineService.isReady(pipeline.get())) {
            inconsistency = new IllegalStateException("Library pipeline '"+pipeline.get().getId()+"' not ready");
        }


        if (inconsistency != null) {
            throw inconsistency;
        }

        // CHECK PIPELINE BUILD
        List<Build> build = pipelineService.getBuildsByBranch(pipeline.get(), DEFAULT_BRANCH);
        Optional<Build> lastBuild = build.stream().sorted(Comparator.comparingLong(Build::getCreationDate).reversed()).findFirst();
        if (lastBuild.isPresent()) {
            switch (lastBuild.get().getStatus()) {
                case SUCCESS:
                    updateStatus(packageEntryPackage, Resource.StatusEnum.READY);
                case FAILED:
                    build(packageEntryPackage, DEFAULT_BRANCH, ImmutableMap.of(), null);
                    break;
            }
        } else {
            build(packageEntryPackage, DEFAULT_BRANCH, ImmutableMap.of(), null);
        }
    }
}