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
package org.kathra.appmanager.implementation;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.core.model.Asset;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test ImplementationService
 *
 * @author quentin.semanne
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ImplementationServiceCreateTest extends ImplementationServiceTest {

    @Test
    public void given_existing_impl_name_when_create_then_throw_illegalArgException() throws Exception {
        Mockito.doReturn(ImmutableList.of(new Implementation().name(IMPL_NAME))).when(resourceManager).getImplementations();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA,getApiVersion(),null));
        Assertions.assertEquals("Implementation's name already exists.", exception.getMessage());
    }
    @Test
    public void given_empty_name_when_create_then_throw_illegalArgException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create("", Asset.LanguageEnum.JAVA,getApiVersion(),null));
        Assertions.assertEquals("Implementation's name is null or empty.", exception.getMessage());
    }
    @Test
    public void given_null_apiVersion_when_create_then_throw_illegalArgException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA, null,null));
        Assertions.assertEquals("ApiVersion is null.", exception.getMessage());
    }
    @Test
    public void given_null_lang_when_create_then_throw_illegalArgException() throws Exception {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> underTest.create(IMPL_NAME, null,getApiVersion(),null));
        Assertions.assertEquals("Language is null.", exception.getMessage());
    }
    @Test
    public void given_apiVersion_not_ready_when_create_then_throw_illegalStateException() throws Exception {
        Mockito.doReturn(Optional.of(getApiVersion().status(Resource.StatusEnum.ERROR))).when(apiVersionService).getById(Mockito.eq(API_VERSION_ID));
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA,getApiVersion(),null));
        Assertions.assertEquals("ApiVersion 'api-version-id' is not READY", exception.getMessage());
    }

    @Test
    public void given_nominal_args_when_create_and_wait_until_ready_then_implementation_is_ready() throws Exception {

        Implementation implemPending = underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA, getApiVersion(),null);

        Assertions.assertEquals(IMPL_ID, implemPending.getId());
        Assertions.assertEquals(IMPL_NAME, implemPending.getName());
        Assertions.assertEquals(COMPONENT_ID, implemPending.getComponent().getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, implemPending.getStatus());

        waitUntilNotPending(5000);

        Implementation implemReady = implementationDb;
        Assertions.assertEquals(IMPL_ID, implemReady.getId());
        Assertions.assertEquals(IMPL_NAME, implemReady.getName());
        Assertions.assertEquals(COMPONENT_ID, implemReady.getComponent().getId());
        Assertions.assertEquals(Resource.StatusEnum.READY, implemReady.getStatus());
        Assertions.assertEquals(SRC_REPO_ID, implemReady.getSourceRepository().getId());
        Assertions.assertEquals(PIPELINE_ID, implemReady.getPipeline().getId());
        Assertions.assertEquals(IMPL_VERSION_ID, implemReady.getVersions().get(0).getId());
        Assertions.assertEquals("1.0.0", implemReady.getVersions().get(0).getVersion());
        Assertions.assertEquals("implementationname", implemReady.getMetadata().get(ImplementationService.METADATA_ARTIFACT_NAME));
        Assertions.assertEquals(COMPONENT_ARTIFACT_GROUP_ID, implemReady.getMetadata().get(ImplementationService.METADATA_ARTIFACT_GROUP_ID));
        Assertions.assertEquals(GROUP_ID, implemReady.getMetadata().get(ImplementationService.METADATA_GROUP_ID));
        Assertions.assertEquals(GROUP_PATH, implemReady.getMetadata().get(ImplementationService.METADATA_GROUP_PATH));
    }

    @Test
    public void given_occurred_exception_patch_impl_when_create_and_wait_until_ready_then_implementation_is_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(resourceManager).updateImplementationAttributes(Mockito.any(), Mockito.any());

        Implementation implemPending = underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA, getApiVersion(),null);

        Assertions.assertEquals(IMPL_ID, implemPending.getId());
        Assertions.assertEquals(IMPL_NAME, implemPending.getName());
        Assertions.assertEquals(COMPONENT_ID, implemPending.getComponent().getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, implemPending.getStatus());
        Assertions.assertEquals(GROUP_ID, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_ID));
        Assertions.assertEquals(GROUP_PATH, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_PATH));

        waitUntilNotPending(5000);

        Implementation implemError = implementationDb;
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implemError.getStatus());
    }

    @Test
    public void given_occurred_exception_srcRepoService_when_create_and_wait_until_ready_then_implementation_is_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(sourceRepositoryService).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Implementation implemPending = underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA, getApiVersion(),null);

        Assertions.assertEquals(IMPL_ID, implemPending.getId());
        Assertions.assertEquals(IMPL_NAME, implemPending.getName());
        Assertions.assertEquals(COMPONENT_ID, implemPending.getComponent().getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, implemPending.getStatus());
        Assertions.assertEquals(GROUP_ID, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_ID));
        Assertions.assertEquals(GROUP_PATH, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_PATH));

        waitUntilNotPending(5000);

        Implementation implemError = implementationDb;
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implemError.getStatus());
    }

    @Test
    public void given_occurred_exception_pipelineService_when_create_and_wait_until_ready_then_implementation_is_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(pipelineService).create(Mockito.eq(IMPL_NAME), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Implementation implemPending = underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA, getApiVersion(),null);

        Assertions.assertEquals(IMPL_ID, implemPending.getId());
        Assertions.assertEquals(IMPL_NAME, implemPending.getName());
        Assertions.assertEquals(COMPONENT_ID, implemPending.getComponent().getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, implemPending.getStatus());
        Assertions.assertEquals(GROUP_ID, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_ID));
        Assertions.assertEquals(GROUP_PATH, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_PATH));

        waitUntilNotPending(5000);

        Implementation implemError = implementationDb;
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implemError.getStatus());
    }

    @Test
    public void given_occurred_exception_impVersionService_when_create_and_wait_until_ready_then_implementation_is_error() throws Exception {

        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(implementationVersionService).create(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

        Implementation implemPending = underTest.create(IMPL_NAME, Asset.LanguageEnum.JAVA, getApiVersion(),null);

        Assertions.assertEquals(IMPL_ID, implemPending.getId());
        Assertions.assertEquals(IMPL_NAME, implemPending.getName());
        Assertions.assertEquals(COMPONENT_ID, implemPending.getComponent().getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, implemPending.getStatus());
        Assertions.assertEquals(GROUP_ID, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_ID));
        Assertions.assertEquals(GROUP_PATH, implemPending.getMetadata().get(ImplementationService.METADATA_GROUP_PATH));

        waitUntilNotPending(5000);

        Implementation implemError = implementationDb;
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implemError.getStatus());
    }

}
