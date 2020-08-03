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
package org.kathra.appmanager.implementation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.binaryrepository.BinaryRepositoryService;
import org.kathra.appmanager.catalogentry.CatalogEntryService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.group.GroupService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.model.CatalogEntryTemplate;
import org.kathra.appmanager.model.CatalogEntryTemplateArgument;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.Session;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author pierredadt@kathra.org
 */
public class ImplementationService extends AbstractResourceService<Implementation> {

    private Logger logger = LoggerFactory.getLogger(ImplementationService.class);

    public static final String METADATA_PATH = "path";
    public static final String METADATA_DEPLOY_KEY = "deployKey";
    public static final String METADATA_GROUP_ID = "groupId";
    public static final String METADATA_GROUP_PATH = "groupPath";
    public static final String METADATA_ARTIFACT_GROUP_ID = "artifact-groupId";
    public static final String METADATA_ARTIFACT_NAME = "artifact-artifactName";
    private static final Pattern PATTERN_NAME = Pattern.compile("^[0-9A-Za-z_\\-]+$");

    private static final String FIRST_VERSION = "1.0.0";

    // Clients and Services
    private ApiVersionService apiVersionService;
    private ComponentService componentService;
    private SourceRepositoryService sourceRepositoryService;
    private BinaryRepositoryService binaryRepositoryService;
    private PipelineService pipelineService;
    private ImplementationsClient resourceManager;
    private ImplementationVersionService implementationVersionService;

    private GroupService groupService;
    private CatalogEntryService catalogEntryService;
    private String imageRegistryHost;

    public ImplementationService() {

    }

    public void configure(ServiceInjection serviceInjection) {
        super.configure(serviceInjection);
        this.resourceManager = new ImplementationsClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
        this.componentService = serviceInjection.getService(ComponentService.class);
        this.apiVersionService = serviceInjection.getService(ApiVersionService.class);
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        this.implementationVersionService = serviceInjection.getService(ImplementationVersionService.class);
        this.pipelineService = serviceInjection.getService(PipelineService.class);
        this.catalogEntryService = serviceInjection.getService(CatalogEntryService.class);
        this.groupService = serviceInjection.getService(GroupService.class);
        this.imageRegistryHost = serviceInjection.getConfig().getImageRegistryHost();
        this.binaryRepositoryService = serviceInjection.getService(BinaryRepositoryService.class);
    }


    public ImplementationService(ComponentService componentService, ApiVersionService apiVersionService, SourceRepositoryService sourceRepositoryService, ImplementationVersionService implementationVersionService, ImplementationsClient resourceManager, PipelineService pipelineService, KathraSessionManager kathraSessionManager, CatalogEntryService catalogEntryService, BinaryRepositoryService binaryRepositoryService, GroupService groupService, String imageRegistryHost) {
        // Init with clients and services
        this.componentService = componentService;
        this.apiVersionService = apiVersionService;
        this.sourceRepositoryService = sourceRepositoryService;
        this.implementationVersionService = implementationVersionService;
        this.resourceManager = resourceManager;
        this.pipelineService = pipelineService;
        super.kathraSessionManager = kathraSessionManager;
        this.catalogEntryService = catalogEntryService;
        this.groupService = groupService;
        this.imageRegistryHost = imageRegistryHost;
        this.binaryRepositoryService = binaryRepositoryService;
    }

    public Implementation create(@NotNull String name, Implementation.LanguageEnum language, ApiVersion apiVersion, String description) throws ApiException {

        if (StringUtils.isEmpty(name)) {
            throw new IllegalArgumentException("Implementation's name is null or empty.");
        } else if (!PATTERN_NAME.matcher(name).find()) {
            throw new IllegalArgumentException("Implementation's name do not respect regex's pattern " + PATTERN_NAME.pattern());
        } else if (language == null) {
            throw new IllegalArgumentException("Language is null.");
        } else if (apiVersion == null) {
            throw new IllegalArgumentException("ApiVersion is null.");
        } else if (getAll().parallelStream().anyMatch(impl -> impl.getName().equals(name))) {
            throw new IllegalArgumentException("Implementation's name already exists.");
        }
        ApiVersion apiVersionWithDetails = apiVersionService.getById(apiVersion.getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find ApiVersion with id : '" + apiVersion.getId() + "'"));
        if (!isReady(apiVersionWithDetails)) {
            throw new IllegalStateException("ApiVersion '" + apiVersionWithDetails.getId() + "' is not READY");
        }
        Component componentWithDetails = componentService.getById(apiVersionWithDetails.getComponent().getId()).orElseThrow(() -> new IllegalArgumentException("Unable to find Component with id : '" + apiVersionWithDetails.getComponent().getId() + "'"));
        if (componentWithDetails.getName().equals(name)) {
            throw new IllegalArgumentException("Implementation's name should be different component's name.");
        } else if (componentWithDetails.getMetadata() == null || !(componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_ID) instanceof String)) {
            throw new IllegalArgumentException("Component's metadata should contains entry with key '" + ComponentService.METADATA_GROUP_ID + "'.");
        } else if (componentWithDetails.getMetadata() == null || !(componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_PATH) instanceof String)) {
            throw new IllegalArgumentException("Component's metadata should contains entry with key '" + ComponentService.METADATA_GROUP_PATH + "'.");
        } else if (componentWithDetails.getMetadata() == null || !(componentWithDetails.getMetadata().get(ComponentService.METADATA_API_GROUP_ID) instanceof String)) {
            throw new IllegalArgumentException("Component's metadata should contains entry with key '" + ComponentService.METADATA_API_GROUP_ID + "'.");
        }

        String path = componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_PATH) + "/components/" +
                componentWithDetails.getName() +
                "/implementations/" + language.getValue() + "/" + name;

        String artifactName = getArtifactName(name);
        String artifactGroupId = (String) componentWithDetails.getMetadata().get(ComponentService.METADATA_API_GROUP_ID);

        Group group = this.groupService.getById((String)(componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_ID))).get();
        BinaryRepository binaryRepository = binaryRepositoryService.getBinaryRepositoryFromGroupAndType(group, BinaryRepository.TypeEnum.DOCKER_IMAGE).get(0);

        final Implementation impl = resourceManager.addImplementation(new Implementation().name(name)
                .component(componentWithDetails)
                .language(language)
                .binaryRepository(binaryRepository)
                .status(Resource.StatusEnum.PENDING)
                .description(description==null?"":description)
                .putMetadataItem(METADATA_PATH, path)
                .putMetadataItem(METADATA_DEPLOY_KEY, componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_ID))
                .putMetadataItem(METADATA_GROUP_ID, componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_ID))
                .putMetadataItem(METADATA_GROUP_PATH, componentWithDetails.getMetadata().get(ComponentService.METADATA_GROUP_PATH))
                .putMetadataItem(METADATA_ARTIFACT_NAME, artifactName)
                .putMetadataItem(METADATA_ARTIFACT_GROUP_ID, artifactGroupId));



        final Session session = kathraSessionManager.getCurrentSession();

        // ON PIPELINE READY
        Runnable onPipelineReady = () -> {
            try {
                kathraSessionManager.handleSession(session);
                final Implementation implementationWithDetails = resourceManager.getImplementation(impl.getId());
                if (isError(implementationWithDetails)) {
                    return;
                }
                Pipeline pipeline = pipelineService.getById(implementationWithDetails.getPipeline().getId()).orElseThrow(() -> new IllegalStateException("Unable to find Pipeline " + implementationWithDetails.getPipeline().getId()));
                switch (pipeline.getStatus()) {
                    case READY:
                        createFirstImplementationVersion(implementationWithDetails, apiVersion);
                        break;
                    default:
                        throw new IllegalStateException("Pipeline '" + pipeline.getId() + "' is not READY");
                }
            } catch (Exception e) {
                manageError(impl, e);
            }
        };

        // ON REPOSITORY READY
        Runnable onRepositoryReady = () -> {
            kathraSessionManager.handleSession(session);
            try {
                final Implementation implementationWithDetails = resourceManager.getImplementation(impl.getId());
                if (isError(implementationWithDetails)) {
                    return;
                }
                SourceRepository sourceRepository = sourceRepositoryService.getById(implementationWithDetails.getSourceRepository().getId()).orElseThrow(() -> new IllegalStateException("Unable to find SourceRepository " + implementationWithDetails.getSourceRepository().getId()));
                switch (sourceRepository.getStatus()) {
                    case READY:
                        createPipeline(implementationWithDetails, onPipelineReady);
                        break;
                    default:
                        throw new IllegalStateException("SourceRepository '" + sourceRepository.getId() + "' is not READY");
                }
            } catch (ApiException e) {
                manageError(impl, e);
            }
        };

        CompletableFuture.runAsync(() -> {
            kathraSessionManager.handleSession(session);
            createSourceRepository(impl, onRepositoryReady);
        });
        return impl;
    }

    private String getArtifactName(String implementationName) {
        return implementationName.toLowerCase().replaceAll("[^a-z]*", "");
    }

    public void createSourceRepository(final Implementation implementation, Runnable onRepositoryReady) {
        try {
            String[] deployKeys = {(String) implementation.getMetadata().get(METADATA_DEPLOY_KEY)};
            SourceRepository src = sourceRepositoryService.create("impl-" + implementation.getName(), (String) implementation.getMetadata().get(METADATA_PATH), deployKeys, onRepositoryReady);
            patch(new Implementation().id(implementation.getId()).sourceRepository(src));
        } catch (Exception e) {
            manageError(implementation, e);
        }
    }

    private void createPipeline(final Implementation implementation, Runnable callback) {

        final Pipeline.TemplateEnum template = getTemplateFromImplementation(implementation);
        final String pathPipeline = (String) implementation.getMetadata().get(METADATA_PATH);
        final String credentialId = (String) implementation.getMetadata().get(METADATA_DEPLOY_KEY);
        final SourceRepository sourceRepository;
        try {
            sourceRepository = sourceRepositoryService.getById(implementation.getSourceRepository().getId()).orElseThrow(() -> new IllegalStateException("Unable to find SourceRepository '" + implementation.getSourceRepository().getId() + "'"));
            Map<String, Object> args = ImmutableMap.of("dockerBinaryRepositoryHost", this.imageRegistryHost);
            Pipeline pipeline = pipelineService.create(implementation.getName(), pathPipeline, sourceRepository, template, credentialId, callback, args);
            patch(new Implementation().id(implementation.getId()).pipeline(pipeline));
        } catch (Exception e) {
            manageError(implementation, e);
        }
    }

    private Pipeline.TemplateEnum getTemplateFromImplementation(Implementation implementation) {
        switch (implementation.getLanguage()) {
            case JAVA:
                return Pipeline.TemplateEnum.JAVA_SERVICE;
            case PYTHON:
                return Pipeline.TemplateEnum.PYTHON_SERVICE;
            default:
                throw new NotImplementedException("Implementation's language " + implementation.getLanguage() + " not implemented");
        }
    }

    private void createFirstImplementationVersion(final Implementation implementationWithDetails, ApiVersion apiVersion) {
        final Session session = kathraSessionManager.getCurrentSession();
        Runnable callbackAfterImplementationVersionReadyOrError = () -> {
            try {
                kathraSessionManager.handleSession(session);
                String implVersionId = resourceManager.getImplementation(implementationWithDetails.getId()).getVersions().get(0).getId();
                ImplementationVersion firstVersion = implementationVersionService.getById(implVersionId).orElseThrow(() -> new IllegalStateException("Unable to find ImplementationVersion with id :"+implVersionId));
                switch (firstVersion.getStatus()) {
                    case READY:
                        updateStatus(implementationWithDetails, Resource.StatusEnum.READY);
                        try {
                            initCatalogEntryService(implementationWithDetails);
                        } catch (ApiException e) {
                            e.printStackTrace();
                        }
                        break;
                    default:
                        throw new IllegalStateException("ImplementationVersion '" + firstVersion.getId() + "' is not READY");
                }
            } catch (Exception e) {
                manageError(implementationWithDetails, e);
            }
        };
        try {
            implementationWithDetails.addVersionsItem(implementationVersionService.create(implementationWithDetails, apiVersion, FIRST_VERSION, callbackAfterImplementationVersionReadyOrError));
        } catch (Exception e) {
            manageError(implementationWithDetails, e);
        }
    }

    private void initCatalogEntryService(final Implementation implementation) throws ApiException {
        CatalogEntryTemplate template = new CatalogEntryTemplate().name("RestApiFromImplementation")
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("NAME").value(implementation.getName()))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("DESCRIPTION").value(implementation.getDescription()))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("GROUP_PATH").value((String) implementation.getMetadata().get(METADATA_GROUP_PATH)))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_ID").value(implementation.getId()))
                .addArgumentsItem(new CatalogEntryTemplateArgument().key("IMPLEMENTATION_VERSION").value(FIRST_VERSION));
        CatalogEntry catalogEntry = this.catalogEntryService.create(template);
        implementation.addCatalogEntriesItem(catalogEntry);
        patch(new Implementation().id(implementation.getId()).addCatalogEntriesItem(catalogEntry));
    }


    public List<Implementation> getComponentImplementations(String componentId) throws ApiException {
        List<Implementation> implementations = resourceManager.getImplementations();
        if (implementations == null || implementations.isEmpty()) return new ArrayList<>();

        return implementations.stream().filter(implementation -> implementation.getComponent().getId().equals(componentId)).collect(Collectors.toList());
    }


    @Override
    public Optional<Implementation> getById(String implemId) throws ApiException {
        Implementation implem = resourceManager.getImplementation(implemId);
        return implem == null ? Optional.empty() : Optional.of(implem);
    }

    @Override
    public void patch(Implementation implem) throws ApiException {
        resourceManager.updateImplementationAttributes(implem.getId(), implem);
    }


    @Override
    public List<Implementation> getAll() throws ApiException {

        List<Implementation> implementations = resourceManager.getImplementations();
        if (implementations == null || implementations.isEmpty()) return new ArrayList<>();

        return implementations;
    }

    public List<Implementation> fillImplementationWithVersions(List<Implementation> implementations, List<ImplementationVersion> implementationVersions) throws ApiException {
        if (implementations == null || implementations.isEmpty()) return new ArrayList<>();

        // Cleaning the default provided implementations
        for (Implementation implementation : implementations)
            implementation.setVersions(new LinkedList<>());

        // Converting list to map to optimize accesses
        Map<String, Implementation> implementationMap =
                implementations.stream().collect(Collectors.toMap(Implementation::getId, implementation -> implementation));


        // Completing implementations with their related implementationVersions
        for (ImplementationVersion implementationVersion : implementationVersions) {
            Implementation implementation = implementationMap.get(implementationVersion.getImplementation().getId());

            if (implementation != null)
                implementation.addVersionsItem(implementationVersion);
        }
        return implementations;

    }

    public void delete(Implementation implementation, boolean purge) throws ApiException {
        try {
            Implementation implementationToDelete = resourceManager.getImplementation(implementation.getId());
            if (isDeleted(implementationToDelete)) {
                return;
            }
            final AtomicReference<ApiException> exceptionFound = new AtomicReference<>();
            final Session session = kathraSessionManager.getCurrentSession();
            implementationToDelete.getVersions().parallelStream().forEach(version -> {
                kathraSessionManager.handleSession(session);
                try {
                    implementationVersionService.delete(version, purge);
                } catch (ApiException e) {
                    exceptionFound.set(e);
                }
            });
            if (exceptionFound.get() != null) {
                throw exceptionFound.get();
            }
            if (implementation.getPipeline() != null) {
                pipelineService.delete(implementation.getPipeline(), purge);
            }
            if (implementation.getSourceRepository() != null) {
                sourceRepositoryService.delete(implementation.getSourceRepository(), purge);
            }
            resourceManager.deleteImplementation(implementation.getId());
            implementation.status(Resource.StatusEnum.DELETED);
        } catch (ApiException e) {
            manageError(implementation, e);
            throw e;
        }
    }

    public void tryToReconcile(Implementation implementation) throws Exception {
        if (StringUtils.isEmpty(implementation.getName())) {
            throw new IllegalStateException("Name null or empty");
        }
        if (implementation.getLanguage() == null) {
            throw new IllegalStateException("Language null or empty");
        }
        if (implementation.getComponent() == null) {
            throw new IllegalStateException("Component null or empty");
        }
        if (isReady(implementation) || isPending(implementation) || isDeleted(implementation)) {
            return;
        }

        // CHECK SOURCE REPOSITORY
        Optional<SourceRepository> sourceRepository = Optional.empty();
        if (implementation.getSourceRepository() != null && implementation.getSourceRepository().getId() != null) {
            sourceRepository = sourceRepositoryService.getById(implementation.getSourceRepository().getId());
        }
        if (sourceRepository.isEmpty()) {
            createSourceRepository(implementation, () -> {});
            throw new IllegalStateException("Source repository missing, create new one");
        } else if (!sourceRepositoryService.isReady(sourceRepository.get())) {
            throw new IllegalStateException("Library source repository '"+sourceRepository.get().getId()+"' not ready");
        }


        // CHECK PIPELINE
        Optional<Pipeline> pipeline = Optional.empty();
        if (implementation.getPipeline() != null && implementation.getPipeline().getId() != null) {
            pipeline = pipelineService.getById(implementation.getPipeline().getId());
        }
        if (pipeline.isEmpty()) {
            createPipeline(implementation, () -> {});
            throw new IllegalStateException("Pipeline missing, create new one");
        } else if (!pipelineService.isReady(pipeline.get())) {
            throw new IllegalStateException("Library pipeline '"+pipeline.get().getId()+"' not ready");
        }


        // CHECK FIRST VERSION
        final Session session = kathraSessionManager.getCurrentSession();
        Optional<ImplementationVersion> firstImpl = implementation.getVersions().stream().map(version -> {
            kathraSessionManager.handleSession(session);
            try {
                return this.implementationVersionService.getById(version.getId());
            } catch (ApiException e) {
                e.printStackTrace();
                return null;
            }
        }).filter(implementationVersion -> implementationVersion != null && implementationVersion.isPresent() && implementationVersion.get().getVersion().equals(FIRST_VERSION)).map(Optional::get).findFirst();
        if (firstImpl.isEmpty()) {
            ApiVersion apiVersion = this.componentService.getById(implementation.getComponent().getId()).get().getVersions().get(0);
            ApiVersion apiVersionWithDetails = this.apiVersionService.getById(apiVersion.getId()).get();
            this.createFirstImplementationVersion(implementation, apiVersionWithDetails);
            throw new IllegalStateException("Implementation version not exist, create new one from version "+apiVersion.getVersion());
        } else if (!this.implementationVersionService.isReady(firstImpl.get())) {
            throw new IllegalStateException("First version of implementation is not ready");
        }

        // CHECK CATALOG
        if (implementation.getCatalogEntries().isEmpty()) {
            this.initCatalogEntryService(implementation);
        }

        updateStatus(implementation, Resource.StatusEnum.READY);
    }
}

