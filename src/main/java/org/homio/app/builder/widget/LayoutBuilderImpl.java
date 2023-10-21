package org.homio.app.builder.widget;

import static java.lang.String.format;

import org.homio.api.ContextWidget.LayoutWidgetBuilder;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LayoutBuilderImpl extends WidgetBaseBuilderImpl<LayoutWidgetBuilder, WidgetLayoutEntity>
        implements LayoutWidgetBuilder {

    LayoutBuilderImpl(WidgetLayoutEntity widget, ContextImpl context) {
        super(widget, context);
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
