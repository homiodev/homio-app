package org.touchhome.app.service.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.setting.system.cloud.SystemCloudProviderSetting;
import org.touchhome.app.spring.ContextCreated;
import org.touchhome.bundle.api.service.CloudProviderService;

@Log4j2
@Component
@RequiredArgsConstructor
public class CloudService implements ContextCreated {

    private CloudProviderService cloudProvider;

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        entityContext.setting().listenValueAndGet(SystemCloudProviderSetting.class, "cloud", provider -> {
            if (cloudProvider == null || (cloudProvider.getClass() != provider.getClass())) {
                if (cloudProvider != null) {
                    log.warn("Stop cloud connection provider: '{}'", provider.getName());
                    try {
                        cloudProvider.stop();
                    } catch (Exception ex) {
                        log.error("Unable to stop cloud connection provider: '{}'. Try restart device.",
                            provider.getName(), ex);
                        return;
                    }
                }
                cloudProvider = provider;
                try {
                    log.warn("Starting cloud connection provider: '{}'", provider.getName());
                    cloudProvider.start();
                } catch (Exception ex) {
                    log.error("Unable to start cloud connection provider: '{}'. Try restart device.",
                        provider.getName(), ex);
                }
            }
        });
    }
}
