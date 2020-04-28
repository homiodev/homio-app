package org.touchhome.bundle.rf433.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.touchhome.bundle.api.model.DeviceBaseEntity;

import javax.persistence.Entity;

@Entity
@JsonInclude(JsonInclude.Include.NON_NULL)
/*@UISidebarMenu(
        icon = "fa-broadcast-tower",
        parent = UISidebarMenu.TopSidebarMenu.HARDWARE,
        templateUrl = "items",
        itemType = RF433DeviceEntity.class,
        allowCreateNewItems = false,
        order = 2,
        showInScriptDialog = true)*/
public class RF433DeviceEntity extends DeviceBaseEntity<RF433DeviceEntity> {

    @Override
    public String getShortTitle() {
        return "Rf433";
    }
}
