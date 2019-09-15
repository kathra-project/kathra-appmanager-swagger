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

import org.kathra.appmanager.Config;
import org.kathra.appmanager.service.AbstractResourceService;
import org.kathra.appmanager.service.SecurityService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.Group;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.kathra.utils.KathraSessionManager;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author julien.boubechtoula
 */
public class GroupService extends AbstractResourceService<Group> {

    private GroupsClient resourceManager;
    private SecurityService securityService;

    public GroupService() {

    }

    public void configure(ServiceInjection serviceInjection) {
        this.resourceManager = new GroupsClient(serviceInjection.getConfig().getResourceManagerUrl(), serviceInjection.getSessionManager());
        this.securityService = new SecurityService(serviceInjection.getSessionManager());
    }

    public GroupService(GroupsClient resourceManager, SecurityService securityService) {
        this.resourceManager = resourceManager;
        this.securityService = securityService;
    }

    public GroupService(Config config, KathraSessionManager sessionManager) {
        this.resourceManager = new GroupsClient(config.getResourceManagerUrl(), sessionManager);
    }

    @Override
    protected void patch(Group object) throws ApiException {
        this.resourceManager.updateGroupAttributes(object.getId(), object);
    }

    @Override
    public Optional<Group> getById(String id) throws ApiException {
        return Optional.empty();
    }

    @Override
    public List<Group> getAll() throws ApiException {
        return resourceManager.getGroups();
    }

    public Optional<Group> findByPath(String path) throws ApiException {
        return resourceManager.getGroups().parallelStream().filter(group -> group.getPath().equals(path)).findFirst();
    }

    public List<Group> getGroupsFromCurrentUser() throws KathraException {
        try {
            List<String> groupsFromToken = (List<String>) securityService.getUserInfo(SecurityService.UserInformation.GROUPS);
            List<Group> groupsExisting = resourceManager.getGroups();
            groupsFromToken.stream().filter(groupToken -> groupsExisting.stream().noneMatch(group -> group.getPath().equals(groupToken))).forEach(groupToken ->
                logger.warn("Group '" + groupToken + "' from token no existing in db"));
            return groupsExisting.stream().filter(group -> groupsFromToken.contains(group.getPath())).collect(Collectors.toList());
        } catch (Exception e) {
            super.logger.error("Error getMyGroups", e);
            throw new KathraException("Error getMyGroups", e, KathraException.ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
