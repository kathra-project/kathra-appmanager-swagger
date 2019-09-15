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

import org.kathra.appmanager.component.ComponentService;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.libraryapiversion.LibraryApiVersionService;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ApiVersionsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;
import org.apache.commons.io.FileUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractApiVersionTest extends AbstractServiceTest {

    @Mock
    protected ApiVersionsClient resourceManager;
    @Mock
    protected ComponentService componentService;
    @Mock
    protected OpenApiParser openApiParser;
    @Mock
    protected SourceRepositoryService sourceRepositoryService;
    @Mock
    protected LibraryApiVersionService libraryApiVersionService;
    @Mock
    protected LibraryService libraryService;
    @Mock
    protected KathraSessionManager kathraSessionManager;

    protected ApiVersionService underTest;
    protected String COMPONENT_ID;
    protected String SOURCE_REPOSITORY_API_ID;
    protected long timeoutMax = 4000;

    protected final static String API_NAME = "apiversion's name";
    protected final static String API_VERSION = "1.0.0";

    protected final static String ARTIFACT_GROUP = "my.component.group";
    protected final static String ARTIFACT_NAME = "componentname";

    protected ApiVersion apiVersionDb;

    protected String API_VERSION_ID = null;
    protected Map<String,LibraryApiVersion> libraryApiVersionsDb = null;


    Logger logger = LoggerFactory.getLogger(AbstractApiVersionTest.class);

    protected void resetMock() throws ApiException {
        API_VERSION_ID = "api-version-id-requested "+UUID.randomUUID().toString();
        COMPONENT_ID = "component-id-existing"+UUID.randomUUID().toString();
        SOURCE_REPOSITORY_API_ID = "sourceRepositoryApi-id-existing"+UUID.randomUUID().toString();

        Mockito.reset(resourceManager);
        Mockito.reset(componentService);
        Mockito.reset(openApiParser);
        Mockito.reset(libraryApiVersionService);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(libraryService);
        underTest = new ApiVersionService(resourceManager, componentService, openApiParser, libraryService, libraryApiVersionService, sourceRepositoryService, kathraSessionManager);

        Mockito.doAnswer(invocationOnMock -> {
            String id = invocationOnMock.getArgument(0);
            logger.info("libraryApiVersionServiceMock - getById('" + id +"') -> exist = " + libraryApiVersionsDb.containsKey(id));
            return (libraryApiVersionsDb.containsKey(id)) ? Optional.of(copy(libraryApiVersionsDb.get(id))) : Optional.empty();
        }).when(libraryApiVersionService).getById(Mockito.anyString());

        Mockito.doAnswer(invocationOnMock -> Optional.of(getComponent())).when(componentService).getById(COMPONENT_ID);
        Mockito.when(openApiParser.getApiVersionFromApiFile(Mockito.any())).thenReturn(getApiVersionFromFile());

        apiVersionDb = getApiVersion();
        libraryApiVersionsDb = new ConcurrentHashMap();
        callback = Mockito.mock(Runnable.class);
    }

    public ApiVersion getApiVersionFromFile() {
        return new ApiVersion().version(API_VERSION).name(API_NAME).putMetadataItem(ApiVersionService.METADATA_API_ARTIFACT_NAME, ARTIFACT_NAME).putMetadataItem(ApiVersionService.METADATA_API_GROUP_ID, ARTIFACT_GROUP);
    }

    public ApiVersion getApiVersion() {
        ApiVersion apiVersion = getApiVersionFromFile()
                .id(API_VERSION_ID)
                .released(false)
                .status(Resource.StatusEnum.READY)
                .component(getComponent());
        return apiVersion.librariesApiVersions(getLibrariesApiVersions(apiVersion));
    }

    private List<LibraryApiVersion> getLibrariesApiVersions(ApiVersion apiVersion) {
        List<LibraryApiVersion> items = new ArrayList<>();
        for(Library.LanguageEnum lang : Library.LanguageEnum.values()) {
            for(Library.TypeEnum type : Library.TypeEnum.values()) {
                items.add(new LibraryApiVersion()
                        .id(UUID.randomUUID().toString())
                        .library(getLibrary(apiVersion.getComponent(), lang, type))
                        .apiVersion(apiVersion).status(Resource.StatusEnum.READY)
                        .pipelineStatus(LibraryApiVersion.PipelineStatusEnum.READY)
                        .apiRepositoryStatus(LibraryApiVersion.ApiRepositoryStatusEnum.READY)
                        .status(Resource.StatusEnum.READY));
            }
        }
        return items;
    }

    public Component getComponent() {
        SourceRepository apiRepository = new SourceRepository().id(SOURCE_REPOSITORY_API_ID).path("/kathra-project/component-name/api").status(Resource.StatusEnum.READY);
        Component component = new Component().name("component-name")
                .id(COMPONENT_ID)
                .status(Resource.StatusEnum.READY)
                .apiRepository(apiRepository)
                .putMetadataItem(ComponentService.METADATA_GROUP_ID, "group_id");

        try {
            Mockito.when(sourceRepositoryService.getById(Mockito.eq(apiRepository.getId()))).thenReturn(Optional.of(apiRepository));
        } catch (ApiException e) {
            e.printStackTrace();
        }


        for(Library.LanguageEnum lang : Library.LanguageEnum.values()) {
            for(Library.TypeEnum type : Library.TypeEnum.values()) {
                component.addLibrariesItem(getLibrary(component, lang, type));
            }
        }
        return component;
    }

    private Library getLibrary(Component component, Asset.LanguageEnum language, Library.TypeEnum type) {
        Pipeline pipeline = new Pipeline().id(UUID.randomUUID().toString()).status(Resource.StatusEnum.READY);
        SourceRepository sourceRepository = new SourceRepository().id(UUID.randomUUID().toString()).status(Resource.StatusEnum.READY);
        Library library = new Library() .component(component)
                .language(language)
                .type(type)
                .id("library_id_"+language+"_"+type)
                .status(Resource.StatusEnum.READY)
                .pipeline(pipeline)
                .sourceRepository(sourceRepository);
        try {
            Mockito.doAnswer(invocationOnMock -> Optional.of(sourceRepository)).when(sourceRepositoryService).getById(Mockito.eq(sourceRepository.getId()));
            Mockito.when(libraryService.getById(Mockito.eq(library.getId()))).thenReturn(Optional.of(copy(library)));
        } catch (ApiException e) {
            e.printStackTrace();
        }
        return library;
    }

    protected File getApiFile() throws IOException {
        File fileApi = new File(System.getProperty("java.io.tmpdir")+File.separator+"swagger.json");
        FileUtils.touch(fileApi);
        return fileApi;
    }

    public void mockLibraryApiVersionBuildWithException() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(libraryApiVersionService).build(Mockito.any(), Mockito.any());
    }

    public void mockUpdateSwaggerFileIntoApiRepository() throws ApiException {
        SourceRepositoryCommit result = new SourceRepositoryCommit();
        result.setId(UUID.randomUUID().toString());
        Mockito.doReturn(result).when(sourceRepositoryService).commitFileAndTag(Mockito.any(),
                Mockito.eq("dev"),
                Mockito.any(),
                Mockito.eq("swagger.yaml"),
                Mockito.any());

    }

    public void mockUpdateSwaggerFileIntoApiRepositoryWithException() throws ApiException {
        SourceRepositoryCommit result = new SourceRepositoryCommit();
        result.setId(UUID.randomUUID().toString());
        Mockito.doAnswer(invocationOnMock -> {Thread.sleep(500); throw new ApiException("error");}).when(sourceRepositoryService).commitFileAndTag(Mockito.any(),
                Mockito.eq("dev"),
                Mockito.argThat(apiFile -> apiFile.getName().equals("swagger.yaml")),
                Mockito.eq("swagger.yaml"),
                Mockito.any());
    }

    public void mockLibraryApiVersionBuild(long buildingDuration) throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            final LibraryApiVersion libraryApiVersion = invocationOnMock.getArgument(0);
            final Build buildScheduled = new Build().buildNumber(UUID.randomUUID().toString()).status(Build.StatusEnum.SCHEDULED);
            libraryApiVersion.setPipelineStatus(LibraryApiVersion.PipelineStatusEnum.PENDING);
            mockGetApiVersionLibrary(libraryApiVersion);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(buildingDuration);
                    libraryApiVersion.setPipelineStatus(LibraryApiVersion.PipelineStatusEnum.READY);
                    libraryApiVersion.setStatus(Resource.StatusEnum.READY);
                    mockGetApiVersionLibrary(libraryApiVersion);
                    ((Runnable)invocationOnMock.getArgument(1)).run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return buildScheduled;
        }).when(libraryApiVersionService).build(Mockito.any(), Mockito.any());
    }

    public void mockGetApiVersionLibrary(LibraryApiVersion libraryApiVersion) {
        libraryApiVersionsDb.put(libraryApiVersion.getId(), libraryApiVersion);
    }

    public void mockLibraryApiVersionBuildWithError(long buildingDuration) throws Exception {
        Mockito.doAnswer(invocationOnMock -> {
            final LibraryApiVersion libraryApiVersion = invocationOnMock.getArgument(0);
            final Build buildScheduled = new Build().buildNumber(UUID.randomUUID().toString()).status(Build.StatusEnum.SCHEDULED);
            libraryApiVersion.setPipelineStatus(LibraryApiVersion.PipelineStatusEnum.PENDING);
            mockGetApiVersionLibrary(libraryApiVersion);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(buildingDuration);
                    libraryApiVersion.setPipelineStatus(LibraryApiVersion.PipelineStatusEnum.ERROR);;
                    libraryApiVersion.setStatus(Resource.StatusEnum.READY);
                    mockGetApiVersionLibrary(libraryApiVersion);
                    ((Runnable)invocationOnMock.getArgument(1)).run();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            return buildScheduled;
        }).when(libraryApiVersionService).build(Mockito.any(), Mockito.any());
    }

    public void mockUpdateApiVersion() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            ApiVersion apiVersion = invocationOnMock.getArgument(1);
            apiVersionDb .name(apiVersion.getName())
                    .version(apiVersion.getVersion())
                    .component(apiVersion.getComponent())
                    .librariesApiVersions(apiVersion.getLibrariesApiVersions())
                    .apiRepositoryStatus(apiVersion.getApiRepositoryStatus())
                    .status(apiVersion.getStatus())
                    .metadata(apiVersion.getMetadata())
                    .released(apiVersion.getReleased())
                    .implementationsVersions(apiVersion.getImplementationsVersions());

            Mockito.doReturn(copy(apiVersionDb)).when(resourceManager).getApiVersion(Mockito.anyString());
            return copy(apiVersionDb);
        }).when(resourceManager).updateApiVersion(Mockito.eq(API_VERSION_ID), Mockito.any());

    }
    public void mockPatchApiVersion() throws ApiException {
        Mockito.doAnswer(invocationOnMock -> {
            ApiVersion apiVersion = invocationOnMock.getArgument(1);
            if (apiVersion.getVersion() != null) {
                apiVersionDb.setName(apiVersion.getVersion());
            }
            if (apiVersion.getName() != null) {
                apiVersionDb.setName(apiVersion.getName());
            }
            if (apiVersion.getComponent() != null) {
                apiVersionDb.setComponent(apiVersion.getComponent());
            }
            if (apiVersion.getLibrariesApiVersions() != null) {
                apiVersionDb.setLibrariesApiVersions(apiVersion.getLibrariesApiVersions());
            }
            if (apiVersion.getImplementationsVersions() != null) {
                apiVersionDb.setImplementationsVersions(apiVersion.getImplementationsVersions());
            }
            if (apiVersion.getStatus() != null) {
                apiVersionDb.setStatus(apiVersion.getStatus());
            }
            if (apiVersion.getReleased() != null) {
                apiVersionDb.setReleased(apiVersion.getReleased());
            }
            if (apiVersion.getApiRepositoryStatus() != null) {
                apiVersionDb.setApiRepositoryStatus(apiVersion.getApiRepositoryStatus());
            }
            if (apiVersion.getMetadata() != null) {
                apiVersionDb.metadata(apiVersion.getMetadata());
            }
            Mockito.doReturn(copy(apiVersionDb)).when(resourceManager).getApiVersion(Mockito.anyString());
            return copy(apiVersionDb);
        }).when(resourceManager).updateApiVersionAttributes(Mockito.eq(API_VERSION_ID), Mockito.any());
    }

    public LibraryApiVersion copy(LibraryApiVersion libraryApiVersion) {
        return new LibraryApiVersion().id(libraryApiVersion.getId())
                .apiVersion(libraryApiVersion.getApiVersion())
                .name(libraryApiVersion.getName())
                .library(libraryApiVersion.getLibrary())
                .apiRepositoryStatus(libraryApiVersion.getApiRepositoryStatus())
                .pipelineStatus(libraryApiVersion.getPipelineStatus())
                .status(libraryApiVersion.getStatus())
                .metadata(libraryApiVersion.getMetadata());
    }

    public Library copy(Library library) {
        return new Library().id(library.getId())
                .component(library.getComponent())
                .type(library.getType())
                .language(library.getLanguage())
                .pipeline(library.getPipeline())
                .sourceRepository(library.getSourceRepository())
                .status(library.getStatus())
                .metadata(library.getMetadata());
    }

    public ApiVersion copy(ApiVersion apiVersion) {
        return new ApiVersion().id(apiVersion.getId())
                .version(apiVersion.getVersion())
                .name(apiVersion.getName())
                .component(apiVersion.getComponent())
                .librariesApiVersions(apiVersion.getLibrariesApiVersions())
                .apiRepositoryStatus(apiVersion.getApiRepositoryStatus())
                .status(apiVersion.getStatus())
                .metadata(apiVersion.getMetadata())
                .released(apiVersion.getReleased())
                .implementationsVersions(apiVersion.getImplementationsVersions());
    }


}
