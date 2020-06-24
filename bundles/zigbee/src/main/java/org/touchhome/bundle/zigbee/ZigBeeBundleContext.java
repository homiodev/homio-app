package org.touchhome.bundle.zigbee;

import com.zsmartsystems.zigbee.IeeeAddress;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.util.NotificationType;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeChannelConverterFactory;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.setting.ZigbeeCoordinatorHandlerSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusMessageSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusSetting;
import org.touchhome.bundle.zigbee.workspace.ZigBeeDeviceUpdateValueListener;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Component
@RequiredArgsConstructor
public class ZigBeeBundleContext implements BundleContext {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);

    private final EntityContext entityContext;
    private final ZigBeeChannelConverterFactory zigBeeChannelConverterFactory;
    private final ZigBeeDeviceUpdateValueListener deviceUpdateListener;

    private final ZigBeeIsAliveTracker zigBeeIsAliveTracker = new ZigBeeIsAliveTracker();

    private ZigBeeDiscoveryService zigBeeDiscoveryService;
    @Getter
    private ZigBeeCoordinatorHandler coordinatorHandler;

    @Override
    public void init() {
        this.entityContext.setSettingValueSilence(ZigbeeStatusSetting.class, DeviceStatus.UNKNOWN);
        this.coordinatorHandler = entityContext.getSettingValue(ZigbeeCoordinatorHandlerSetting.class);
        this.zigBeeDiscoveryService = new ZigBeeDiscoveryService(
                entityContext, coordinatorHandler,
                zigBeeIsAliveTracker,
                zigBeeChannelConverterFactory,
                scheduler,
                deviceUpdateListener);

        this.entityContext.listenSettingValue(ZigbeeStatusSetting.class, status -> {

            if (status == DeviceStatus.ONLINE) {
                for (ZigBeeDeviceEntity zigbeeDeviceEntity : entityContext.findAll(ZigBeeDeviceEntity.class)) {
                    zigBeeDiscoveryService.addZigBeeDevice(new IeeeAddress(zigbeeDeviceEntity.getIeeeAddress()));
                }
            }
        });

        coordinatorHandler.initialize();
    }

    @Override
    public void destroy() {
        this.coordinatorHandler.dispose();
    }

    @Override
    public String getBundleId() {
        return "zigbee";
    }

    @Override
    public int order() {
        return 300;
    }

    @Override
    public Set<NotificationEntityJSON> getNotifications() {
        DeviceStatus deviceStatus = entityContext.getSettingValue(ZigbeeStatusSetting.class);
        return Collections.singleton(new NotificationEntityJSON("zigbee-status")
                .setName("Zigbee status: " + deviceStatus)
                .setDescription(entityContext.getSettingValue(ZigbeeStatusMessageSetting.class))
                .setNotificationType(deviceStatus == DeviceStatus.ONLINE ? NotificationType.info : NotificationType.warn));
    }
}
