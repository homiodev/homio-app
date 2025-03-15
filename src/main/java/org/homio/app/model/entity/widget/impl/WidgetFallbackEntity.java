package org.homio.app.model.entity.widget.impl;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import org.hibernate.mapping.PersistentClass;
import org.homio.api.ui.UISidebarChildren;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.jetbrains.annotations.NotNull;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class WidgetFallbackEntity extends WidgetEntity<WidgetFallbackEntity> {

  @Override
  public String getDefaultName() {
    return "Unknown discriminator";
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "fallback";
  }

  @Override
  public @NotNull String getImage() {
    return "fas fa-question";
  }

  @Override
  public boolean isVisible() {
    return false;
  }
}
