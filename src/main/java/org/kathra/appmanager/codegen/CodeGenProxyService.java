package org.kathra.appmanager.codegen;

import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.kathra.appmanager.service.Injectable;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.codegen.client.CodegenClient;
import org.kathra.codegen.model.CodeGenTemplate;
import org.kathra.utils.ApiException;
import org.kathra.utils.KathraSessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CodeGenProxyService implements Injectable {

    private Map<String, CodegenClient> providers;
    protected KathraSessionManager kathraSessionManager;

    public void configure(ServiceInjection serviceInjection) {
        kathraSessionManager = serviceInjection.getSessionManager();
        providers = ImmutableMap.of("SWAGGER",new CodegenClient(serviceInjection.getConfig().getCodegenUrlSwagger()), "HELM", new CodegenClient(serviceInjection.getConfig().getCodegenUrlHelm()));
    }

    public CodeGenProxyService() {
        providers = ImmutableMap.of("SWAGGER",new CodegenClient(), "HELM", new CodegenClient());
    }

    public List<Triple<String,CodegenClient,List<CodeGenTemplate>>> getAllTemplates() throws ApiException {
        List list = new ArrayList();
        for(String provider : providers.keySet()){
            list.add(new ImmutableTriple(provider, providers.get(provider), providers.get(provider).getTemplates()));
        }
        return list;
    }
}
