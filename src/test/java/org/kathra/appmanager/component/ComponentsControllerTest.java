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
package org.kathra.appmanager.component;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.component.ComponentsController;
import org.kathra.core.model.ApiVersion;
import org.kathra.core.model.Component;
import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import javassist.NotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ComponentsControllerTest {

    private String COMPONENT_ID = "uuiddddd";

    ComponentsController underTest;

    @Mock
    ComponentService componentService;

    @Mock
    ApiVersionService apiVersionService;

    @BeforeEach
    public void setUp() {
        underTest = new ComponentsController(componentService, apiVersionService);
    }

    @Test
    public void given_id_when_get_then_return_component() throws Exception {

        Component returnedByService = new Component().id(COMPONENT_ID).status(Resource.StatusEnum.PENDING).name("new component").putMetadataItem("groupPath", "group name");
        Mockito.when(componentService.getById(returnedByService.getId())).thenReturn(Optional.of(returnedByService));

        ApiVersion apiVersion1 = new ApiVersion().component(returnedByService).id("api1");
        ApiVersion apiVersion1b = new ApiVersion().component(returnedByService).id("api1b");

        Mockito.when(apiVersionService.getApiVersions(ImmutableList.of(returnedByService))).thenReturn(ImmutableList.of(apiVersion1, apiVersion1b));

        Component component =  underTest.getComponentById(returnedByService.getId());

        Assertions.assertEquals(returnedByService, component);

        Assertions.assertEquals(2,component.getVersions().size());

        Assertions.assertEquals(apiVersion1,component.getVersions().get(0));
        Assertions.assertEquals(apiVersion1b,component.getVersions().get(1));

    }

    @Test
    public void given_id_when_get_then_return_component_without_apiVersions() throws Exception {
        Component returnedByService = new Component().id(COMPONENT_ID).status(Resource.StatusEnum.PENDING).name("new component").putMetadataItem("groupPath", "group name");

        Mockito.when(componentService.getById(returnedByService.getId())).thenReturn(Optional.of(returnedByService));
        Mockito.when(apiVersionService.getApiVersions(ImmutableList.of(returnedByService))).thenReturn(new ArrayList<>());

        Component component =  underTest.getComponentById(returnedByService.getId());
        Assertions.assertEquals(returnedByService, component);

        Assertions.assertEquals(0,component.getVersions().size());

    }

    @Test
    public void given_id_when_get_then_throw_exception() throws Exception {
        Component returnedByService = new Component().id(COMPONENT_ID).status(Resource.StatusEnum.PENDING).name("new component").putMetadataItem("groupPath", "group name");

        Mockito.when(componentService.getById(returnedByService.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            underTest.getComponentById(returnedByService.getId());
        });

    }

    @Test
    public void given_component_when_create_then_return_component_with_id() throws Exception {

        Component input = new Component().name("new component").title("title1").description("desc1").putMetadataItem("groupPath", "group name");

        Component returnedByService = new Component().id(COMPONENT_ID).status(Resource.StatusEnum.PENDING).name("new component").title("title1").description("desc1").putMetadataItem("groupPath", "group name");

        Mockito.when(componentService.create(Mockito.argThat(component -> component.getName().equals("new component")), Mockito.eq("group name"))).thenReturn(returnedByService);

        Component result = underTest.createComponent(input);

        Assertions.assertEquals(COMPONENT_ID, result.getId());
        Assertions.assertEquals(Resource.StatusEnum.PENDING, result.getStatus());
        Assertions.assertNotNull(result.getTitle());
        Assertions.assertNotNull(result.getDescription());
        Assertions.assertEquals("group name", result.getMetadata().get("groupPath"));

    }

    @Test
    public void given_component_without_groupPath_when_create_then_throw_exception() {

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            Component input = new Component().name("new component").title("title1").description("desc1").putMetadataItem("groupPath", "");

            Component returnedByService = new Component().id(COMPONENT_ID).status(Resource.StatusEnum.PENDING).name("new component").putMetadataItem("groupPath", "group name");

            Mockito.when(componentService.create(Mockito.argThat(component -> component.getName().equals("new component")), Mockito.eq("group name"))).thenReturn(returnedByService);

            Component result = underTest.createComponent(input);

            Assertions.assertEquals(COMPONENT_ID, result.getId());
            Assertions.assertEquals(Resource.StatusEnum.PENDING, result.getStatus());
            Assertions.assertEquals("group name", result.getMetadata().get("groupPath"));
        });
        Assertions.assertEquals("Metadata groupPath should be defined into Component", exception.getMessage());
    }


    @Test
    public void when_getAll_then_return_items() throws Exception {

        Component item1 = new Component().name("new component 1").title("title1").description("desc1").putMetadataItem("groupPath", "group name");
        Component item2 = new Component().name("new component 2").title("title2").description("desc2").putMetadataItem("groupPath", "group name");
        Mockito.when(componentService.getAllComponentsWithApiVersions()).thenReturn(ImmutableList.of(item1, item2));
        List<Component> items = underTest.getComponents();

        Assertions.assertEquals(items.get(0), item1);
        Assertions.assertEquals(items.get(1), item2);
    }


}
