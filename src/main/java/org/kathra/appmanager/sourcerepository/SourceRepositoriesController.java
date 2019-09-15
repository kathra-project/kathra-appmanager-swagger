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
package org.kathra.appmanager.sourcerepository;

import org.kathra.appmanager.Config;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.model.Commit;
import org.kathra.appmanager.service.RepositoriesService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.SourceRepository;
import org.kathra.core.model.SourceRepositoryCommit;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraException;
import org.apache.camel.cdi.ContextName;

import javax.inject.Named;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author julien.boubechtoula
 */
@Named("RepositoriesController")
@ContextName("AppManager")
public class SourceRepositoriesController implements RepositoriesService {

    private ServiceInjection serviceInjection;
    private ImplementationService implementationService;
    private SourceRepositoryService sourceRepositoryService;

    public SourceRepositoriesController(SourceRepositoryService sourceRepositoryService, ImplementationService implementationService) {
        this.implementationService = implementationService;
        this.sourceRepositoryService = sourceRepositoryService;
    }

    public SourceRepositoriesController() {
        serviceInjection = new ServiceInjection(new Config(), getSessionManager());
        this.sourceRepositoryService = serviceInjection.getService(SourceRepositoryService.class);
        this.implementationService = serviceInjection.getService(ImplementationService.class);
    }



    @Override
    public List<String> getRepositoryBranches(String sourceRepositoryId) throws Exception {
        return sourceRepositoryService.getBranchs(getSourceRepositoryImpl(sourceRepositoryId));
    }

    @Override
    public List<Commit> getRepositoryCommitsForBranch(String sourceRepositoryId, String branch) throws Exception {
        return sourceRepositoryService.getCommits(getSourceRepositoryImpl(sourceRepositoryId), branch).stream().map(commit -> map(commit)).collect(Collectors.toList());
    }

    private SourceRepository getSourceRepositoryImpl(String sourceRepositoryId) throws ApiException, KathraException {
        SourceRepository sourceRepository = sourceRepositoryService.getById(sourceRepositoryId).orElseThrow(() -> new KathraException("SourceRepository "+sourceRepositoryId+" not found", null,KathraException.ErrorCode.NOT_FOUND));
        boolean sourceRepositoryImplementationAuthorized = implementationService.getAll().stream().anyMatch(implementation -> implementation.getSourceRepository() != null && implementation.getSourceRepository().getId() != null && implementation.getSourceRepository().getId().equals(sourceRepositoryId));
        if (!sourceRepositoryImplementationAuthorized) {
            throw new KathraException("SourceRepository's implementation '"+sourceRepositoryId+"' forbidden", null,KathraException.ErrorCode.FORBIDDEN);
        }
        return sourceRepository;
    }

    private Commit map(SourceRepositoryCommit commit) {
        return new Commit().id(commit.getId()).author(commit.getAuthorName()).date(commit.getCreatedAt()).hash(commit.getId()).message(commit.getMessage());
    }
}
