package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasName;
import org.homio.api.entity.BaseEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasNameBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.attributes.HasName, R>
  extends HasName<R> {

  T getWidget();

  @Override
  default @NotNull R setName(@Nullable String value) {
    getWidget().setName(value);
    return (R) this;
  }

  @Override
  default @NotNull R setNameColor(@Nullable String value) {
    getWidget().setNameColor(value);
    return (R) this;
  }
}
