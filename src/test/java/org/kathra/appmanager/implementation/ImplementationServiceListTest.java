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
import org.kathra.core.model.Asset;
import org.kathra.core.model.Component;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.ImplementationVersion;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

/**
 * Test ImplementationService
 *
 * @author quentin.semanne
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class ImplementationServiceListTest extends ImplementationServiceTest {
    @Test
    public void given_nominal_args_when_creating_implementation_then_works() throws Exception {

        //assertThat("The response HTTP Status Code is 201(Created)", underTest.addBinaryRepository(repoToCreate).getStatusCode(), is(201));
        //assertThat("The response informs the resource have been created", underTest.addBinaryRepository(repoToCreate).getData(), is(""));
    }

    @Test
    public void when_getAll_then_works() throws Exception {
        Implementation implem1 = generateImplementationExample(Implementation.LanguageEnum.JAVA);
        Implementation implem2 = generateImplementationExample(Implementation.LanguageEnum.PYTHON);

        Mockito.when(resourceManager.getImplementations()).thenReturn(ImmutableList.of(implem1, implem2));
        List<Implementation> items = underTest.getAll();

        Assertions.assertTrue(items.size()==2);
        Assertions.assertEquals(items.get(0), implem1);
        Assertions.assertEquals(items.get(1), implem2);
    }

    @Test
    public void when_getAll_then_return_empty() throws Exception {

        Mockito.when(resourceManager.getImplementations()).thenReturn(new ArrayList<>());
        List<Implementation> items = underTest.getAll();

        Assertions.assertTrue(items.isEmpty());

    }

    @Test
    public void given_componentId_when_get_then_works() throws Exception {

        Implementation implem1 = generateImplementationExample(Implementation.LanguageEnum.JAVA).component(new Component().id("c1"));
        Implementation implem2 = generateImplementationExample(Implementation.LanguageEnum.PYTHON).component(new Component().id("c1"));
        Implementation implem3 = generateImplementationExample(Implementation.LanguageEnum.JAVA).component(new Component().id("c2"));
        Implementation implem4 = generateImplementationExample(Implementation.LanguageEnum.PYTHON).component(new Component().id("c2"));

        Mockito.when(resourceManager.getImplementations()).thenReturn(ImmutableList.of(implem1,implem2,implem3,implem4));
        List<Implementation> items = underTest.getComponentImplementations("c1");

        Assertions.assertEquals(2, items.size());
        Assertions.assertEquals(implem1, items.get(0));
        Assertions.assertEquals(implem2, items.get(1));

    }

    @Test
    public void when_fill_implementation_with_version_then_works() throws Exception {

        Implementation implem1 = generateImplementationExample(Implementation.LanguageEnum.JAVA).id("1");
        Implementation implem2 = generateImplementationExample(Implementation.LanguageEnum.PYTHON).id("2");

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
