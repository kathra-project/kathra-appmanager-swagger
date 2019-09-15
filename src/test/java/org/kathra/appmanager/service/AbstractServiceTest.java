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
import org.junit.jupiter.api.Assertions;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbstractServiceTest {

    @Mock
    protected KathraSessionManager kathraSessionManager;

    protected Logger logger = LoggerFactory.getLogger(AbstractServiceTest.class);

    protected long timeout = 2000;
    protected Runnable callback;

    protected void callbackIsCalled(boolean called) throws InterruptedException {
        long start = System.currentTimeMillis();
        boolean isVerified = false;
        Throwable exception = null;
        while(!isVerified) {
            if (System.currentTimeMillis() - start > timeout) {
                if (exception != null) {
                    exception.printStackTrace();
                }
                Assertions.fail("Timeout exceed "+(System.currentTimeMillis() - start)+" ms ");
            }
            try {
                Mockito.verify(callback, Mockito.atLeast(called ? 1 : 0)).run();
                isVerified = true;
            } catch (Throwable e) {
                isVerified = false;
                exception = e;
            }
            Thread.sleep(200);
        }
    }
    protected Runnable getCallBack() {
        return callback;
    }
}
