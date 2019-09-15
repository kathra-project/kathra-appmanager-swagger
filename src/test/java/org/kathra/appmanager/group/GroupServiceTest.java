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
package org.kathra.appmanager.group;

import com.google.common.collect.ImmutableList;
import org.kathra.appmanager.service.SecurityService;
import org.kathra.core.model.Group;
import org.kathra.resourcemanager.client.GroupsClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;

/**
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class GroupServiceTest {

    GroupService underTest;

    @Mock
    GroupsClient resourceManager;
    @Mock
    SecurityService securityService;

    @BeforeEach
    public void setUp() {
        Mockito.reset(resourceManager);
        Mockito.reset(securityService);
        underTest = new GroupService(resourceManager, securityService);
    }

    @Test
    public void when_getAll_then_return_items() throws Exception {
        Group item1 = getGroup("group 1");
        Group item2 = getGroup("group 2");
        Mockito.when(resourceManager.getGroups()).thenReturn(ImmutableList.of(item1, item2));
        List<Group> items = underTest.getAll();
        Assertions.assertEquals(items.get(0), item1);
        Assertions.assertEquals(items.get(1), item2);
    }

    private Group getGroup(String s) {
        return new Group().path(s);
    }

    @Test
    public void given_user_with_group1_when_getGroupsFromCurrentUser_then_return_only_group1() throws Exception {
        Group item1 = getGroup("group 1");
        Group item2 = getGroup("group 2");
        Mockito.when(resourceManager.getGroups()).thenReturn(ImmutableList.of(item1, item2));

        Mockito.when(securityService.getUserInfo(Mockito.eq(SecurityService.UserInformation.GROUPS))).thenReturn(ImmutableList.of("group 1"));

        List<Group> items = underTest.getGroupsFromCurrentUser();
        Assertions.assertEquals(items.size(), 1);
        Assertions.assertEquals(items.get(0).getPath(), "group 1");
    }

    @Test
    public void given_path_when_findByPath_then_return_group_with_path_wanted() throws Exception {
        Group item1 = getGroup("group1");
        Group item2 = getGroup("group2");
        Mockito.when(resourceManager.getGroups()).thenReturn(ImmutableList.of(item1, item2));

        Optional<Group> item = underTest.findByPath("group1");
        Assertions.assertTrue(item.isPresent());
        Assertions.assertEquals("group1", item.get().getPath());
    }

}
