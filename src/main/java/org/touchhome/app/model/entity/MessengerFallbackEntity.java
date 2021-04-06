package org.touchhome.app.model.entity;

import org.hibernate.mapping.PersistentClass;
import org.touchhome.bundle.api.entity.MessengerEntity;
import org.touchhome.bundle.api.ui.UISidebarChildren;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class MessengerFallbackEntity extends MessengerEntity<MessengerFallbackEntity> {
    @Override
    public String getEntityPrefix() {
        return "messenger_fallback";
    }
}
