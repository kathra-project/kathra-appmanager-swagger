package org.kathra.appmanager.binaryrepository;

import org.kathra.appmanager.codegen.CodeGenProxyService;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.binaryrepositorymanager.model.Credential;
import org.kathra.catalogmanager.client.ReadCatalogEntriesClient;
import org.kathra.core.model.BinaryRepository;
import org.kathra.core.model.CatalogEntryPackage;
import org.kathra.core.model.Group;
import org.kathra.resourcemanager.client.BinaryRepositoriesClient;
import org.kathra.binaryrepositorymanager.client.BinaryRepositoryManagerClient;
import org.kathra.resourcemanager.client.CatalogEntryPackagesClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class BinaryRepositoryService extends AbstractResourceService<BinaryRepository> {

    private BinaryRepositoryManagerClient client;
    private BinaryRepositoriesClient resourceManager;


    public BinaryRepositoryService() {

    }

    public BinaryRepositoryService(BinaryRepositoriesClient resourceManager, KathraSessionManager kathraSessionManager, BinaryRepositoryManagerClient client) {
        this.resourceManager = resourceManager;
        super.kathraSessionManager = kathraSessionManager;
        this.client = client;
    }

    public void configure(ServiceInjection service) {
        super.configure(service);
        this.resourceManager = new BinaryRepositoriesClient(service.getConfig().getResourceManagerUrl(), service.getSessionManager());
        this.client = new BinaryRepositoryManagerClient(service.getConfig().getBinaryManagerHarbor());
    }

    public List<BinaryRepository> getBinaryRepositoryFromGroupAndType(Group group, BinaryRepository.TypeEnum type) {
        return group.getBinaryRepositories().stream().map(binaryRepository -> {
            try {
                return resourceManager.getBinaryRepository(binaryRepository.getId());
            } catch (ApiException e) {
                e.printStackTrace();
                return null;
            }
        }).filter(binaryRepository -> binaryRepository != null && type.equals(binaryRepository.getType())).collect(Collectors.toList());
    }

    public Credential getCredential(BinaryRepository binaryRepository) throws ApiException {
        return client.credentialsIdGet(binaryRepository.getProviderId());
    }

    @Override
    protected void patch(BinaryRepository object) throws ApiException {
        client.updateBinaryRepositoryAttributes(object.getId(), object);
    }

    @Override
    public Optional<BinaryRepository> getById(String id) throws ApiException {
        BinaryRepository o = resourceManager.getBinaryRepository(id);
        return (o == null) ? Optional.empty() : Optional.of(o);
    }

    @Override
    public List<BinaryRepository> getAll() throws ApiException {
        return resourceManager.getBinaryRepositories();
    }
}
