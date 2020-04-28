package org.touchhome.bundle.raspberry.model;

import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.ui.UISidebarMenu;

import javax.persistence.Entity;

@Entity
@UISidebarMenu(icon = "fab fa-raspberry-pi", parent = UISidebarMenu.TopSidebarMenu.HARDWARE, bg = "#c4d24f")
public class RaspberryDeviceEntity extends DeviceBaseEntity<RaspberryDeviceEntity> {

    public static final String DEFAULT_DEVICE_ENTITY_ID = "od_LocalRaspberry";

    @Override
    public String getShortTitle() {
        return "Rpi";
    }

    @Override
    public int getOrder() {
        return 10;
    }
}
