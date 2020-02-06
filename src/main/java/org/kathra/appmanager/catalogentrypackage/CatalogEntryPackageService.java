package org.kathra.appmanager.catalogentrypackage;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.binaryrepository.BinaryRepositoryService;
import org.kathra.appmanager.catalogentry.CatalogEntryService;
import org.kathra.appmanager.codegen.CodeGenProxyService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.binaryrepositorymanager.model.Credential;
import org.kathra.catalogmanager.client.ReadCatalogEntriesClient;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.codegen.model.CodeGenTemplate;
import org.kathra.codegen.model.CodeGenTemplateArgument;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.CatalogEntryPackagesClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.kathra.utils.KathraSessionManager;
import org.kathra.utils.Session;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CatalogEntryPackageService extends AbstractResourceService<CatalogEntryPackage> {

    private SourceRepositoryService sourceRepositoryService;
    private BinaryRepositoryService binaryRepositoryService;
    private PipelineService pipelineService;
    private CodeGenProxyService codeGenProxyService;
    private CatalogEntryPackagesClient resourceManager;
    private ReadCatalogEntriesClient catalogManager;
    private Config config;
    private CatalogEntryService catalogEntryService;

    public static final String DEFAULT_BRANCH = "dev";
    public static final String FIRST_VERSION = "1.0.0";

    public static final String METADATA_DEPLOY_KEY = "deployKey";
    public static final String METADATA_GROUP_ID = "groupId";
    public static final String METADATA_GROUP_PATH = "groupPath";

    public CatalogEntryPackageService() {

    }

    public CatalogEntryPackageService(CatalogEntryPackagesClient resourceManager, KathraSessionManager kathraSessionManager, SourceRepositoryService sourceRepositoryService, BinaryRepositoryService binaryRepositoryService, PipelineService pipelineService, CodeGenProxyService codeGenProxyService, ReadCatalogEntriesClient
            catalogManager, CatalogEntryService catalogEntryService, Config config) {
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
        this.sourceRepositoryService = sourceRepositoryService;
        this.binaryRepositoryService = binaryRepositoryService;
        this.pipelineService = pipelineService;
        this.codeGenProxyService = codeGenProxyService;
        this.catalogManager = catalogManager;
        this.catalogEntryService = catalogEntryService;
        this.config = config;
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
        this.catalogManager = new ReadCatalogEntriesClient(service.getConfig().getCatalogManagerUrl(), service.getSessionManager());
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

    @Override
    public List<CatalogEntryPackage> getAll() throws ApiException {
        final Session session = kathraSessionManager.getCurrentSession();
        List<CatalogEntryPackage> allEntriesFromCatalogManager = this.catalogManager.getAllCatalogEntryPackages();
        List<CatalogEntryPackage> allEntriesFromResourceManager = this.resourceManager.getCatalogEntryPackages();
        ConcurrentHashMap<String,BinaryRepository> binaryRepositories = new ConcurrentHashMap();
        ConcurrentHashMap<String,CatalogEntry> catalogEntries = new ConcurrentHashMap();
        List<CatalogEntryPackage> allEntries = allEntriesFromCatalogManager.parallelStream().map(entry -> {
            kathraSessionManager.handleSession(session);
            return enrichWithResourceManager(entry, allEntriesFromResourceManager, binaryRepositories, catalogEntries);
        }).collect(Collectors.toList());

        List<CatalogEntryPackage> missingFromCatalogManager = allEntriesFromResourceManager .parallelStream()
                                                                                            .filter(entry -> allEntries.parallelStream()
                                                                                                    .map(CatalogEntryPackage::getProviderId)
                                                                                                    .noneMatch(providerId -> providerId.equals(entry.getProviderId())))
                                                                                            .collect(Collectors.toList());

        allEntries.addAll(missingFromCatalogManager);
        return allEntries;
    }

    private CatalogEntryPackageVersion enrichWithResourceManager(CatalogEntryPackageVersion catalogEntryPackageVersion, List<CatalogEntryPackage> entriesFromResourceManager, ConcurrentHashMap<String, BinaryRepository> binaryRepositories, ConcurrentHashMap<String, CatalogEntry> catalogEntries) {
        return catalogEntryPackageVersion.catalogEntryPackage(enrichWithResourceManager(catalogEntryPackageVersion.getCatalogEntryPackage(), entriesFromResourceManager, binaryRepositories, catalogEntries));
    }

    private void tryToReconcileDbWithManager(CatalogEntryPackage catalogEntryPackageDb, CatalogEntryPackage catalogEntryPackageManager, ConcurrentHashMap<String, BinaryRepository> binaryRepositoriesCache, ConcurrentHashMap<String, CatalogEntry> catalogEntriesCache) throws ApiException {
        if (!binaryRepositoriesCache.containsKey(catalogEntryPackageDb.getBinaryRepository().getId())) {
            binaryRepositoriesCache.put(catalogEntryPackageDb.getBinaryRepository().getId(), binaryRepositoryService.getById(catalogEntryPackageDb.getBinaryRepository().getId()).get());
        }
        BinaryRepository binaryRepository = binaryRepositoriesCache.get(catalogEntryPackageDb.getBinaryRepository().getId());
        if (!catalogEntryPackageManager.getUrl().equals(binaryRepository.getUrl()))
            return;
        if (catalogEntryPackageManager.getName() != null && !catalogEntryPackageDb.getName().equals(catalogEntryPackageDb.getName()))
            return;
        if (catalogEntryPackageManager.getCatalogEntry().getName() != null) {
            CatalogEntry catalogEntryDb = catalogEntryPackageDb.getCatalogEntry();
            if (catalogEntryDb == null)
                return;
            if (!catalogEntriesCache.containsKey(catalogEntryPackageDb.getCatalogEntry().getId())) {
                catalogEntriesCache.put(catalogEntryPackageDb.getCatalogEntry().getId(), catalogEntryService.getById(catalogEntryPackageDb.getCatalogEntry().getId()).get());
            }
            catalogEntryDb = catalogEntriesCache.get(catalogEntryPackageDb.getCatalogEntry().getId());

            if (catalogEntryPackageManager.getCatalogEntry().getName().equals(catalogEntryDb.getName())) {
                catalogEntryPackageDb.setProviderId(catalogEntryPackageManager.getProviderId());
                this.patch(new CatalogEntryPackage().id(catalogEntryPackageDb.getId()).providerId(catalogEntryPackageDb.getProviderId()));
            }
        }
    }

    private CatalogEntryPackage enrichWithResourceManager(CatalogEntryPackage catalogEntryPackage, List<CatalogEntryPackage> entriesFromResourceManager, ConcurrentHashMap<String, BinaryRepository> binaryRepositoriesCache, ConcurrentHashMap<String, CatalogEntry> catalogEntriesCache) {
        if (binaryRepositoriesCache == null)
            binaryRepositoriesCache = new ConcurrentHashMap();
        if (catalogEntriesCache == null)
            catalogEntriesCache = new ConcurrentHashMap();

        final Session session = kathraSessionManager.getCurrentSession();
        ConcurrentHashMap<String, BinaryRepository> finalBinaryRepositories = binaryRepositoriesCache;
        ConcurrentHashMap<String, CatalogEntry> finalCatalogEntries = catalogEntriesCache;
        Optional<CatalogEntryPackage> fromResourceManager = entriesFromResourceManager.parallelStream()
                                .filter(Objects::nonNull)
                                .filter(entry -> {
                                    String providerId = entry.getProviderId();
                                    kathraSessionManager.handleSession(session);
                                    if (providerId == null) {
                                        try {
                                            // If providerId is null, try de reconcile identifiers between BinaryRepositoryManager and ResourceManager with Url and Name
                                            tryToReconcileDbWithManager(entry, catalogEntryPackage, finalBinaryRepositories, finalCatalogEntries);
                                            providerId = entry.getProviderId();
                                        } catch (ApiException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                    return providerId != null && providerId.equals(catalogEntryPackage.getProviderId());
                                }).findFirst();
        if (fromResourceManager.isEmpty()) {
            return catalogEntryPackage;
        }
        catalogEntryPackage.id(fromResourceManager.get().getId())
                .binaryRepository(fromResourceManager.get().getBinaryRepository())
                .packageType(fromResourceManager.get().getPackageType())
                .createdAt(fromResourceManager.get().getCreatedAt())
                .updatedAt(fromResourceManager.get().getUpdatedAt())
                .createdBy(fromResourceManager.get().getCreatedBy())
                .updatedBy(fromResourceManager.get().getUpdatedBy())
                .pipeline(fromResourceManager.get().getPipeline())
                .provider(fromResourceManager.get().getProvider())
                .providerId(fromResourceManager.get().getProviderId())
                .catalogEntry(fromResourceManager.get().getCatalogEntry())
                .sourceRepository(fromResourceManager.get().getSourceRepository())
                .status(fromResourceManager.get().getStatus());
        catalogEntryPackage.getMetadata().putAll(fromResourceManager.get().getMetadata());
        return catalogEntryPackage;
    }

    public CatalogEntryPackage createPackageFromImplementation(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, Implementation implementation, Group group, Consumer<CatalogEntryPackage> onSuccess) throws ApiException {
        Pair<CodegenClient, CodeGenTemplate> templateWithClient = getHelmTemplate(type, "RestApiService");
        switch (type) {
            case HELM:
                BinaryRepository binaryRepository = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.HELM).stream().findFirst().orElse(null);
                BinaryRepository binaryRepositoryDocker = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.DOCKER_IMAGE).stream().findFirst().orElse(null);
                templateWithClient.getRight().arguments(addValuesForHelmSrcTemplate(catalogEntry.getName(), implementation, binaryRepository, binaryRepositoryDocker));
                return generate(catalogEntry, type, group, onSuccess, templateWithClient, Pipeline.TemplateEnum.HELM_PACKAGE, binaryRepository);
            default:
                throw new NotImplementedException("Type '"+type.getValue()+"' not implemented");
        }
    }

    public CatalogEntryPackage createPackageFromDockerImage(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, String imageRegistry, String imageName, String imageTag, Group group, Consumer<CatalogEntryPackage> onSuccess) throws ApiException {
        Pair<CodegenClient, CodeGenTemplate> templateWithClient = getHelmTemplate(type, "RestApiService");
        switch (type) {
            case HELM:
                BinaryRepository binaryRepository = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.HELM).stream().findFirst().orElse(null);
                List<CodeGenTemplateArgument> template = new ArrayList<>();
                template.add(new CodeGenTemplateArgument().key("CHART_NAME").value(catalogEntry.getName()));
                template.add(new CodeGenTemplateArgument().key("CHART_DESCRIPTION").value(catalogEntry.getDescription()));
                template.add(new CodeGenTemplateArgument().key("CHART_VERSION").value(FIRST_VERSION));
                template.add(new CodeGenTemplateArgument().key("APP_VERSION").value(FIRST_VERSION));
                template.add(new CodeGenTemplateArgument().key("IMAGE_NAME").value(imageName));
                template.add(new CodeGenTemplateArgument().key("IMAGE_TAG").value(imageTag));
                template.add(new CodeGenTemplateArgument().key("IMAGE_REGISTRY").value(imageRegistry));
                template.add(new CodeGenTemplateArgument().key("REGISTRY_HOST").value(binaryRepository.getUrl()));
                templateWithClient.getRight().arguments(template);
                return generate(catalogEntry, type, group, onSuccess, templateWithClient, Pipeline.TemplateEnum.HELM_PACKAGE, binaryRepository);
            default:
                throw new NotImplementedException("Type '"+type.getValue()+"' not implemented");
        }
    }

    private CatalogEntryPackage generate(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, Group group, Consumer<CatalogEntryPackage> onSuccess, Pair<CodegenClient, CodeGenTemplate> templateWithClient, Pipeline.TemplateEnum pipelineTemplate, BinaryRepository binaryRepository) throws ApiException {
        File generatedSource = templateWithClient.getKey().generateFromTemplate(templateWithClient.getValue());
        CatalogEntryPackage catalogEntryPackage = new CatalogEntryPackage().name(catalogEntry.getName() + "-" + type.getValue())
                .putMetadataItem(METADATA_DEPLOY_KEY, group.getId())
                .putMetadataItem(METADATA_GROUP_ID, group.getId())
                .putMetadataItem(METADATA_GROUP_PATH, group.getPath())
                .packageType(type)
                .catalogEntry(catalogEntry)
                .binaryRepository(binaryRepository);
        final CatalogEntryPackage catalogEntryPackCatalogEntryPackageAdded = resourceManager.addCatalogEntryPackage(catalogEntryPackage);
        try {
            if (StringUtils.isEmpty(catalogEntryPackCatalogEntryPackageAdded.getId())) {
                throw new IllegalStateException("CatalogEntry id should be defined");
            }
        } catch (Exception e) {
            manageError(catalogEntryPackCatalogEntryPackageAdded, e);
            throw e;
        }

        initSrcRepoAndPipeline(catalogEntryPackCatalogEntryPackageAdded, generatedSource, onSuccess, pipelineTemplate, group);
        return catalogEntryPackCatalogEntryPackageAdded;
    }

    private void initSrcRepoAndPipeline(final CatalogEntryPackage catalogEntryPackage, File generatedSource, Consumer<CatalogEntryPackage> onSuccess, Pipeline.TemplateEnum pipelineTemplate, Group group) {
        final Session session = kathraSessionManager.getCurrentSession();
        final Runnable afterPipelineCreated = () -> {
            try {
                kathraSessionManager.handleSession(session);
                pipelineService.build(catalogEntryPackage.getPipeline(), DEFAULT_BRANCH, ImmutableMap.of(), () -> onBuildSuccess(catalogEntryPackage, onSuccess));
            } catch (ApiException e) {
                manageError(catalogEntryPackage, e);
            }
        };

        final Consumer<Pair<CatalogEntryPackage, Session>> createPipeline = (item) -> {
            try {
                kathraSessionManager.handleSession(item.getRight());
                Map<String, Object> extra = getBinaryRepositorySettingForPipeline(catalogEntryPackage.getBinaryRepository());
                Optional<SourceRepository> sourceRepository = sourceRepositoryService.getById(item.getLeft().getSourceRepository().getId());
                String deployKey = (String) item.getLeft().getMetadata().get(METADATA_DEPLOY_KEY);
                String pathPipeline = group.getPath() + "/components/packages/" + item.getLeft().getName();
                Pipeline pipeline = pipelineService.create(item.getLeft().getName(), pathPipeline, sourceRepository.get(), pipelineTemplate, deployKey, afterPipelineCreated, extra);
                item.getLeft().pipeline(pipeline);
                this.patch(new CatalogEntryPackage().id(item.getLeft().getId()).pipeline(pipeline));
            } catch (ApiException e) {
                manageError(item.getLeft(), e);
            }
        };

        final Runnable pushSource = () -> {
            try {
                kathraSessionManager.handleSession(session);
                sourceRepositoryService.commitArchiveAndTag(catalogEntryPackage.getSourceRepository(), DEFAULT_BRANCH, generatedSource, null, FIRST_VERSION);
                // CREATE PIPELINE
                createPipeline.accept(Pair.of(catalogEntryPackage, session));
            } catch (ApiException e) {
                manageError(catalogEntryPackage, e);
            }
        };

        final Consumer<Pair<CatalogEntryPackage, Session>> createSourceRepository = (item) -> {
            try {
                kathraSessionManager.handleSession(item.getRight());
                String[] deployKeys = {(String) item.getLeft().getMetadata().get(METADATA_DEPLOY_KEY)};
                // CREATE REPOSITORY AND PUSH SOURCE WHEN IT IS READY
                SourceRepository sourceRepository = sourceRepositoryService.create(item.getLeft().getName(), group.getPath() + "/packages/" + item.getLeft().getName(), deployKeys, pushSource);
                item.getLeft().sourceRepository(sourceRepository);
                this.patch(new CatalogEntryPackage().id(item.getLeft().getId()).sourceRepository(sourceRepository));
            } catch (ApiException e) {
                manageError(item.getLeft(), e);
            }
        };

        createSourceRepository.accept(Pair.of(catalogEntryPackage, session));
    }

    private List<CodeGenTemplateArgument> addValuesForHelmSrcTemplate(String catalogEntryName, Implementation implementation, BinaryRepository binaryRepositoryHelm, BinaryRepository binaryRepositoryApp) {
        List<CodeGenTemplateArgument> template = new ArrayList<>();
        template.add(new CodeGenTemplateArgument().key("CHART_NAME").value(catalogEntryName));
        template.add(new CodeGenTemplateArgument().key("CHART_DESCRIPTION").value(implementation.getDescription()));
        template.add(new CodeGenTemplateArgument().key("CHART_VERSION").value(FIRST_VERSION));
        template.add(new CodeGenTemplateArgument().key("APP_VERSION").value(FIRST_VERSION));
        template.add(new CodeGenTemplateArgument().key("IMAGE_NAME").value(implementation.getName().toLowerCase()));
        template.add(new CodeGenTemplateArgument().key("IMAGE_TAG").value(ImplementationVersionService.DEFAULT_BRANCH));
        template.add(new CodeGenTemplateArgument().key("IMAGE_REGISTRY").value(binaryRepositoryApp.getUrl()));
        template.add(new CodeGenTemplateArgument().key("REGISTRY_HOST").value(binaryRepositoryHelm.getUrl()));
        return template;
    }

    private Map<String, Object> getBinaryRepositorySettingForPipeline(BinaryRepository binaryRepository) throws ApiException {
        Map<String, Object> extra = new HashMap<>();
        Optional<BinaryRepository> binaryRepositoryWithDetails = binaryRepositoryService.getById(binaryRepository.getId());
        Credential credentialBinaryRepository = binaryRepositoryService.getCredential(binaryRepositoryWithDetails.get());
        extra.put("BINARY_REPOSITORY_USERNAME", credentialBinaryRepository.getUsername());
        extra.put("BINARY_REPOSITORY_PASSWORD", credentialBinaryRepository.getPassword());
        extra.put("BINARY_REPOSITORY_URL", binaryRepositoryWithDetails.get().getUrl());
        extra.put("KATHRA_WEBHOOK_URL", this.config.getWebHookPipelineUrl());
        return extra;
    }

    public Build build(CatalogEntryPackage catalogEntryPackage, String branch, Map<String, String> extraArgs, Consumer<CatalogEntryPackage> onSuccess) throws ApiException {
        Build build = pipelineService.build(catalogEntryPackage.getPipeline(), DEFAULT_BRANCH, ImmutableMap.of(), () -> onBuildSuccess(catalogEntryPackage, onSuccess));
        catalogEntryPackage.getMetadata().put("LATEST_BUILD_ID", build.getBuildNumber());
        return build;
    }

    public void onBuildSuccess(CatalogEntryPackage catalogEntryPackage, Consumer<CatalogEntryPackage> onSuccess) {
        updateStatus(catalogEntryPackage, Resource.StatusEnum.READY);
    }

    private Pair<CodegenClient, CodeGenTemplate> getHelmTemplate(CatalogEntryPackage.PackageTypeEnum typeEnum, String templateName) throws ApiException {
        return codeGenProxyService.getAllTemplates().stream()
                .filter(i -> i.getLeft().equals(typeEnum.getValue()))
                .map(i -> Pair.of(i.getMiddle(),
                        i.getRight().stream().filter(t -> t.getName().equals(templateName)).findFirst().get()))
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
        entryWithVersions.parallelStream().map(entry -> enrichWithResourceManager(entry, allEntriesFromResourceManager, binaryRepositories, catalogEntries)).collect(Collectors.toList());
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
        return enrichWithResourceManager(entryVersionWithDetails, allEntriesFromResourceManager, binaryRepositories, catalogEntries);
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
        return enrichWithResourceManager(entry.providerId(providerId), allEntriesFromResourceManager, binaryRepositories, catalogEntries);
    }
}