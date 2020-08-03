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

package org.kathra.appmanager.catalogentrypackage;

import org.kathra.appmanager.binaryrepository.BinaryRepositoryService;
import org.kathra.appmanager.catalogentry.CatalogEntryService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.BinaryRepository;
import org.kathra.core.model.CatalogEntry;
import org.kathra.core.model.CatalogEntryPackage;
import org.kathra.core.model.CatalogEntryPackageVersion;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;
import org.kathra.utils.Session;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class CatalogEntryUtils {

    private KathraSessionManager kathraSessionManager;
    private BinaryRepositoryService binaryRepositoryService;
    private CatalogEntryService catalogEntryService;
    private CatalogEntryPackageService catalogEntryPackageService;
    private SourceRepositoryService sourceRepositoryService;

    public CatalogEntryUtils(KathraSessionManager kathraSessionManager, BinaryRepositoryService binaryRepositoryService, CatalogEntryService catalogEntryService, CatalogEntryPackageService catalogEntryPackageService, SourceRepositoryService sourceRepositoryService) {
        this.kathraSessionManager = kathraSessionManager;
        this.binaryRepositoryService = binaryRepositoryService;
        this.catalogEntryService = catalogEntryService;
        this.catalogEntryPackageService = catalogEntryPackageService;
        this.sourceRepositoryService = sourceRepositoryService;
    }

    public CatalogEntryPackageVersion enrichWithResourceManager(CatalogEntryPackageVersion catalogEntryPackageVersion, List<CatalogEntryPackage> entriesFromResourceManager, ConcurrentHashMap<String, BinaryRepository> binaryRepositories, ConcurrentHashMap<String, CatalogEntry> catalogEntries) {
        return catalogEntryPackageVersion.catalogEntryPackage(enrichWithResourceManager(catalogEntryPackageVersion.getCatalogEntryPackage(), entriesFromResourceManager, binaryRepositories, catalogEntries));
    }

    public void tryToReconcileDbWithManager(CatalogEntryPackage catalogEntryPackageDb, CatalogEntryPackage catalogEntryPackageManager, ConcurrentHashMap<String, BinaryRepository> binaryRepositoriesCache, ConcurrentHashMap<String, CatalogEntry> catalogEntriesCache) throws ApiException {

        if (!binaryRepositoriesCache.containsKey(catalogEntryPackageDb.getBinaryRepository().getId())) {
            binaryRepositoriesCache.put(catalogEntryPackageDb.getBinaryRepository().getId(), binaryRepositoryService.getById(catalogEntryPackageDb.getBinaryRepository().getId()).get());
        }

        BinaryRepository binaryRepository = binaryRepositoriesCache.get(catalogEntryPackageDb.getBinaryRepository().getId());
        if (!catalogEntryPackageManager.getUrl().equals(binaryRepository.getUrl()))
            return;
        if (catalogEntryPackageManager.getName() != null && !catalogEntryPackageDb.getName().equals(catalogEntryPackageDb.getName()))
            return;
        if (catalogEntryPackageManager.getCatalogEntry().getName() != null) {
            CatalogEntry catalogEntryDb = catalogEntryPackageDb.getCatalogEntry();
            if (catalogEntryDb == null)
                return;
            if (!catalogEntriesCache.containsKey(catalogEntryPackageDb.getCatalogEntry().getId())) {
                catalogEntriesCache.put(catalogEntryPackageDb.getCatalogEntry().getId(), catalogEntryService.getById(catalogEntryPackageDb.getCatalogEntry().getId()).get());
            }
            catalogEntryDb = catalogEntriesCache.get(catalogEntryPackageDb.getCatalogEntry().getId());

            if (catalogEntryPackageManager.getCatalogEntry().getName().equals(catalogEntryDb.getName())) {
                catalogEntryPackageDb.setProviderId(catalogEntryPackageManager.getProviderId());
                catalogEntryPackageService.patch(new CatalogEntryPackage().id(catalogEntryPackageDb.getId()).providerId(catalogEntryPackageDb.getProviderId()));
            }
        }
    }

    public CatalogEntryPackage enrichWithResourceManager(CatalogEntryPackage catalogEntryPackage, List<CatalogEntryPackage> entriesFromResourceManager, ConcurrentHashMap<String, BinaryRepository> binaryRepositoriesCache, ConcurrentHashMap<String, CatalogEntry> catalogEntriesCache) {
        if (binaryRepositoriesCache == null)
            binaryRepositoriesCache = new ConcurrentHashMap();
        if (catalogEntriesCache == null)
            catalogEntriesCache = new ConcurrentHashMap();

        final Session session = kathraSessionManager.getCurrentSession();
        ConcurrentHashMap<String, BinaryRepository> finalBinaryRepositories = binaryRepositoriesCache;
        ConcurrentHashMap<String, CatalogEntry> finalCatalogEntries = catalogEntriesCache;
        Optional<CatalogEntryPackage> fromResourceManager = entriesFromResourceManager.parallelStream()
                .filter(Objects::nonNull)
                .filter(entry -> {
                    String providerId = entry.getProviderId();
                    kathraSessionManager.handleSession(session);
                    if (providerId == null) {
                        try {
                            // If providerId is null, try de reconcile identifiers between BinaryRepositoryManager and ResourceManager with Url and Name
                            tryToReconcileDbWithManager(entry, catalogEntryPackage, finalBinaryRepositories, finalCatalogEntries);
                            providerId = entry.getProviderId();
                        } catch (ApiException e) {
                            e.printStackTrace();
                        }
                    }
                    return providerId != null && providerId.equals(catalogEntryPackage.getProviderId());
                }).findFirst();
        if (fromResourceManager.isEmpty()) {
            return catalogEntryPackage;
        }
        catalogEntryPackage.id(fromResourceManager.get().getId())
                .binaryRepository(fromResourceManager.get().getBinaryRepository())
                .packageType(fromResourceManager.get().getPackageType())
                .createdAt(fromResourceManager.get().getCreatedAt())
                .updatedAt(fromResourceManager.get().getUpdatedAt())
                .createdBy(fromResourceManager.get().getCreatedBy())
                .updatedBy(fromResourceManager.get().getUpdatedBy())
                .pipeline(fromResourceManager.get().getPipeline())
                .provider(fromResourceManager.get().getProvider())
                .providerId(fromResourceManager.get().getProviderId())
                .catalogEntry(fromResourceManager.get().getCatalogEntry())
                .sourceRepository(fromResourceManager.get().getSourceRepository())
                .status(fromResourceManager.get().getStatus());
        if (catalogEntryPackage.getMetadata() == null) {
            catalogEntryPackage.setMetadata(new HashMap<>());
        }
        catalogEntryPackage.getMetadata().putAll(fromResourceManager.get().getMetadata());
        return catalogEntryPackage;
    }
    
}
