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
import org.kathra.appmanager.Config;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.implementation.ImplementationServiceTest;
import org.kathra.appmanager.pipeline.PipelineService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ImplementationVersionsClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImplementationVersionServiceTest extends AbstractServiceTest {

    /**
     * Local Variables
     */
    protected ImplementationVersionService underTest;

    @Mock
    protected Config config;
    @Mock
    protected ImplementationVersionsClient resourceManager;
    @Mock
    protected ComponentService componentService;
    @Mock
    protected ApiVersionService apiVersionService;
    @Mock
    protected ImplementationService implementationService;
    @Mock
    protected SourceRepositoryService sourceRepositoryService;
    @Mock
    protected CodegenClient codegenClient;
    @Mock
    protected PipelineService pipelineService;

    /**
     * Context initialization
     */
    @BeforeEach
    void setUp() throws Exception {
        Mockito.reset(resourceManager);
        Mockito.reset(implementationService);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(componentService);
        Mockito.reset(codegenClient);
        Mockito.reset(apiVersionService);
        Mockito.reset(pipelineService);
        callback = Mockito.mock(Runnable.class);
        Mockito.doReturn("my-registry.com").when(config).getImageRegistryHost();
        underTest = new ImplementationVersionService(this.config, this.resourceManager, apiVersionService, componentService, implementationService, sourceRepositoryService, codegenClient, kathraSessionManager, pipelineService);
    }



    /**
     * Mocks behaviour initialization
     */
    @BeforeEach
    void setUpEach() {
        this.resourceManager = Mockito.mock(ImplementationVersionsClient.class);
        Mockito.doReturn("my-registry.com").when(config).getImageRegistryHost();
        underTest = new ImplementationVersionService(this.config, this.resourceManager, apiVersionService, componentService, implementationService, sourceRepositoryService, codegenClient, kathraSessionManager, pipelineService);
    }

    public static ImplementationVersion getImplementationVersion() {
        return new ImplementationVersion().id("implementation-id");
    }

    /**
     * After tests
     */
    @AfterAll
    static void tearDown() {
    }

    @Test
    public void given_implementation_list_when_get_implementationVersion_then_works() throws Exception {

        Implementation implementation1 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.JAVA).id("1");
        Implementation implementation2 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON).id("2");
        Implementation implementation3 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.JAVA).id("3");
        Implementation implementation4 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON).id("4");

        List<Implementation> implementations = ImmutableList.of(implementation1, implementation2, implementation3);

        ImplementationVersion implementationVersion1 = new ImplementationVersion().implementation(implementation1).id("version1");
        ImplementationVersion implementationVersion1b = new ImplementationVersion().implementation(implementation1).id("version1b");
        ImplementationVersion implementationVersion2 = new ImplementationVersion().implementation(implementation2).id("version2");
        ImplementationVersion implementationVersion4 = new ImplementationVersion().implementation(implementation4).id("version4");

        List<ImplementationVersion> existing = ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2,implementationVersion4);


        Mockito.when(resourceManager.getImplementationVersions()).thenReturn(existing);

        List<ImplementationVersion> results = underTest.getImplementationVersions(implementations);

        Assertions.assertEquals(3, results.size());

        // Doesn't contains version4 because implementation4 isn't requested
        Assertions.assertTrue(results.containsAll(ImmutableList.of(implementationVersion1, implementationVersion1b, implementationVersion2)));
    }

    @Test
    public void given_empty_implementation_list_when_get_implementationVersion_then_works() throws Exception {

        Implementation implementation1 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.JAVA);
        Implementation implementation2 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON);
        Implementation implementation4 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON);

        ImplementationVersion implementationVersion1 = new ImplementationVersion().implementation(implementation1).id("version1");
        ImplementationVersion implementationVersion1b = new ImplementationVersion().implementation(implementation1).id("version1b");
        ImplementationVersion implementationVersion2 = new ImplementationVersion().implementation(implementation2).id("version2");
        ImplementationVersion implementationVersion4 = new ImplementationVersion().implementation(implementation4).id("version4");

        List<ImplementationVersion> existing = ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2,implementationVersion4);


        Mockito.when(resourceManager.getImplementationVersions()).thenReturn(existing);

        List<ImplementationVersion> results = underTest.getImplementationVersions(new ArrayList<>());

        Assertions.assertEquals(0, results.size());

    }

    @Test
    public void given_null_implementation_list_when_get_implementationVersion_then_works() throws Exception {

        Implementation implementation1 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.JAVA);
        Implementation implementation2 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON);
        Implementation implementation3 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON);

        ImplementationVersion implementationVersion1 = new ImplementationVersion().implementation(implementation1).id("version1");
        ImplementationVersion implementationVersion1b = new ImplementationVersion().implementation(implementation1).id("version1b");
        ImplementationVersion implementationVersion2 = new ImplementationVersion().implementation(implementation2).id("version2");
        ImplementationVersion implementationVersion4 = new ImplementationVersion().implementation(implementation3).id("version4");

        List<ImplementationVersion> existing = ImmutableList.of(implementationVersion1,implementationVersion1b,implementationVersion2,implementationVersion4);


        Mockito.when(resourceManager.getImplementationVersions()).thenReturn(existing);

        List<ImplementationVersion> results = underTest.getImplementationVersions(null);

        Assertions.assertEquals(0, results.size());

    }

    @Test
    public void given_implementation_list_when_get_implementationVersion_isempty_then_works() throws Exception {

        Implementation implementation1 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.JAVA);
        Implementation implementation2 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON);
        Implementation implementation3 = ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.PYTHON);

        List<Implementation> implementations = ImmutableList.of(implementation1, implementation2, implementation3);

        Mockito.when(resourceManager.getImplementationVersions()).thenReturn(new ArrayList<>());

        List<ImplementationVersion> results = underTest.getImplementationVersions(implementations);

        Assertions.assertEquals(0, results.size());

    }


    @Test
    public void when_fill_implementationVersion_with_apiVersion_then_works() throws Exception {

        ApiVersion api1 = new ApiVersion().id("api1").name("api 1");
        ApiVersion api2 = new ApiVersion().id("api2").name("api 2");

        ImplementationVersion implVersion1 = new ImplementationVersion().id("version1").apiVersion(new ApiVersion().id("api1"));
        ImplementationVersion implVersion2 = new ImplementationVersion().id("version2").apiVersion(new ApiVersion().id("api2"));

        List<ImplementationVersion> items = underTest.fillImplementationVersionWithApiVersion(ImmutableList.of(implVersion1, implVersion2),ImmutableList.of(api1, api2));

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(implVersion1,items.get(0));
        Assertions.assertEquals(implVersion2,items.get(1));

        Assertions.assertEquals(api1,items.get(0).getApiVersion());
        Assertions.assertEquals(api2,items.get(1).getApiVersion());

    }

}
