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
package org.kathra.appmanager.service;

import org.kathra.utils.KathraSessionManager;
import org.kathra.utils.security.KeycloakUtils;

/**
 * @author julien.boubechtoula
 */
public class SecurityService {

    private KathraSessionManager sessionManager;

    public enum UserInformation {
        GROUPS("groups");

        private String key;
        UserInformation(String key) {
            this.key = key;
        }
    }

    public SecurityService(KathraSessionManager sessionManager){
        this.sessionManager = sessionManager;
    }

    public Object getUserInfo(UserInformation userInformation) throws Exception {
        return KeycloakUtils.getUserInfos(sessionManager.getCurrentSession().getAccessToken()).get(userInformation.key);
    }

}
