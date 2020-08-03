package org.kathra.appmanager.service.reconciler;

import org.apache.camel.builder.RouteBuilder;
import org.kathra.appmanager.Config;
import org.kathra.appmanager.service.ServiceInjection;
import org.kathra.core.model.User;
import org.kathra.resourcemanager.client.GroupsClient;
import org.kathra.resourcemanager.client.UsersClient;
import org.kathra.utils.KathraSessionManager;

public class ResourceScannerScheduler extends RouteBuilder {

    @Override
    public void configure() {
        from("scheduler://foo?delay=30s").process(exchange -> {
            Config config = new Config();

            KathraSessionManager sessionManagerUserSync = new KeycloackSession(new User().name(config.getUserLogin()).password(config.getUserPassword()));
            new ResourceReconciler(new ServiceInjection(config, sessionManagerUserSync)).processForGlobalResources();

            GroupsClient groupsClient = new GroupsClient(config.getResourceManagerUrl(), sessionManagerUserSync);
            UsersClient usersClient = new UsersClient(config.getResourceManagerUrl(), sessionManagerUserSync);
            groupsClient.getGroups().forEach(group -> {
                if (group.getTechnicalUser() == null) {
                    return;
                }
                try {
                    User technicalUser = usersClient.getUser(group.getTechnicalUser().getId());
                    KathraSessionManager sessionManagerForTechnicalUser= new KeycloackSession(new User().name(technicalUser.getName()).password(technicalUser.getPassword()));
                    new ResourceReconciler(new ServiceInjection(config, sessionManagerForTechnicalUser)).processForGroupResource();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            });
        }).to("mock:success");
    }

}
