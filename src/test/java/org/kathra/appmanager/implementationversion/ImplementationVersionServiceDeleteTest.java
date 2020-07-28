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
package org.kathra.appmanager.implementationversion;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.implementation.ImplementationServiceCreateTest;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationVersionsClient;
import org.kathra.utils.ApiException;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kathra.appmanager.implementation.ImplementationServiceCreateTest.API_VERSION_ID;
import static org.kathra.appmanager.implementation.ImplementationServiceCreateTest.getApiVersion;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ImplementationVersionServiceDeleteTest extends ImplementationVersionServiceTest {

    @BeforeEach
    void setUp() throws Exception {
        super.setUp();
    }


    @Test
    public void given_implVersion_when_delete_then_work() throws ApiException {
        ImplementationVersion implV = getImplementationVersion();
        Mockito.when(resourceManager.getImplementationVersion(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        underTest.delete(implV, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, implV.getStatus());
        Mockito.verify(resourceManager).deleteImplementationVersion(implV.getId());
    }

    @Test
    public void given_implVersion_deleted_when_delete_then_do_nothing() throws ApiException {
        ImplementationVersion implV = getImplementationVersion();
        Mockito.when(resourceManager.getImplementationVersion(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.DELETED);
        underTest.delete(implV, true);
        Assertions.assertEquals(Resource.StatusEnum.DELETED, implV.getStatus());
        Mockito.verify(resourceManager, Mockito.never()).deleteImplementationVersion(implV.getId());
    }

    @Test
    public void given_implVersion_with_deletingError_when_delete_then_throws_exception() throws ApiException {
        ImplementationVersion implV = getImplementationVersion();
        Mockito.when(resourceManager.getImplementationVersion(implV.getId())).thenReturn(implV);
        implV.setStatus(Resource.StatusEnum.READY);
        Mockito.doThrow(new ApiException("Internal error")).when(resourceManager).deleteImplementationVersion(implV.getId());
        assertThrows(ApiException.class, () -> {
            underTest.delete(implV, true);
        });
        Assertions.assertEquals(Resource.StatusEnum.ERROR, implV.getStatus());
    }
}
