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
package org.kathra.appmanager.apiversion;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.implementationversion.ImplementationVersionService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.libraryapiversion.LibraryApiVersionService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.Component;
import org.kathra.core.model.ImplementationVersion;
import org.kathra.core.model.LibraryApiVersion;
import org.kathra.resourcemanager.client.ApiVersionsClient;
import org.kathra.utils.KathraSessionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ApiVersionServiceViewTest {

    @Mock
    private ApiVersionsClient resourceManager;
    @Mock
    private ComponentService componentService;
    @Mock
    private OpenApiParser openApiParser;
    @Mock
    private SourceRepositoryService sourceRepositoryService;
    @Mock
    private LibraryApiVersionService libraryApiVersionService;
    @Mock
    private LibraryService libraryService;
    @Mock
    private KathraSessionManager kathraSessionManager;
    @Mock
    private ImplementationVersionService implementationVersionService;

    private ApiVersionService underTest;


    @BeforeEach
    public void setUp() throws Exception {

        Mockito.reset(resourceManager);
        Mockito.reset(componentService);
        Mockito.reset(openApiParser);
        Mockito.reset(libraryApiVersionService);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(libraryService);
        Mockito.reset(implementationVersionService);
        underTest = new ApiVersionService(resourceManager, componentService, openApiParser, libraryService, libraryApiVersionService, sourceRepositoryService, kathraSessionManager, implementationVersionService);
    }

    @Test
    public void given_implementationVersion_when_getApiVersion_then_works() throws Exception{
        ApiVersion api1 = new ApiVersion().id("api1").name("api 1");
        ApiVersion api2 = new ApiVersion().id("api2").name("api 2");
        ApiVersion api3 = new ApiVersion().id("api3").name("api 3");

        ImplementationVersion implVersion1 = new ImplementationVersion().id("version1").apiVersion(new ApiVersion().id("api1"));
        ImplementationVersion implVersion2 = new ImplementationVersion().id("version2").apiVersion(new ApiVersion().id("api2"));

        Mockito.when(resourceManager.getApiVersions()).thenReturn(ImmutableList.of(api1,api2,api3));

        List<ApiVersion> results = underTest.getApiVersionsForImplementationVersion(ImmutableList.of(implVersion1, implVersion2));

        Assertions.assertEquals(2, results.size());

        // Results does not contains api3
        Assertions.assertEquals(api1, results.get(0));
        Assertions.assertEquals(api2, results.get(1));

    }

    @Test
    public void given_component_list_when_get_apiversion_then_works() throws Exception {

        Component component1 = new Component().id("1");
        Component component2 = new Component().id("2");
        Component component3 = new Component().id("3");
        Component component4 = new Component().id("4");

        List<Component> components = ImmutableList.of(component1, component2, component3);

        ApiVersion apiVersion1 = new ApiVersion().component(component1).id("api1");
        ApiVersion apiVersion1b = new ApiVersion().component(component1).id("api1b");
        ApiVersion apiVersion2 = new ApiVersion().component(component2).id("api2");
        ApiVersion apiVersion4 = new ApiVersion().component(component4).id("api4");

        List<ApiVersion> existing = ImmutableList.of(apiVersion1,apiVersion1b,apiVersion2,apiVersion4);


        Mockito.when(resourceManager.getApiVersions()).thenReturn(existing);

        List<ApiVersion> results = underTest.getApiVersions(components);

        Assertions.assertEquals(3, results.size());

        // Doesn't contains apiVersion4 because component4 isn't requested
        Assertions.assertTrue(results.containsAll(ImmutableList.of(apiVersion1, apiVersion1b, apiVersion2)));
    }

    @Test
    public void given_empty_component_list_when_get_apiversion_then_works() throws Exception {

        Component component1 = new Component().id("1");
        Component component2 = new Component().id("2");
        Component component4 = new Component().id("4");

        ApiVersion apiVersion1 = new ApiVersion().component(component1).id("api1");
        ApiVersion apiVersion1b = new ApiVersion().component(component1).id("api1b");
        ApiVersion apiVersion2 = new ApiVersion().component(component2).id("api2");
        ApiVersion apiVersion4 = new ApiVersion().component(component4).id("api4");

        List<ApiVersion> existing = ImmutableList.of(apiVersion1,apiVersion1b,apiVersion2,apiVersion4);


        Mockito.when(resourceManager.getApiVersions()).thenReturn(existing);

        List<ApiVersion> results = underTest.getApiVersions(new ArrayList<>());

        Assertions.assertEquals(0, results.size());

    }

    @Test
    public void given_null_component_list_when_get_apiversion_then_works() throws Exception {

        Component component1 = new Component().id("1");
        Component component2 = new Component().id("2");
        Component component4 = new Component().id("4");

        ApiVersion apiVersion1 = new ApiVersion().component(component1).id("api1");
        ApiVersion apiVersion1b = new ApiVersion().component(component1).id("api1b");
        ApiVersion apiVersion2 = new ApiVersion().component(component2).id("api2");
        ApiVersion apiVersion4 = new ApiVersion().component(component4).id("api4");

        List<ApiVersion> existing = ImmutableList.of(apiVersion1,apiVersion1b,apiVersion2,apiVersion4);


        Mockito.when(resourceManager.getApiVersions()).thenReturn(existing);

        List<ApiVersion> results = underTest.getApiVersions(null);

        Assertions.assertEquals(0, results.size());

    }

    @Test
    public void given_component_list_when_get_apiversion_isempty_then_works() throws Exception {

        Component component1 = new Component().id("1");
        Component component2 = new Component().id("2");
        Component component3 = new Component().id("3");

        List<Component> components = ImmutableList.of(component1, component2, component3);

        Mockito.when(resourceManager.getApiVersions()).thenReturn(new ArrayList<>());

        List<ApiVersion> results = underTest.getApiVersions(components);

        Assertions.assertEquals(0, results.size());

    }


}