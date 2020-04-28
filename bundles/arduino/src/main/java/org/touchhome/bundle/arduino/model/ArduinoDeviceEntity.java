package org.touchhome.bundle.arduino.model;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;

import javax.persistence.Entity;

@Entity
@UISidebarMenu(icon = "fas fa-tablet-alt", parent = UISidebarMenu.TopSidebarMenu.HARDWARE, order = 5, bg = "#7482d0")
@Accessors(chain = true)
public class ArduinoDeviceEntity extends DeviceBaseEntity<ArduinoDeviceEntity> {

    @UIField(readOnly = true, order = 21)
    @Getter
    @Setter
    private Long pipe;

    @Override
    public String getShortTitle() {
        return "Arduino";
    }

    @Override
    public int getOrder() {
        return 20;
    }
}
