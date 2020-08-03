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
package org.kathra.appmanager.apiversion;

import com.google.common.collect.ImmutableMap;
import org.kathra.core.model.ApiVersion;
import io.swagger.models.Info;
import io.swagger.models.Swagger;
import io.swagger.parser.SwaggerParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.util.Map;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class OpenApiParserTest {

    OpenApiParser underTest;

    @Mock
    SwaggerParser swaggerParser;


    @BeforeEach
    public void setUp() {
        Mockito.reset(swaggerParser);
        underTest = new OpenApiParser(swaggerParser);
    }

    @Test
    public void given_api_file_when_parse_then_return_excepted_information() {

        File file = Mockito.mock(File.class);
        Mockito.doReturn("/file.txt").when(file).getPath();

        Swagger fileInfo = Mockito.mock(Swagger.class);
        Mockito.when(swaggerParser.read(Mockito.eq(file.getPath()))).thenReturn(fileInfo);

        Info info = Mockito.mock(Info.class);
        Mockito.doReturn("1.2.0").when(info).getVersion();
        Mockito.doReturn("My Component").when(info).getTitle();
        Mockito.doReturn(info).when(fileInfo).getInfo();

        Map<String, Object> vendorExt = ImmutableMap.of("x-groupId", "my.group.component", "x-artifactName", "mycomponent");
        Mockito.doReturn(vendorExt).when(info).getVendorExtensions();

        ApiVersion result = underTest.getApiVersionFromApiFile(file);

        Assertions.assertEquals("My Component", result.getName());
        Assertions.assertEquals("1.2.0", result.getVersion());
        Assertions.assertEquals("mycomponent", result.getMetadata().get(ApiVersionService.METADATA_API_ARTIFACT_NAME));
        Assertions.assertEquals("my.group.component", result.getMetadata().get(ApiVersionService.METADATA_API_GROUP_ID));

    }
}
