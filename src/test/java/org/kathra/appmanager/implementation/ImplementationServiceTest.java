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
import org.kathra.appmanager.service.ApiVersionsService;
import org.kathra.appmanager.service.ComponentsService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationsClient;
import org.kathra.sourcemanager.client.SourceManagerClient;
import org.kathra.utils.ApiException;
import org.junit.Assert;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Test ImplementationService
 *
 * @author quentin.semanne
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImplementationServiceTest extends AbstractImplementationTest {

    /**
     * Local Variables
     */
    private ImplementationService underTest;

    /**
     * Mock clients & services
     */
    @Mock
    private ApiVersionService apiVersionService;

    @Mock
    private ComponentService componentService;

    @Mock
    private SourceRepositoryService sourceRepositoryService;

    @Mock
    private ImplementationsClient implementationsClient;

    @Mock
    private ImplementationVersionService implementationVersionService;


    /**
     * Context initialization
     */
    @BeforeAll
    static void setUp() throws Exception {
    }

    /**
     * Mocks behaviour initialization
     */
    @BeforeEach
    void setUpEach() throws Exception {
        this.apiVersionService = Mockito.mock(ApiVersionService.class);
        this.componentService = Mockito.mock(ComponentService.class);
        this.sourceRepositoryService = Mockito.mock(SourceRepositoryService.class);
        this.implementationsClient = Mockito.mock(ImplementationsClient.class);
        this.implementationVersionService = Mockito.mock(ImplementationVersionService.class);

        underTest = new ImplementationService(this.componentService, this.apiVersionService, this.sourceRepositoryService, this.implementationVersionService, this.implementationsClient, null, kathraSessionManager);
    }

    /**
     * After tests
     */
    @AfterAll
    static void tearDown() {
    }

    @Test
    public void given_nominal_args_when_creating_implementation_then_works() throws Exception {

        //assertThat("The response HTTP Status Code is 201(Created)", underTest.addContainersRepository(repoToCreate).getStatusCode(), is(201));
        //assertThat("The response informs the resource have been created", underTest.addContainersRepository(repoToCreate).getData(), is(""));
    }

    @Test
    public void when_getAll_then_works() throws Exception {
        Implementation implem1 = generateImplementationExample(Asset.LanguageEnum.JAVA);
        Implementation implem2 = generateImplementationExample(Asset.LanguageEnum.PYTHON);

        Mockito.when(implementationsClient.getImplementations()).thenReturn(ImmutableList.of(implem1, implem2));
        List<Implementation> items = underTest.getAll();

        Assertions.assertTrue(items.size()==2);
        Assertions.assertEquals(items.get(0), implem1);
        Assertions.assertEquals(items.get(1), implem2);
    }

    @Test
    public void when_getAll_then_return_empty() throws Exception {

        Mockito.when(implementationsClient.getImplementations()).thenReturn(new ArrayList<>());
        List<Implementation> items = underTest.getAll();

        Assertions.assertTrue(items.isEmpty());

    }

    @Test
    public void given_componentId_when_get_then_works() throws Exception {

        Implementation implem1 = generateImplementationExample(Asset.LanguageEnum.JAVA).component(new Component().id("c1"));
        Implementation implem2 = generateImplementationExample(Asset.LanguageEnum.PYTHON).component(new Component().id("c1"));
        Implementation implem3 = generateImplementationExample(Asset.LanguageEnum.JAVA).component(new Component().id("c2"));
        Implementation implem4 = generateImplementationExample(Asset.LanguageEnum.PYTHON).component(new Component().id("c2"));

        Mockito.when(implementationsClient.getImplementations()).thenReturn(ImmutableList.of(implem1,implem2,implem3,implem4));
        List<Implementation> items = underTest.getComponentImplementations("c1");

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(implem1, items.get(0));
        Assertions.assertEquals(implem2, items.get(1));

    }

    @Test
    public void when_fill_implementation_with_version_then_works() throws Exception {

        Implementation implem1 = generateImplementationExample(Asset.LanguageEnum.JAVA).id("1");
        Implementation implem2 = generateImplementationExample(Asset.LanguageEnum.PYTHON).id("2");

        ImplementationVersion implementationVersion1 = new ImplementationVersion().implementation(implem1).id("version1");
        ImplementationVersion implementationVersion1b = new ImplementationVersion().implementation(implem1).id("version1b");
        ImplementationVersion implementationVersion2 = new ImplementationVersion().implementation(implem2).id("version2");

        List<Implementation> items = underTest.fillImplementationWithVersions(ImmutableList.of(implem1, implem2),ImmutableList.of(implementationVersion1, implementationVersion1b, implementationVersion2));

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(items.get(0), implem1);
        Assertions.assertEquals(items.get(1), implem2);

        Assertions.assertEquals(2, items.get(0).getVersions().size());
        Assertions.assertEquals(1, items.get(1).getVersions().size());

    }

}
