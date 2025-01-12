package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasValueConverter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasValueConverterBuilder<T extends org.homio.app.model.entity.widget.attributes.HasValueConverter, R>
  extends HasValueConverter<R> {

  T getWidget();

  @Override
  default @NotNull R setValueConverter(@Nullable String value) {
    getWidget().setValueConverter(value);
    return (R) this;
  }

  @Override
  default @NotNull R setValueConverterRefreshInterval(int value) {
    getWidget().setValueConverterInterval(value);
    return (R) this;
  }
}
