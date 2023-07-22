package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.EntityContextWidget.HasValueConverter;
import org.jetbrains.annotations.Nullable;

public interface HasValueConverterBuilder<T extends org.homio.app.model.entity.widget.attributes.HasValueConverter, R>
    extends HasValueConverter<R> {

    T getWidget();

    @Override
    default R setValueConverter(@Nullable String value) {
        getWidget().setValueConverter(value);
        return (R) this;
    }

    @Override
    default R setValueConverterRefreshInterval(int value) {
        getWidget().setValueConverterInterval(value);
        return (R) this;
    }
}
