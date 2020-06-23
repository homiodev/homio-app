package org.touchhome.bundle.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.cloud.setting.CloudProviderSetting;

import java.util.Set;

@Log4j2
@Component
@RequiredArgsConstructor
public class CloudBundle implements BundleContext {

    private final EntityContext entityContext;

    public void init() {

    }

    @Override
    public String getBundleId() {
        return "cloud";
    }

    @Override
    public int order() {
        return 800;
    }

    @Override
    public Set<NotificationEntityJSON> getNotifications() {
        return entityContext.getSettingValue(CloudProviderSetting.class).getNotifications();
    }
}
