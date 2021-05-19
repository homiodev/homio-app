package org.touchhome.app.model.entity;

import org.hibernate.mapping.PersistentClass;
import org.touchhome.bundle.api.entity.CommunicationEntity;
import org.touchhome.bundle.api.ui.UISidebarChildren;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class CommunicationFallbackEntity extends CommunicationEntity<CommunicationFallbackEntity> {
    @Override
    public String getEntityPrefix() {
        return "communication_fallback";
    }
}
