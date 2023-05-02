package org.homio.app.service.cloud;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;

import javax.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.setting.system.cloud.SystemCloudProviderSetting;
import org.homio.app.spring.ContextCreated;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.service.CloudProviderService;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.util.FlowMap;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CloudService implements ContextCreated {

    private final EntityContext entityContext;
    private CloudProviderService cloudProvider;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        entityContext.setting().listenValueAndGet(SystemCloudProviderSetting.class, "cloud", provider -> {
            if (cloudProvider == null || (cloudProvider.getClass() != provider.getClass())) {
                if (cloudProvider != null) {
                    log.warn("Stop cloud connection provider: '{}'", provider.getName());
                    try {
                        cloudProvider.stop();
                        cloudProvider.updateNotificationBlock();
                    } catch (Exception ex) {
                        log.error("Unable to stop cloud connection provider: '{}'. Try restart device.",
                            provider.getName(), ex);
                        cloudProvider.updateNotificationBlock(ex);
                        return;
                    }
                }
                cloudProvider = provider;
                restart();
            }
        });
    }

    @RolesAllowed(ADMIN_ROLE)
    public void restart() {
        try {
            log.warn("Starting cloud connection provider: '{}'", cloudProvider.getName());
            cloudProvider.start();
            cloudProvider.updateNotificationBlock();
            entityContext.ui().sendSuccessMessage("cloud.success");
        } catch (Exception ex) {
            String message = CommonUtils.getErrorMessage(ex);
            log.error("Unable to start cloud connection provider: '{}'. Try restart device.",
                cloudProvider.getName(), ex);
            cloudProvider.updateNotificationBlock(ex);
            entityContext.ui().sendErrorMessage("cloud.error", "cloud.unable_connect", FlowMap.of("MSG", message), null);
        }
    }
}
