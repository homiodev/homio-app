package org.touchhome.app.builder.widget;

import static java.lang.String.format;

import org.jetbrains.annotations.Nullable;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.touchhome.bundle.api.EntityContextWidget.LayoutWidgetBuilder;

public class LayoutBuilderImpl extends WidgetBaseBuilderImpl<LayoutWidgetBuilder, WidgetLayoutEntity>
    implements LayoutWidgetBuilder {

    LayoutBuilderImpl(WidgetLayoutEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public LayoutWidgetBuilder setLayoutDimension(int rows, int columns) {
        if (rows > 8 || columns > 8) {
            throw new IllegalArgumentException("rows/columns must be less than 9");
        }
        widget.setLayout(format("%sx%s", columns, rows));
        return this;
    }

    @Override
    public LayoutWidgetBuilder setBorderColor(@Nullable String value) {
        widget.setBorderColor(value);
        return this;
    }

    @Override
    public LayoutWidgetBuilder setShowWidgetBorders(boolean value) {
        widget.setShowWidgetBorders(value);
        return this;
    }
}
