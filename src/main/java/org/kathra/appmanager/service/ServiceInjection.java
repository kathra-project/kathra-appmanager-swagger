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
package org.kathra.appmanager.service;

import org.kathra.utils.KathraSessionManager;
import org.kathra.appmanager.Config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class ServiceInjection {

    private final Map<Class,Injectable> instancesServices = new HashMap<>();

    Config config;
    KathraSessionManager kathraSessionManager;

    public ServiceInjection(Config config, KathraSessionManager sessionManager) {
        this.config = config;
        this.kathraSessionManager = sessionManager;
    }

    public synchronized <T extends Injectable> T getService(Class<T> clazz) {

        Injectable instance = instancesServices.get(clazz);
        if (instance == null) {
            try {
                Constructor<T> defaultConstructor = clazz.getConstructor();
                instance = defaultConstructor.newInstance();
                instancesServices.put(clazz, instance);
                instance.configure(this);
            } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }

        return (T) instance;
    }

    public KathraSessionManager getSessionManager() {
        return this.kathraSessionManager;
    }

    public Config getConfig() {
        return this.config;
    }
}
