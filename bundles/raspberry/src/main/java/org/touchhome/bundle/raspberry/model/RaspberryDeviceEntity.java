package org.touchhome.bundle.raspberry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.model.DeviceBaseEntity;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldExpand;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.method.UIFieldCreateWorkspaceVariableOnEmpty;
import org.touchhome.bundle.raspberry.repository.RaspberryDeviceRepository;

import javax.persistence.Entity;
import javax.persistence.Transient;
import java.util.List;
import java.util.Map;

@Entity
@UISidebarMenu(icon = "fab fa-raspberry-pi", parent = UISidebarMenu.TopSidebarMenu.HARDWARE, bg = "#c4d24f")
public class RaspberryDeviceEntity extends DeviceBaseEntity<RaspberryDeviceEntity> {

    public static final String DEFAULT_DEVICE_ENTITY_ID = RaspberryDeviceRepository.PREFIX + "LocalRaspberry";

    @Override
    public String getShortTitle() {
        return "Rpi";
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Getter
    @Setter
    @Transient
    @UIField(order = 40, type = UIFieldType.Selection, readOnly = true, color = "#7FBBCC")
    @UIFieldExpand
    @UIFieldCreateWorkspaceVariableOnEmpty
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private List<Map<Option, String>> availableLinks;

}
