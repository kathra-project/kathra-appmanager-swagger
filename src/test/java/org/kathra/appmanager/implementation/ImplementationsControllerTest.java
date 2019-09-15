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
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.model.ImplementationParameters;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;

import org.kathra.utils.KathraException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;


/**
 * @author quentin.semanne
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImplementationsControllerTest extends AbstractImplementationTest {


    ImplementationsController underTest;

    @Mock
    private ImplementationService implementationService;
    @Mock
    private ApiVersionService apiVersionService;
    @Mock
    private ImplementationVersionService implementationVersionService;
    @Mock
    private ComponentService componentService;
    @Mock
    private SourceRepositoryService sourceRepositoryService;
    @Mock
    private PipelineService pipelineService;

    @BeforeEach
    public void setUp() {
        implementationService = Mockito.mock(ImplementationService.class);
        implementationVersionService = Mockito.mock(ImplementationVersionService.class);
        underTest = new ImplementationsController(implementationService, implementationVersionService, apiVersionService, componentService, sourceRepositoryService, pipelineService);
    }

    @Test
    public void given_existing_id_when_getImplementation_then_works() throws Exception {
        Implementation implExpected = super.generateImplementationExample(Asset.LanguageEnum.JAVA);
        Mockito.doReturn(Optional.of(implExpected)).when(implementationService).getById(Mockito.eq(IMPL_ID));
        List<ImplementationVersion> implementationVersions = ImmutableList.of(new ImplementationVersion().implementation(implExpected));
        Mockito.when(implementationVersionService.getImplementationVersions(ImmutableList.of(implExpected))).thenReturn(implementationVersions);
        Implementation implExpectedWithVersions = getImplementation().versions(implementationVersions);
        Mockito.when(implementationService.fillImplementationWithVersions(ImmutableList.of(implExpected), implementationVersions)).thenReturn(ImmutableList.of(implExpectedWithVersions));

        Mockito.when(componentService.getById(implExpected.getComponent().getId())).thenReturn(Optional.of(implExpected.getComponent()));
        Mockito.when(sourceRepositoryService.getById(implExpected.getSourceRepository().getId())).thenReturn(Optional.of(implExpected.getSourceRepository()));
        Mockito.when(pipelineService.getById(implExpected.getPipeline().getId())).thenReturn(Optional.of(implExpected.getPipeline()));

        Implementation impl = underTest.getImplementationById(IMPL_ID);

        Assertions.assertEquals(implExpectedWithVersions, impl);
    }

    @Test
    public void given_no_existing_id_when_getImplementation_then_throw_kathra_exception() throws Exception {
        Mockito.doReturn(Optional.empty()).when(implementationService).getById(Mockito.eq(IMPL_ID));
        KathraException exception = assertThrows(KathraException.class, () -> {
            underTest.getImplementationById(IMPL_ID);
        });
        Assertions.assertEquals("Unable to find Implementation 'implementation-id'", exception.getMessage());
    }

    private Implementation getImplementation() {
        return new Implementation().id(IMPL_ID).name(IMPL_NAME);
    }

    @Test
    public void given_nominal_when_createImplementation_then_works() throws Exception {
        Implementation expected = super.generateImplementationExample(Asset.LanguageEnum.JAVA);
        Mockito.doReturn(expected).when(implementationService).create(Mockito.eq("newimpl"), Mockito.eq(Implementation.LanguageEnum.JAVA), Mockito.argThat(apiV -> apiV.getId().equals("api-version-id")),Mockito.any());
        Implementation impl = underTest.createImplementation(new ImplementationParameters().language("JAVA").name("newimpl").apiVersion(new ApiVersion().id("api-version-id")));
        Assertions.assertEquals(expected, impl);
    }

    @Test
    public void when_getImplementations_then_return_items() throws Exception {

        Implementation item1 = generateImplementationExample(Asset.LanguageEnum.JAVA);
        Implementation item2 = generateImplementationExample(Asset.LanguageEnum.PYTHON);

        Mockito.when(implementationService.getAll()).thenReturn(ImmutableList.of(item1, item2));
        Mockito.when(implementationVersionService.getImplementationVersions(ImmutableList.of(item1, item2))).thenReturn(Collections.emptyList());
        Mockito.when(implementationService.fillImplementationWithVersions(ImmutableList.of(item1, item2),Collections.emptyList())).thenReturn(ImmutableList.of(item1,item2));

        List<Implementation> items = underTest.getImplementations();

        Assertions.assertEquals(items.get(0), item1);
        Assertions.assertEquals(items.get(1), item2);
    }

    @Test
    public void when_getImplementations_then_return_empty() throws Exception {

        Mockito.when(implementationService.getAll()).thenReturn(Collections.emptyList());
        List<Implementation> items = underTest.getImplementations();

        Assertions.assertTrue(items.isEmpty());

    }

    @Test
    public void when_getImplementations_then_return_items_with_versions() throws Exception {

        Implementation implem1 = generateImplementationExample(Asset.LanguageEnum.JAVA);
        Implementation implem2 = generateImplementationExample(Asset.LanguageEnum.PYTHON);

        Mockito.when(implementationService.getAll()).thenReturn(ImmutableList.of(implem1, implem2));

        ImplementationVersion implementationVersion1 = new ImplementationVersion().implementation(implem1).id("version1");
        ImplementationVersion implementationVersion1b = new ImplementationVersion().implementation(implem1).id("version1b");
        ImplementationVersion implementationVersion2 = new ImplementationVersion().implementation(implem2).id("version2");

        Mockito.when(implementationVersionService.getImplementationVersions(ImmutableList.of(implem1, implem2))).thenReturn(ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2));
        Mockito.when(implementationService.fillImplementationWithVersions(ImmutableList.of(implem1, implem2),ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2))).thenReturn(ImmutableList.of(implem1.addVersionsItem(implementationVersion1).addVersionsItem(implementationVersion1b), implem2.addVersionsItem(implementationVersion2)));

        List<Implementation> items = underTest.getImplementations();

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(items.get(0), implem1);
        Assertions.assertEquals(items.get(1), implem2);

        Assertions.assertEquals(2, items.get(0).getVersions().size());
        Assertions.assertEquals(1, items.get(1).getVersions().size());

        Assertions.assertTrue(items.get(0).getVersions().contains(implementationVersion1));
        Assertions.assertTrue(items.get(0).getVersions().contains(implementationVersion1b));
        Assertions.assertTrue(items.get(1).getVersions().contains(implementationVersion2));

    }


    @Test
    public void given_componentId_when_getImplementations_then_return_empty_list() throws Exception {

        Mockito.when(implementationService.getComponentImplementations("comp1")).thenReturn(Collections.emptyList());
        Mockito.when(implementationVersionService.getImplementationVersions(Collections.emptyList())).thenReturn(Collections.emptyList());
        Mockito.when(apiVersionService.getApiVersionsForImplementationVersion(Collections.emptyList())).thenReturn(Collections.emptyList());
        Mockito.when(implementationVersionService.fillImplementationVersionWithApiVersion(Collections.emptyList(),Collections.emptyList())).thenReturn(Collections.emptyList());
        Mockito.when(implementationService.fillImplementationWithVersions(Collections.emptyList(),Collections.emptyList())).thenReturn(Collections.emptyList());

        List<Implementation> items = underTest.getComponentImplementations("comp1");

        Assertions.assertEquals(0, items.size());


    }

    @Test
    public void given_componentId_when_getImplementations_then_return_items_without_versions() throws Exception {
        Implementation implem1 = generateImplementationExample(Asset.LanguageEnum.JAVA);
        Implementation implem2 = generateImplementationExample(Asset.LanguageEnum.PYTHON);

        Mockito.when(implementationService.getComponentImplementations("comp1")).thenReturn(ImmutableList.of(implem1, implem2));
        Mockito.when(implementationVersionService.getImplementationVersions(ImmutableList.of(implem1, implem2))).thenReturn(Collections.emptyList());
        Mockito.when(apiVersionService.getApiVersionsForImplementationVersion(Collections.emptyList())).thenReturn(Collections.emptyList());
        Mockito.when(implementationVersionService.fillImplementationVersionWithApiVersion(Collections.emptyList(),Collections.emptyList())).thenReturn(Collections.emptyList());
        Mockito.when(implementationService.fillImplementationWithVersions(ImmutableList.of(implem1, implem2),Collections.emptyList())).thenReturn(ImmutableList.of(implem1, implem2));

        List<Implementation> items = underTest.getComponentImplementations("comp1");

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(items.get(0), implem1);
        Assertions.assertEquals(items.get(1), implem2);

    }

    @Test
    public void given_componentId_when_getImplementations_then_return_items_with_versions() throws Exception {

        Implementation implem1 = generateImplementationExample(Asset.LanguageEnum.JAVA);
        Implementation implem2 = generateImplementationExample(Asset.LanguageEnum.PYTHON);

        Mockito.when(implementationService.getComponentImplementations("comp1")).thenReturn(ImmutableList.of(implem1, implem2));

        ImplementationVersion implementationVersion1 = new ImplementationVersion().implementation(implem1).id("version1");
        ImplementationVersion implementationVersion1b = new ImplementationVersion().implementation(implem1).id("version1b");
        ImplementationVersion implementationVersion2 = new ImplementationVersion().implementation(implem2).id("version2");

        // ImplementationVersionMock
        Mockito.when(implementationVersionService.getImplementationVersions(ImmutableList.of(implem1, implem2))).thenReturn(ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2));

        ApiVersion api1 = new ApiVersion().id("api1").name("api 1");
        ApiVersion api2 = new ApiVersion().id("api2").name("api 2");
        ApiVersion api3 = new ApiVersion().id("api3").name("api 3");
        Mockito.when(apiVersionService.getApiVersionsForImplementationVersion(ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2))).thenReturn(ImmutableList.of(api1,api2,api3));

        Mockito.when(implementationVersionService.fillImplementationVersionWithApiVersion(ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2),ImmutableList.of(api1,api2,api3))).thenReturn(ImmutableList.of(implementationVersion1.apiVersion(api1),implementationVersion1b.apiVersion(api3), implementationVersion2.apiVersion(api2)));
        Mockito.when(implementationService.fillImplementationWithVersions(ImmutableList.of(implem1, implem2),ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2))).thenReturn(ImmutableList.of(implem1.addVersionsItem(implementationVersion1).addVersionsItem(implementationVersion1b), implem2.addVersionsItem(implementationVersion2)));

        List<Implementation> items = underTest.getComponentImplementations("comp1");

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(items.get(0), implem1);
        Assertions.assertEquals(items.get(1), implem2);

        Assertions.assertEquals(2, items.get(0).getVersions().size());
        Assertions.assertEquals(1, items.get(1).getVersions().size());

        Assertions.assertTrue(items.get(0).getVersions().contains(implementationVersion1));
        Assertions.assertTrue(items.get(0).getVersions().contains(implementationVersion1b));
        Assertions.assertTrue(items.get(1).getVersions().contains(implementationVersion2));

        Assertions.assertNotNull(items.get(0).getVersions().get(0).getApiVersion());
        Assertions.assertNotNull(items.get(0).getVersions().get(1).getApiVersion());
        Assertions.assertNotNull(items.get(1).getVersions().get(0).getApiVersion());

    }

}