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
import org.kathra.appmanager.component.ComponentsController;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.utils.KathraException;
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

import javax.activation.FileDataSource;
import java.io.File;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ApiVersionsControllerTest {


    ApiVersionsController underTest;

    @Mock
    SourceRepositoryService sourceRepositoryService;

    @Mock
    ComponentService componentService;

    @Mock
    ApiVersionService apiVersionService;

    @BeforeEach
    public void setUp() {
        underTest = new ApiVersionsController(apiVersionService, componentService, sourceRepositoryService);
    }

    @Test
    public void given_existing_id_when_getApiVersion_then_works() throws Exception {
        ApiVersion apiVersionExpected = new ApiVersion().id("api-version-1");
        Mockito.doReturn(Optional.of(apiVersionExpected)).when(apiVersionService).getById(Mockito.eq("api-version-1"));
        ApiVersion apiVersion = underTest.getApiVersionById("api-version-1");
        Assertions.assertEquals(apiVersionExpected, apiVersion);
    }
    @Test
    public void given_no_existing_id_when_getApiVersion_then_throw_kathra_exception() throws Exception {
        Mockito.doReturn(Optional.empty()).when(apiVersionService).getById(Mockito.eq("api-version-1"));
        KathraException exception = assertThrows(KathraException.class, () -> {
            underTest.getApiVersionById("api-version-1");
        });
        Assertions.assertEquals("Unable to find Api Version 'api-version-1'", exception.getMessage());
    }


    @Test
    public void given_apiVersionId_when_getApiFile_then_works() throws Exception {

        SourceRepository sourceRepository1 = new SourceRepository().id("s1");
        Component component1 = new Component().id("c1").name("new component").putMetadataItem("groupPath", "group name").apiRepository(sourceRepository1);
        ApiVersion api1 = new ApiVersion().id("api1").name("api 1").component(component1).version("1.0.0-RC-SNAPSHOT");
        File file = Mockito.mock(File.class);

        Mockito.when(apiVersionService.getById("1")).thenReturn(Optional.of(api1));
        Mockito.when(componentService.getById("c1")).thenReturn(Optional.of(component1));
        Mockito.when(sourceRepositoryService.getById("s1")).thenReturn(Optional.of(sourceRepository1));
        Mockito.when(sourceRepositoryService.getFile(sourceRepository1,api1.getVersion(),"swagger.yaml")).thenReturn(file);

        FileDataSource result = underTest.getApiFile("1");

        Assertions.assertEquals(file,result.getFile());
    }

    @Test
    public void given_filesource_empty_when_updateApiVersion_then_throw_exception() throws Exception {

        InvalidParameterException exception = assertThrows(InvalidParameterException.class, () -> {
            underTest.updateApiVersion("1", null);
        });
        Assertions.assertEquals("File source is not present", exception.getMessage());
    }

    @Test
    public void given_filesource_empty_when_createApiVersion_then_throw_exception() throws Exception {

        InvalidParameterException exception = assertThrows(InvalidParameterException.class, () -> {
            underTest.createApiVersion("1", null);
        });
        Assertions.assertEquals("File source is not present", exception.getMessage());
    }

}
