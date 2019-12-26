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
import org.kathra.appmanager.group.GroupService;
import org.kathra.appmanager.implementation.ImplementationService;
import org.kathra.appmanager.implementation.ImplementationServiceTest;
import org.kathra.appmanager.library.LibraryService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryService;
import org.kathra.appmanager.sourcerepository.SourceRepositoryServiceAbstractTest;
import org.kathra.core.model.*;
import org.kathra.resourcemanager.client.ComponentsClient;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test ComponentService
 *
 * @author julien.boubechtoula
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Execution(ExecutionMode.SAME_THREAD)
public class ComponentServiceTest {
    Logger logger = LoggerFactory.getLogger(ComponentServiceTest.class);


    protected ComponentService underTest;

    @Mock
    protected ComponentsClient resourceManager;
    @Mock
    protected SourceRepositoryService sourceRepositoryService;
    @Mock
    protected LibraryService libraryService;
    @Mock
    protected ApiVersionService apiVersionService;
    @Mock
    protected GroupService groupService;
    @Mock
    protected KathraSessionManager kathraSessionManager;
    @Mock
    protected ImplementationService implementationService;


    public static String COMPONENT_ID = "component-id";
    private static String COMPONENT_NAME = "component-name";
    private static String GROUP_PATH = "group-path";
    private static String GROUP_ID = "group-identifier";
    private static String REPOSITORY_ID = "repository-api-id";
    private static final String API_REPOSITORY_PATH = GROUP_PATH+"/components/"+COMPONENT_NAME+"/api";


    Component component;
    Component componentInDb;

    @BeforeEach
    public void setUp() {
        Mockito.reset(resourceManager);
        Mockito.reset(sourceRepositoryService);
        Mockito.reset(libraryService);
        Mockito.reset(groupService);
        Mockito.reset(apiVersionService);
        Mockito.reset(implementationService);
        underTest = new ComponentService(resourceManager, sourceRepositoryService, apiVersionService, libraryService, groupService, kathraSessionManager, implementationService);

        component = getComponent();
        componentInDb = getComponentWithId();
        componentInDb.status(Resource.StatusEnum.PENDING);
        try {
            Mockito.when(groupService.findByPath(GROUP_PATH)).thenReturn(Optional.of(new Group().path(GROUP_PATH).id(GROUP_ID)));
        } catch (ApiException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void given_id_when_getById_then_return_item() throws Exception {
        Component item1 = new Component().name("new component 1").putMetadataItem("groupPath", "group name").id("1");
        Mockito.when(resourceManager.getComponent(item1.getId())).thenReturn((item1));

        Optional<Component> component =  underTest.getById(item1.getId());

        Assertions.assertEquals(item1, component.get());

    }

    @Test
    public void given_unexist_id_when_getById_then_return_exception() throws Exception {
        Component item1 = new Component().name("new component 1").putMetadataItem("groupPath", "group name").id("1");
        Mockito.when(resourceManager.getComponent(item1.getId())).thenThrow(new ApiException());

        assertThrows(ApiException.class, () -> underTest.getById(item1.getId()));

    }

    @Test
    public void when_getAllWithApiversion_then_return_items() throws Exception {

        Component item1 = new Component().name("new component 1").putMetadataItem("groupPath", "group name").id("1");
        Component item2 = new Component().name("new component 2").putMetadataItem("groupPath", "group name").id("2");
        Mockito.when(resourceManager.getComponents()).thenReturn(ImmutableList.of(item1, item2));
        Mockito.when(apiVersionService.getApiVersions(Mockito.anyList())).thenReturn(new ArrayList<>());
        List<Component> items = underTest.getAll();

        Assertions.assertEquals(item1, items.get(0));
        Assertions.assertEquals(item2, items.get(1));
    }

    @Test
    public void when_getAll_then_return_items() throws Exception {

        Component item1 = new Component().name("new component 1").putMetadataItem("groupPath", "group name").id("1");
        Component item2 = new Component().name("new component 2").putMetadataItem("groupPath", "group name").id("2");
        Mockito.when(resourceManager.getComponents()).thenReturn(ImmutableList.of(item1, item2));
        List<Component> items = underTest.getAll();

        Assertions.assertEquals(item1, items.get(0));
        Assertions.assertEquals(item2, items.get(1));
    }

    @Test
    public void when_getAllWithApiversion_then_return_items_with_apiVersion() throws Exception {

        Component item1 = new Component().name("new component 1").putMetadataItem("groupPath", "group name").id("1");
        Component item2 = new Component().name("new component 2").putMetadataItem("groupPath", "group name").id("2");

        Mockito.when(resourceManager.getComponents()).thenReturn(ImmutableList.of(item1, item2));

        ApiVersion apiVersion1 = new ApiVersion().component(item1).id("api1");
        ApiVersion apiVersion1b = new ApiVersion().component(item1).id("api1b");
        ApiVersion apiVersion2 = new ApiVersion().component(item2).id("api2");

        Mockito.when(apiVersionService.getApiVersions(ImmutableList.of(item1, item2))).thenReturn(ImmutableList.of(apiVersion1,apiVersion1b,apiVersion2));
        List<Component> items = underTest.getAllComponentsWithApiVersions();

        Assertions.assertEquals(items.get(0), item1);
        Assertions.assertEquals(items.get(1), item2);

        Assertions.assertEquals(2, items.get(0).getVersions().size());
        Assertions.assertEquals(1, items.get(1).getVersions().size());

        Assertions.assertTrue(items.get(0).getVersions().contains(apiVersion1));
        Assertions.assertTrue(items.get(0).getVersions().contains(apiVersion1b));
        Assertions.assertTrue(items.get(1).getVersions().contains(apiVersion2));

    }

    @Test
    public void when_getAllWithApiversion_then_return_empty_list() throws Exception {

        Mockito.when(resourceManager.getComponents()).thenReturn(new ArrayList<>());

        List<Component> items = underTest.getAll();

        Assertions.assertEquals(0, items.size());
    }

    @Test
    public void given_component_with_groupId_when_create_and_wait_until_ready_then_component_is_ready() throws Exception {

        mockAddGetPatchComponent(component, componentInDb);

        // mock source repository creation
        mockSourceRepositoryCreation(componentInDb,1000);
        // mock library creation
        mockLibrariesCreation(componentInDb, 500);

        assertPartialComponent(component, underTest.create(component, GROUP_PATH, getCallBackIfReady()));

        Thread.sleep(3000);

        // check component READY
        Component result = underTest.getById(COMPONENT_ID).get();
        assertFullComponent(component, result);
    }

    private Runnable getCallBackIfReady(){
        return () -> {logger.info("Component is ready");};
    }

    @Test
    public void given_component_with_groupId_when_create_and_dont_wait_until_ready_then_component_is_pending() throws Exception {

        mockAddGetPatchComponent(component, componentInDb);

        // mock source repository creation
        mockSourceRepositoryCreation(componentInDb,1000);
        // mock library creation
        mockLibrariesCreation(componentInDb, 500);

        assertPartialComponent(component, underTest.create(component, GROUP_PATH, getCallBackIfReady()));

        Thread.sleep(1000);

        // check component READY
        Component result = underTest.getById(COMPONENT_ID).get();
        Assertions.assertEquals(Resource.StatusEnum.PENDING,result.getStatus());
    }

    @Test
    public void given_an_error_occurred_during_call_create_repositoryService_when_create_then_component_has_status_error() throws Exception {

        mockAddGetPatchComponent(component, componentInDb);

        // mock source repository creation
        mockSourceRepositoryCreationWithException(componentInDb,1000 , new ApiException("SourceRepositoryException"));
        // mock library creation
        mockLibrariesCreation(componentInDb, 500);

        assertPartialComponent(component, underTest.create(component, GROUP_PATH, getCallBackIfReady()));

        Thread.sleep(2000);

        // check component READY
        Component result = underTest.getById(COMPONENT_ID).get();
        Assertions.assertEquals(Resource.StatusEnum.ERROR,result.getStatus());
    }

    @Test
    public void given_repositoryService_w_status_error_when_create_then_component_has_status_error() throws Exception {

        mockAddGetPatchComponent(component, componentInDb);

        // mock source repository creation
        mockSourceRepositoryCreation(componentInDb,1000, Resource.StatusEnum.ERROR);
        // mock library creation
        mockLibrariesCreation(componentInDb, 500);

        assertPartialComponent(component, underTest.create(component, GROUP_PATH, getCallBackIfReady()));

        Thread.sleep(2000);

        // check component READY
        Component result = underTest.getById(COMPONENT_ID).get();
        Assertions.assertEquals(Resource.StatusEnum.ERROR,result.getStatus());
    }

    @Test
    public void given_library_w_status_error_when_create_then_component_has_status_error() throws Exception {

        mockAddGetPatchComponent(component, componentInDb);

        // mock source repository creation
        mockSourceRepositoryCreation(componentInDb,1000);
        // mock library creation
        mockLibrariesCreation(componentInDb, 500, Resource.StatusEnum.ERROR);

        assertPartialComponent(component, underTest.create(component, GROUP_PATH, getCallBackIfReady()));

        Thread.sleep(2000);

        // check component READY
        Component result = underTest.getById(COMPONENT_ID).get();
        Assertions.assertEquals(Resource.StatusEnum.ERROR,result.getStatus());
    }

    @Test
    public void given_an_error_occurred_during_call_create_libraryService_when_create_then_component_has_status_error() throws Exception {

        mockAddGetPatchComponent(component, componentInDb);

        // mock source repository creation
        mockSourceRepositoryCreation(componentInDb,1000);
        // mock library creation
        mockLibrariesCreationWithException(componentInDb, 500, new ApiException("LibraryService error"));

        assertPartialComponent(component, underTest.create(component, GROUP_PATH, getCallBackIfReady()));

        Thread.sleep(2000);

        // check component READY
        Component result = underTest.getById(COMPONENT_ID).get();
        Assertions.assertEquals(Resource.StatusEnum.ERROR,result.getStatus());
    }

    private void mockAddGetPatchComponent(Component component, Component componentInDb) throws ApiException {
        final Component componentCreatedInDb = getComponentWithId();
        componentCreatedInDb.status(Resource.StatusEnum.PENDING);
        // mock component creation
        Mockito.when(resourceManager.addComponent(Mockito.argThat(c -> c.getName().equals(component.getName())), Mockito.eq(GROUP_PATH))).thenReturn(componentCreatedInDb);

        // mock get component
        mockGetComponent(componentInDb);
        // mock patch component
        mockPatchComponent(componentInDb);
    }

    private void assertFullComponent(Component excepted, Component result) {
        Assertions.assertNotNull(result);
        Assertions.assertEquals(excepted.getName(), result.getName());
        Assertions.assertEquals(excepted.getDescription(), result.getDescription());
        Assertions.assertEquals(excepted.getTitle(), result.getTitle());
        Assertions.assertEquals(COMPONENT_ID, result.getId());
        Assertions.assertEquals(GROUP_ID, result.getMetadata().get(ComponentService.METADATA_GROUP_ID));
        Assertions.assertEquals(GROUP_PATH, result.getMetadata().get(ComponentService.METADATA_GROUP_PATH));
        Assertions.assertEquals(Resource.StatusEnum.READY, result.getStatus());

        Assertions.assertNotNull(result.getApiRepository());
        Assertions.assertEquals(Library.LanguageEnum.values().length * Library.TypeEnum.values().length, result.getLibraries().size());
        Assertions.assertEquals(REPOSITORY_ID, result.getApiRepository().getId());
        Assertions.assertEquals(API_REPOSITORY_PATH, result.getApiRepository().getPath());

        for(Library.LanguageEnum lang : Library.LanguageEnum.values()) {
            for(Library.TypeEnum type : Library.TypeEnum.values()) {
                Assertions.assertEquals(true, result.getLibraries().stream().anyMatch(lib ->   lib.getName().equals(COMPONENT_NAME) &&
                                                                                                        lib.getType().equals(type) &&
                                                                                                        lib.getLanguage().equals(lang)));
            }
        }
    }

    private void assertPartialComponent(Component component, Component resultPartial) throws ApiException {
        // check partial component
        Mockito.verify(resourceManager).addComponent(Mockito.argThat(c -> c.getName().equals(component.getName())), Mockito.eq(GROUP_PATH));
        Assertions.assertEquals(COMPONENT_ID, resultPartial.getId());
        Assertions.assertEquals(component.getName(), resultPartial.getName());
        Assertions.assertEquals(component.getDescription(), resultPartial.getDescription());
        Assertions.assertEquals(component.getTitle(), resultPartial.getTitle());
        Assertions.assertEquals(COMPONENT_ID, resultPartial.getId());
    }

    private void mockGetComponent(Component componentInDb) throws ApiException {
        Mockito.when(resourceManager.getComponent(COMPONENT_ID)).thenReturn(componentInDb);
    }

    private void mockPatchComponent(Component componentInDb) throws ApiException {
        Answer<Component> patchComponentAnwser = invocation -> {
            String id = (String) invocation.getArguments()[0];
            Component patch = (Component) invocation.getArguments()[1];
            logger.info("patch component "+id+" with status "+patch.getStatus());
            if (id.equals(componentInDb.getId())) {
                if (patch.getStatus() != null) {
                    componentInDb.setStatus(patch.getStatus());
                }
                if (patch.getApiRepository() != null) {
                    componentInDb.setApiRepository(patch.getApiRepository());
                }
                return componentInDb;
            } else {
                return null;
            }
        };

        Mockito.doAnswer(patchComponentAnwser).when(resourceManager).updateComponentAttributes(Mockito.anyString(), Mockito.any());
    }

    private void mockLibrariesCreationWithException(Component componentInDb, int durationForEach, Exception exception) throws ApiException {
        Answer<Library> libraryServiceAnswer = invocation -> {
            Thread.sleep(durationForEach);
            throw exception;
        };
        Mockito.doAnswer(libraryServiceAnswer)
                .when(libraryService)
                .add(Mockito.argThat(c -> c.getId().equals(COMPONENT_ID)), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void mockLibrariesCreation(Component componentInDb, int durationForEach) throws ApiException {
        mockLibrariesCreation(componentInDb, durationForEach, Resource.StatusEnum.READY);
    }

    private void mockLibrariesCreation(Component componentInDb, int durationForEach, Resource.StatusEnum finalStatus) throws ApiException {
        Answer<Library> libraryServiceAnswer = invocation -> {
            final String id = UUID.randomUUID().toString();
            final Library lib = new Library()   .id(id)
                                                .status(Resource.StatusEnum.PENDING)
                                                .name(((Component) invocation.getArgument(0)).getName())
                                                .language(invocation.getArgument(1))
                                                .type(invocation.getArgument(2));
            componentInDb.getLibraries().add(lib);
            Mockito.when(libraryService.getById(id)).thenReturn(Optional.of(lib));
            logger.info("library "+id+" is "+lib.getStatus().toString());

            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(durationForEach);
                    Mockito.when(libraryService.getById(id)).thenReturn(Optional.of(new Library().id(id).status(finalStatus)));
                    logger.info("library "+id+" is "+finalStatus);
                    ((Runnable) invocation.getArgument(3)).run();
                } catch (Exception e) {e.printStackTrace();}
            });
            return lib;
        };

        Mockito.doAnswer(libraryServiceAnswer)
                .when(libraryService)
                .add(Mockito.argThat(c -> c.getId().equals(COMPONENT_ID)), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private void mockSourceRepositoryCreationWithException(Component componentInDb, int duration, Exception e) throws ApiException {
        String[] keys = {GROUP_ID};
        Answer<SourceRepository> sourceRepositoryServiceAnswer = invocation -> {
            Thread.sleep(duration);
            throw e;
        };

        Mockito.doAnswer(sourceRepositoryServiceAnswer)
                .when(sourceRepositoryService)
                .create(Mockito.eq(COMPONENT_NAME), Mockito.eq(API_REPOSITORY_PATH), Mockito.eq(keys), Mockito.any());
    }

    private void mockSourceRepositoryCreation(Component componentInDb, int duration) throws ApiException {
        mockSourceRepositoryCreation(componentInDb, duration, Resource.StatusEnum.READY);
    }

    private void mockSourceRepositoryCreation(Component componentInDb, int duration, Resource.StatusEnum statusFinal) throws ApiException {
        String[] keys = {GROUP_ID};
        Answer<SourceRepository> sourceRepositoryServiceAnswer = invocation -> {
            SourceRepository sourceRepository = new SourceRepository()  .id(REPOSITORY_ID)
                                                                        .status(Resource.StatusEnum.PENDING)
                                                                        .name(invocation.getArgument(0))
                                                                        .path(invocation.getArgument(1));
            Mockito.when(sourceRepositoryService.getById(sourceRepository.getId())).thenReturn(Optional.of(sourceRepository));
            logger.info("SourceRepository '"+REPOSITORY_ID+"' is "+Resource.StatusEnum.PENDING);
            componentInDb.setApiRepository(sourceRepository);
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(duration);
                    Mockito.when(sourceRepositoryService.getById(sourceRepository.getId())).thenReturn(Optional.of(new SourceRepository().id(sourceRepository.getId()).status(statusFinal)));
                    logger.info("SourceRepository '"+REPOSITORY_ID+"' is "+statusFinal);
                    ((Runnable) invocation.getArgument(3)).run();
                } catch (Exception e) {e.printStackTrace();}
            });
            return sourceRepository;
        };

        Mockito.doAnswer(sourceRepositoryServiceAnswer)
                .when(sourceRepositoryService)
                .create(Mockito.eq(COMPONENT_NAME), Mockito.eq(API_REPOSITORY_PATH), Mockito.eq(keys), Mockito.any());
    }

    @Test
    public void given_generating_componentsClient_error_when_create_then_throws_exception() throws ApiException {
        Component component = getComponent();
        // mock component creation with exception
        Mockito.when(resourceManager.addComponent(component, GROUP_ID)).thenThrow(new ApiException());
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(component, GROUP_ID));
    }
    @Test
    public void given_component_without_groupPath_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(component, ""));
        Assertions.assertEquals("GroupPath should be defined", exception.getMessage());
    }

    @Test
    public void given_component_with_empty_name_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getComponent().name(""), GROUP_PATH));
        Assertions.assertEquals("Component's name is empty", exception.getMessage());
    }

    @Test
    public void given_component_with_illegal_name_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getComponent().name("illeg@l n@m#"), GROUP_PATH));
        Assertions.assertEquals("Component's name do not respect regex's pattern ^[0-9A-Za-z_\\-]+$", exception.getMessage());
    }

    @Test
    public void given_unknown_groupPath_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getComponent(), GROUP_PATH + "_not_exist"));
        Assertions.assertEquals("GroupPath defined 'group-path_not_exist' doesn't exist.", exception.getMessage());
    }

    @Test
    public void given_component_without_name_when_create_then_throws_exception() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(getComponent().name(null), GROUP_PATH));
        Assertions.assertEquals("Component's name is empty", exception.getMessage());
    }

    @Test
    public void given_component_existing_when_create_then_throws_exception() throws ApiException {
        Component existing = getComponent();
        existing.putMetadataItem(ComponentService.METADATA_GROUP_PATH, GROUP_PATH);
        Mockito.when(resourceManager.getComponents()).thenReturn(ImmutableList.of(existing));
        Component component = getComponent();
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                underTest.create(component, GROUP_PATH));
        Assertions.assertEquals("Component already exists", exception.getMessage());
    }

    public static Component getComponent() {
        Component component = new Component();
        component.setName(COMPONENT_NAME);
        component.setDescription("a description");
        component.setTitle("a title");

        ApiVersion apiVersion = new ApiVersion();
        apiVersion.setVersion("0.0.1");

        component.setVersions(ImmutableList.of(apiVersion));

        return component;
    }

    public static Component getComponentWithId() {
        Component component = getComponent();
        component.setId(COMPONENT_ID);
        component.putMetadataItem("groupId", GROUP_ID);
        component.putMetadataItem("groupPath", GROUP_PATH);
        component.setLibraries(new ArrayList<>());
        return component;
    }

    public static Component getComponentFullyInitialized() {
        Component component = getComponentWithId();
        component.setStatus(Resource.StatusEnum.READY);
        component.setApiRepository(SourceRepositoryServiceAbstractTest.getSourceRepositoryForDb());
        component.setLibraries(ImmutableList.of(new Library().id("LIB1"), new Library().id("LIB2")));
        component.setVersions(ImmutableList.of(new ApiVersion().id("1.1.0"), new ApiVersion().id("1.0.0")));
        component.setImplementations(ImmutableList.of(ImplementationServiceTest.generateImplementationExample(Implementation.LanguageEnum.JAVA)));
        return component;
    }

}