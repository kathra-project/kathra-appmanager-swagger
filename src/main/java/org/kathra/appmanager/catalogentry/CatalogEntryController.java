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
package org.kathra.appmanager.catalogentry;

import javassist.NotFoundException;
import org.apache.camel.cdi.ContextName;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.apiversion.ApiVersionService;
import org.kathra.appmanager.catalogentrypackage.CatalogEntryPackageService;
import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.model.CatalogEntryTemplate;
import org.kathra.appmanager.service.CatalogEntriesService;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.codegen.model.CodeGenTemplate;
import org.kathra.core.model.*;
import org.kathra.utils.KathraException;
import org.kathra.utils.KathraRuntimeException;

import javax.activation.FileDataSource;
import javax.inject.Named;
import java.io.File;
import java.security.InvalidParameterException;
import java.util.List;

/**
 * @author julien.boubechtoula
 */
@Named("CatalogEntriesController")
@ContextName("AppManager")
public class CatalogEntryController implements CatalogEntriesService {

    private ServiceInjection serviceInjection;
    private final CatalogEntryPackageService catalogEntryPackageService;
    private final CatalogEntryService catalogEntryService;

    public CatalogEntryController(CatalogEntryPackageService catalogEntryPackageService, CatalogEntryService catalogEntryService, SourceRepositoryService sourceRepositoryService) {
        this.catalogEntryPackageService = catalogEntryPackageService;
        this.catalogEntryService = catalogEntryService;
    }

    public CatalogEntryController() {
        serviceInjection = new ServiceInjection(new Config(), getSessionManager());
        this.catalogEntryPackageService = serviceInjection.getService(CatalogEntryPackageService.class);
        this.catalogEntryService = serviceInjection.getService(CatalogEntryService.class);
    }

    @Override
    public CatalogEntry addEntryToCatalogFromTemplate(CatalogEntryTemplate catalogEntry) throws Exception {
        return catalogEntryService.create(catalogEntry);
    }

    @Override
    public CatalogEntry deleteCatalogEntryById(String catalogEntryId) throws Exception {
        catalogEntryService.delete(getCatalogEntry(catalogEntryId));
        return new CatalogEntry().id(catalogEntryId).status(Resource.StatusEnum.DELETED);
    }

    @Override
    public List<CatalogEntry> getCatalogEntries() throws Exception {
        return catalogEntryService.getAll();
    }

    @Override
    public CatalogEntry getCatalogEntry(String catalogEntryId) throws Exception {
        return catalogEntryService.getById(catalogEntryId).orElseThrow(() -> new KathraRuntimeException("Catalog entry not found",null).errorCode(KathraRuntimeException.ErrorCode.NOT_FOUND));
    }

    @Override
    public CatalogEntryPackage getCatalogEntryPackage(String catalogEntryPackageId) throws Exception {
        return catalogEntryPackageService.getById(catalogEntryPackageId).orElseThrow(() -> new KathraRuntimeException("Catalog entry not found",null).errorCode(KathraRuntimeException.ErrorCode.NOT_FOUND));
    }

    @Override
    public CatalogEntryPackage getCatalogEntryPackageFromVersion(String catalogEntryPackageId, String version) throws Exception {
        //return catalogEntryPackageService.getVersion(getCatalogEntryPackage(catalogEntryPackageId), version);
        return null;
    }

    @Override
    public List<CatalogEntryTemplate> getCatalogEntryTemplates() throws Exception {
        return catalogEntryService.getTemplates();
    }
}
