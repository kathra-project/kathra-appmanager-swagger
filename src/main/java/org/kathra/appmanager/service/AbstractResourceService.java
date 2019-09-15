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

import org.kathra.core.model.Resource;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author julien.boubechtoula
 */
public abstract class AbstractResourceService<X extends Resource> {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());
    protected KathraSessionManager kathraSessionManager;



    public void configure(ServiceInjection serviceInjection) {
        kathraSessionManager = serviceInjection.getSessionManager();
    }

    public boolean isReady(Resource resource) {
        return resource != null && resource.getStatus() != null && resource.getStatus().equals(Resource.StatusEnum.READY);
    }
    public boolean isPending(Resource resource) {
        return resource != null && resource.getStatus() != null && resource.getStatus().equals(Resource.StatusEnum.PENDING);
    }
    public boolean isError(Resource resource) {
        return resource != null && resource.getStatus() != null && resource.getStatus().equals(Resource.StatusEnum.ERROR);
    }
    public void throwExceptionIfError(Resource resource) {
        if (isError(resource)) {
            throw new IllegalStateException("Resource "+resource.getClass().getSimpleName()+" "+resource.getId()+" has status error");
        }
    }


    protected void updateStatus(X object, Resource.StatusEnum status) {
        try {
            if (object.getId() != null) {
                X patched = (X) object.getClass().getConstructors()[0].newInstance();
                patched.setId(object.getId());
                object.setStatus(status);
                patched.setStatus(status);
                patch(patched);
                logger.info("Resource " + object.getId() + " has status " + status);
            }
        } catch(Exception e){
            logger.error("Unable to change resource " + object.getId() + " status to " + status, e);
        }
    }

    protected abstract void patch(X object) throws ApiException;

    protected void manageError(X object, Exception exception) {
        logger.error("Error occurred for resource " + object.getClass() + " with id "+object.getId(), exception);
        StringWriter writer = new StringWriter();
        PrintWriter printWriter= new PrintWriter(writer);
        exception.printStackTrace(printWriter);
        try {
            if (object.getId() != null) {
                X patched = (X) object.getClass().getConstructors()[0].newInstance();
                patched.setId(object.getId());
                object.setStatus(Resource.StatusEnum.ERROR);
                patched.setStatus(Resource.StatusEnum.ERROR);
                patched.putMetadataItem("error-stack-trace", writer.toString());
                patch(patched);
                logger.info("Resource " + object.getId() + " has status " + Resource.StatusEnum.ERROR);
            }
        } catch(Exception e){
            logger.error("Unable to change resource " + object.getId() + " status to " + Resource.StatusEnum.ERROR, e);
        } finally {
            printWriter.flush();
            exception.printStackTrace();
        }
    }

    protected Map<String, X> mapById(List<X> resourcesToMap) {
        return resourcesToMap.stream().collect(Collectors.toMap(X::getId, item -> item));

    }


    public abstract Optional<X> getById(String id) throws ApiException;
    public abstract List<X> getAll() throws ApiException;

}
