package org.kathra.appmanager.catalogentrypackage;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.kathra.appmanager.binaryrepository.BinaryRepositoryService;
import org.kathra.appmanager.catalogentry.CatalogEntryService;
import org.kathra.appmanager.codegen.CodeGenProxyService;
import org.kathra.appmanager.implementation.ImplementationService;
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
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CatalogEntryPackageService extends AbstractResourceService<CatalogEntryPackage> {

    private SourceRepositoryService sourceRepositoryService;
    private BinaryRepositoryService binaryRepositoryService;
    private PipelineService pipelineService;
    private CodeGenProxyService codeGenProxyService;
    private CatalogEntryPackagesClient resourceManager;
    private ReadCatalogEntriesClient catalogManager;

    public static final String DEFAULT_BRANCH = "dev";
    public static final String FIRST_VERSION = "1.0.0";

    public CatalogEntryPackageService() {

    }

    public void configure(ServiceInjection service) {
        super.configure(service);
        this.resourceManager = new CatalogEntryPackagesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.codeGenProxyService = service.getService(CodeGenProxyService.class);
        this.pipelineService = service.getService(PipelineService.class);
        this.sourceRepositoryService = service.getService(SourceRepositoryService.class);
        this.binaryRepositoryService = service.getService(BinaryRepositoryService.class);
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
        List<CatalogEntryPackage> allEntriesFromCatalogManager = this.catalogManager.getAllCatalogEntryPackages();
        List<CatalogEntryPackage> allEntriesFromResourceManager = this.resourceManager.getCatalogEntryPackages();
        return allEntriesFromCatalogManager.parallelStream().map(entry -> enrichWithResourceManager(entry, allEntriesFromResourceManager)).collect(Collectors.toList());
    }

    private CatalogEntryPackageVersion enrichWithResourceManager(CatalogEntryPackageVersion catalogEntryPackageVersion, List<CatalogEntryPackage> entriesFromResourceManager) {
        return catalogEntryPackageVersion.catalogEntryPackage(enrichWithResourceManager(catalogEntryPackageVersion.getCatalogEntryPackage(), entriesFromResourceManager));
    }

    private CatalogEntryPackage enrichWithResourceManager(CatalogEntryPackage catalogEntry, List<CatalogEntryPackage> entriesFromResourceManager) {
        Optional<CatalogEntryPackage> fromResourceManager = entriesFromResourceManager.parallelStream().filter(entry -> entry.getProviderId().equals(catalogEntry.getProviderId())).findFirst();
        if (fromResourceManager.isEmpty()) {
            return catalogEntry;
        }
        catalogEntry.id(fromResourceManager.get().getId())
                .binaryRepository(fromResourceManager.get().getBinaryRepository())
                .packageType(fromResourceManager.get().getPackageType())
                .createdAt(fromResourceManager.get().getCreatedAt())
                .updatedAt(fromResourceManager.get().getUpdatedAt())
                .createdBy(fromResourceManager.get().getCreatedBy())
                .updatedBy(fromResourceManager.get().getUpdatedBy())
                .pipeline(fromResourceManager.get().getPipeline())
                .sourceRepository(fromResourceManager.get().getSourceRepository())
                .status(fromResourceManager.get().getStatus());
        return catalogEntry;
    }

    public CatalogEntryPackageService(CatalogEntryPackagesClient resourceManager, KathraSessionManager kathraSessionManager, SourceRepositoryService sourceRepositoryService, BinaryRepositoryService binaryRepositoryService, PipelineService pipelineService, CodeGenProxyService codeGenProxyService, ReadCatalogEntriesClient
            catalogManager) {
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
        this.sourceRepositoryService = sourceRepositoryService;
        this.binaryRepositoryService = binaryRepositoryService;
        this.pipelineService = pipelineService;
        this.codeGenProxyService = codeGenProxyService;
        this.catalogManager = catalogManager;
    }

    public CatalogEntryPackage createPackageFromImplementation(CatalogEntry catalogEntry, CatalogEntryPackage.PackageTypeEnum type, Implementation implementation, Group group, Consumer<CatalogEntryPackage> onSuccess) throws ApiException {

        Pair<CodegenClient, CodeGenTemplate> templateWithClient = getHelmTemplate(type);
        Pipeline.TemplateEnum pipelineTemplate;
        BinaryRepository binaryRepository;
        switch (type) {
            case HELM:
                pipelineTemplate = Pipeline.TemplateEnum.HELM_PACKAGE;
                binaryRepository = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.HELM).stream().findFirst().orElse(null);
                templateWithClient.getRight().arguments(addValuesForHelmSrcTemplate(implementation));
                break;
            default:
                throw new NotImplementedException("Type not implemented");
        }


        File generatedSource = templateWithClient.getKey().generateFromTemplate(templateWithClient.getValue());
        CatalogEntryPackage catalogEntryPackage = new CatalogEntryPackage().name(catalogEntry.getName())
                .packageType(type)
                .catalogEntry(catalogEntry)
                .binaryRepository(binaryRepository);
        final CatalogEntryPackage catalogEntryPackCatalogEntryPackageAdded = resourceManager.addCatalogEntryPackage(catalogEntryPackage);
        try {
            if (StringUtils.isEmpty(catalogEntryPackCatalogEntryPackageAdded.getId())) {
                throw new IllegalStateException("Component'id should be defined");
            }
        } catch (Exception e) {
            manageError(catalogEntryPackCatalogEntryPackageAdded, e);
            throw e;
        }

        initSrcRepoAndPipeline(catalogEntryPackage, generatedSource, onSuccess, pipelineTemplate, group);
        return catalogEntryPackage;
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
                Pipeline pipeline = pipelineService.create(item.getLeft().getName(), group.getPath() + "/helm-charts", item.getLeft().getSourceRepository(), pipelineTemplate, (String) item.getLeft().getMetadata().get(CatalogEntryService.METADATA_DEPLOY_KEY), afterPipelineCreated, extra);
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
                String[] deployKeys = {(String) item.getLeft().getMetadata().get(CatalogEntryService.METADATA_DEPLOY_KEY)};
                // CREATE REPOSITORY AND PUSH SOURCE WHEN IT IS READY
                SourceRepository sourceRepository = sourceRepositoryService.create(item.getLeft().getName(), group.getPath() + "/helm-charts", deployKeys, pushSource);
                item.getLeft().sourceRepository(sourceRepository);
                this.patch(new CatalogEntryPackage().id(item.getLeft().getId()).sourceRepository(sourceRepository));
            } catch (ApiException e) {
                manageError(item.getLeft(), e);
            }
        };

        createSourceRepository.accept(Pair.of(catalogEntryPackage, session));
    }

    private List<CodeGenTemplateArgument> addValuesForHelmSrcTemplate(Implementation implementation) {
        List<CodeGenTemplateArgument> template = new ArrayList<>();
        template.add(new CodeGenTemplateArgument().key("CHART_NAME").value(implementation.getName()));
        template.add(new CodeGenTemplateArgument().key("CHART_DESCRIPTION").value(implementation.getDescription()));
        template.add(new CodeGenTemplateArgument().key("CHART_VERSION").value(FIRST_VERSION));
        template.add(new CodeGenTemplateArgument().key("APP_VERSION").value(FIRST_VERSION));
        template.add(new CodeGenTemplateArgument().key("IMAGE_NAME").value(implementation.getName()));
        template.add(new CodeGenTemplateArgument().key("IMAGE_TAG").value(ImplementationVersionService.DEFAULT_BRANCH));
        template.add(new CodeGenTemplateArgument().key("IMAGE_REGISTRY").value(implementation.getBinaryRepository().getUrl()));
        return template;
    }

    private Map<String, Object> getBinaryRepositorySettingForPipeline(BinaryRepository binaryRepository) throws ApiException {
        Map<String, Object> extra = new HashMap<>();
        Credential credentialBinaryRepository = binaryRepositoryService.getCredential(binaryRepository);
        extra.put("BINARY_REPOSITORY_USERNAME", credentialBinaryRepository.getUsername());
        extra.put("BINARY_REPOSITORY_PASSWORD", credentialBinaryRepository.getPassword());
        extra.put("BINARY_REPOSITORY_URL", binaryRepository.getUrl());
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

    private Pair<CodegenClient, CodeGenTemplate> getHelmTemplate(CatalogEntryPackage.PackageTypeEnum typeEnum) throws ApiException {
        return codeGenProxyService.getAllTemplates().stream()
                .filter(i -> i.getRight().stream().anyMatch(t -> t.equals(typeEnum.getValue())))
                .map(i -> Pair.of(i.getMiddle(), i.getRight().stream().filter(t -> t.equals(typeEnum.getValue())).findFirst().get()))
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
        entryWithVersions.parallelStream().map(entry -> enrichWithResourceManager(entry, allEntriesFromResourceManager)).collect(Collectors.toList());
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
        return enrichWithResourceManager(entryVersionWithDetails, allEntriesFromResourceManager);
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
        return enrichWithResourceManager(entry.providerId(providerId), allEntriesFromResourceManager);
    }
}