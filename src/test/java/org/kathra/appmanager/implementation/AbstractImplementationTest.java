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

import org.kathra.appmanager.component.ComponentServiceTest;
import org.kathra.appmanager.pipeline.PipelineServiceAbstractTest;
import org.kathra.appmanager.service.AbstractServiceTest;
import org.kathra.core.model.Asset;
import org.kathra.core.model.Implementation;
import org.kathra.core.model.ImplementationVersion;
import org.kathra.core.model.SourceRepository;
import org.kathra.utils.KathraSessionManager;
import org.mockito.Mock;

import java.util.UUID;

/**
 * Parent class for Implementation tests
 */
public abstract class AbstractImplementationTest extends AbstractServiceTest {


    public final static String IMPL_ID = "implementation-id";
    public final static String IMPL_SRC_REPO_ID = "implementation-src-repo-id";
    public final static String IMPL_SRC_REPO_PATH = "implementation-src-repo-path";
    public final static String IMPL_NAME = "implementation-name";
    public final static String IMPL_ARTIFACT_NAME = "implementationid";
    public final static String IMPL_ARTIFACT_GROUP_ID = "com.mygroup.subgroup";

    public static Implementation generateImplementationExample(Asset.LanguageEnum languageEnum) {
        String id = UUID.randomUUID().toString();
        return new Implementation()
                .id(IMPL_ID)
                .name(IMPL_NAME)
                .putMetadataItem("groupPath", "groupName")
                .putMetadataItem(ImplementationService.METADATA_ARTIFACT_NAME, IMPL_ARTIFACT_NAME)
                .putMetadataItem(ImplementationService.METADATA_ARTIFACT_GROUP_ID, IMPL_ARTIFACT_GROUP_ID)
                .sourceRepository(new SourceRepository().id(IMPL_SRC_REPO_ID).path(IMPL_SRC_REPO_PATH))
                .pipeline(new PipelineServiceAbstractTest().getPipeline())
                .component(new ComponentServiceTest().getComponentWithId())
                .language(languageEnum)
                .description("my new implem "+id);
    }

}
