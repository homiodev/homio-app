package org.homio.app.builder.widget;

import org.homio.api.EntityContextWidget.LayoutWidgetBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.lang.String.format;

public class LayoutBuilderImpl extends WidgetBaseBuilderImpl<LayoutWidgetBuilder, WidgetLayoutEntity>
        implements LayoutWidgetBuilder {

    LayoutBuilderImpl(WidgetLayoutEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public @NotNull LayoutWidgetBuilder setLayoutDimension(int rows, int columns) {
        if (rows > 30 || columns > 15) {
            throw new IllegalArgumentException("rows/columns must be less than 9");
        }
        widget.setLayout(format("%sx%s", columns, rows));
        return this;
    }

    @Override
    public @NotNull LayoutWidgetBuilder setBorderColor(@Nullable String value) {
        widget.setBorderColor(value);
        return this;
    }
}
