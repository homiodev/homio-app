package org.homio.app.model.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.hibernate.mapping.PersistentClass;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.ui.route.UIRouteMisc;
import org.jetbrains.annotations.NotNull;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UIRouteMisc(icon = "", color = "", allowCreateItem = false)
public class DeviceFallbackEntity extends DeviceBaseEntity {

  @Override
  protected @NotNull String getDevicePrefix() {
    return "fallback";
  }

  @Override
  public String getDefaultName() {
    return null;
  }
}
