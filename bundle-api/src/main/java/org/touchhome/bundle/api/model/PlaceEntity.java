package org.touchhome.bundle.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import org.touchhome.bundle.api.ui.UISidebarMenu;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import java.util.Set;

@Entity
@UISidebarMenu(icon = "fas fa-map-marker-alt", order = 3, bg = "#2894ed", allowCreateNewItems = true)
public class PlaceEntity extends BaseEntity<PlaceEntity> {

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "ownerPlace")
    @JsonIgnore
    @Getter
    private Set<DeviceBaseEntity> deviceEntities;
}















