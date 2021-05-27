package org.touchhome.app.model.entity;

import org.hibernate.mapping.PersistentClass;
import org.touchhome.bundle.api.entity.storage.StorageEntity;
import org.touchhome.bundle.api.ui.UISidebarChildren;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class StorageFallbackEntity extends StorageEntity<StorageFallbackEntity> {
    @Override
    public String getEntityPrefix() {
        return "storage_fallback";
    }
}
