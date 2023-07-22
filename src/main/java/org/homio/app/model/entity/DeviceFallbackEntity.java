package org.homio.app.model.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.hibernate.mapping.PersistentClass;
import org.homio.api.entity.DeviceBaseEntity;
import org.homio.api.ui.UISidebarChildren;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class DeviceFallbackEntity extends DeviceBaseEntity<DeviceFallbackEntity> {

    @Override
    public String getEntityPrefix() {
        return "misc_fallback";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
