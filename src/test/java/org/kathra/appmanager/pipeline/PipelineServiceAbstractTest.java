/* 
 * Copyright 2019 The Kathra Authors.
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
 *
 *    IRT SystemX (https://www.kathra.org/)    
 *
 */
package org.kathra.appmanager.pipeline;

import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.pipelinemanager.client.PipelineManagerClient;
import org.kathra.resourcemanager.client.PipelinesClient;
import org.kathra.utils.ApiException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author julien.boubechtoula
 */
public class PipelineServiceAbstractTest extends AbstractServiceTest {

    protected final String PIPELINE_ID = "pipeline-id";
    protected final String PIPELINE_PROVIDER_ID = "provider-pipeline-id";
    protected final String PIPELINE_PATH = "pipeline-path";

    protected final String SRC_ID = "src-id";
    protected final String SRC_PATH = "src-path";
    protected final String SRC_SSH_URL = "ssh://my-repository/repo.git";

    protected final String BUILD_NUMBER = "build-number-xxx";

    PipelineService underTest;

    @Mock
    PipelinesClient resourceManager;
    @Mock
    PipelineManagerClient pipelineManagerClient;
    @Mock
    LibraryService libraryService;
    @Mock
    SourceRepositoryService sourceRepositoryService;
    @Mock
    ComponentService componentService;

    String id;
    String providerId;
    String provider;
    String credentialId;
    Pipeline pipelineDb;

    public void resetMock() throws ApiException {
        Mockito.reset(resourceManager);
        Mockito.reset(pipelineManagerClient);
        Mockito.reset(libraryService);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(componentService);
        callback = Mockito.mock(Runnable.class);
        underTest = new PipelineService(resourceManager, pipelineManagerClient, libraryService, sourceRepositoryService, kathraSessionManager, componentService);
        underTest.setIntervalCheckMs(200);
        underTest.setIntervalTimeoutMs(5000);

        id = UUID.randomUUID().toString();
        providerId = "jenkins-"+id;
        provider = "jenkins";
        credentialId = (String) getComponent().getMetadata().get("groupId");
        pipelineDb = new Pipeline();
        mockComponent();
        mockSourceRepository(getSourceRepository());
    }

    protected void mockComponent() throws ApiException {
        Mockito.doReturn(Optional.of(getComponent())).when(componentService).getById(Mockito.eq(ComponentServiceTest.COMPONENT_ID));
    }

    protected Component getComponent() {
        return new ComponentServiceTest().getComponentWithId();
    }


    public Pipeline getPipeline() {
        return new Pipeline()   .id(PIPELINE_ID)
                .path(PIPELINE_PATH)
                .provider("jenkins")
                .providerId(PIPELINE_PROVIDER_ID)
                .sourceRepository(getSourceRepository())
                .status(Resource.StatusEnum.READY);
    }
    protected Library getLibrary(){
        return new Library().id(UUID.randomUUID().toString())
                .name("component-name - " + Library.LanguageEnum.PYTHON+" - " + Library.TypeEnum.MODEL)
                .component(new Component().id(getComponent().getId()))
                .language(Library.LanguageEnum.PYTHON)
                .type(Library.TypeEnum.MODEL)
                .sourceRepository(new SourceRepository().id(SRC_ID));
    }

    protected void waitUntilPipelineIsNotPending(long timeout) throws Exception {
        long start = System.currentTimeMillis();

        Thread.sleep(500);
        Resource.StatusEnum status = pipelineDb.getStatus();
        while(status.equals(Resource.StatusEnum.PENDING)) {
            if (System.currentTimeMillis() - start > timeout) {
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            Thread.sleep(200);
            status = pipelineDb.getStatus();
        }
    }

    protected String getPathExpectedFromLibrary(Library library) {
        return getComponent().getMetadata().get("groupPath") + "/components/" + getComponent().getName() + "/" + library.getLanguage() + "/" + library.getType();
    }

    protected void assertionPipelineReady(String id, String providerId, String provider, String credentialId, String pathExpected) throws ApiException {
        Optional<Pipeline> pipelineReadyResult = underTest.getById(id);
        Assertions.assertTrue(pipelineReadyResult.isPresent());
        Pipeline pipelineReady = pipelineReadyResult.get();
        assertEquals(id, pipelineReady.getId());
        assertEquals(provider, pipelineReady.getProvider());
        assertEquals(providerId, pipelineReady.getProviderId());
        assertEquals(pathExpected, pipelineReady.getPath());
        assertEquals(Pipeline.TemplateEnum.PYTHON_LIBRARY, pipelineReady.getTemplate());
        assertEquals(credentialId, pipelineReady.getCredentialId());
        assertEquals(Resource.StatusEnum.READY, pipelineReady.getStatus());
    }

    protected void assertPendingPipeline(String id, String crendentialId, String pathExpected, Pipeline pipelinePending) {
        assertNotNull(pipelinePending);
        assertNotNull(pipelinePending.getId());
        assertEquals(id, pipelinePending.getId());
        assertEquals(pathExpected, pipelinePending.getPath());
        assertEquals(Pipeline.TemplateEnum.PYTHON_LIBRARY, pipelinePending.getTemplate());
        assertEquals(crendentialId, pipelinePending.getCredentialId());
        assertEquals(Resource.StatusEnum.PENDING,pipelinePending.getStatus());
    }

    protected void mockUpdatePipeline(String id, Pipeline pipelineDb) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            if (invocationOnMock.getArgument(0).equals(id)) {
                Pipeline toUpdate = invocationOnMock.getArgument(1);
                pipelineDb.id(id);
                pipelineDb.setPath(toUpdate.getPath());
                pipelineDb.setSourceRepository(toUpdate.getSourceRepository());
                pipelineDb.setTemplate(toUpdate.getTemplate());
                pipelineDb.setProvider(toUpdate.getProvider());
                pipelineDb.setProviderId(toUpdate.getProviderId());
                pipelineDb.setCredentialId(toUpdate.getCredentialId());
                pipelineDb.setStatus(toUpdate.getStatus());
                Mockito.when(resourceManager.getPipeline(pipelineDb.getId())).thenReturn(copy(pipelineDb));
                return copy(pipelineDb);
            } else {
                return null;
            }
        }).when(resourceManager).updatePipeline(Mockito.eq(id), Mockito.any());
    }

    protected void mockPatchPipeline(String id, Pipeline pipelineDb) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            if (invocationOnMock.getArgument(0).equals(id)) {
                Pipeline pipelinePatch = invocationOnMock.getArgument(1);
                if (pipelinePatch.getStatus() != null) {
                    pipelineDb.status(pipelinePatch.getStatus());
                }
                if (pipelinePatch.getProviderId() != null) {
                    pipelineDb.providerId(pipelinePatch.getProviderId());
                }
                if (pipelinePatch.getProvider() != null) {
                    pipelineDb.provider(pipelinePatch.getProvider());
                }
                Mockito.when(resourceManager.getPipeline(pipelineDb.getId())).thenReturn(copy(pipelineDb));
                return pipelineDb;
            } else {
                return null;
            }
        }).when(resourceManager).updatePipelineAttributes(Mockito.eq(id), Mockito.any());
    }

    protected void mockPipelineManager(String id, String provider, String providerId, long creationDuration) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            Pipeline pipeline = invocationOnMock.getArgument(0);
            pipeline.provider(provider);
            pipeline.providerId(providerId);
            Thread.sleep(creationDuration);
            if (pipeline.getSourceRepository() == null || StringUtils.isEmpty(pipeline.getSourceRepository().getSshUrl())) {
                throw new Exception("src repo or ssh url is null");
            }
            return pipeline;
        }).when(pipelineManagerClient).createPipeline(Mockito.argThat(pipeline -> pipeline.getId().equals(id)));
    }

    protected void mockAddPipeline(String id, Library library, String pathExpected, Pipeline pipelineDb) throws ApiException {
        Mockito.doAnswer(invocation -> {
            Pipeline pipelineToAdd = invocation.getArgument(0);
            if (!pipelineToAdd.getSourceRepository().getId().equals(SRC_ID)) {
                return null;
            }
            pipelineDb.id(id);
            pipelineDb.setPath(pipelineToAdd.getPath());
            pipelineDb.setSourceRepository(pipelineToAdd.getSourceRepository());
            pipelineDb.setTemplate(pipelineToAdd.getTemplate());
            pipelineDb.setProvider(pipelineToAdd.getProvider());
            pipelineDb.setProviderId(pipelineToAdd.getProviderId());
            pipelineDb.setCredentialId(pipelineToAdd.getCredentialId());
            pipelineDb.setStatus(Resource.StatusEnum.PENDING);
            Mockito.when(resourceManager.getPipeline(pipelineDb.getId())).thenReturn(copy(pipelineDb));
            return copy(pipelineDb);
        }).when(resourceManager).addPipeline(Mockito.argThat(pipeline -> pipeline.getPath().equals(pathExpected) &&
                                                                    pipeline.getTemplate().equals(Pipeline.TemplateEnum.PYTHON_LIBRARY)));
    }

    protected Pipeline copy(Pipeline pipeline) {
        return new Pipeline()
                .id(pipeline.getId())
                .name(pipeline.getName())
                .status(pipeline.getStatus())
                .path(pipeline.getPath())
                .sourceRepository(pipeline.getSourceRepository())
                .template(pipeline.getTemplate())
                .credentialId(pipeline.getCredentialId())
                .providerId(pipeline.getProviderId())
                .provider(pipeline.getProvider());
    }

    protected void mockSourceRepository(SourceRepository sourceRepository) throws ApiException {
        Mockito.doReturn(Optional.of(sourceRepository)).when(this.sourceRepositoryService).getById(sourceRepository.getId());
    }

    protected SourceRepository getSourceRepository() {
        return new SourceRepository().id(SRC_ID).sshUrl(SRC_SSH_URL).path(SRC_PATH).status(Resource.StatusEnum.READY);
    }
}
