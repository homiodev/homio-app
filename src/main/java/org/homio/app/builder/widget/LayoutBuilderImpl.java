package org.homio.app.builder.widget;

import static java.lang.String.format;

import org.homio.api.EntityContextWidget.LayoutWidgetBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.jetbrains.annotations.Nullable;

public class LayoutBuilderImpl extends WidgetBaseBuilderImpl<LayoutWidgetBuilder, WidgetLayoutEntity>
    implements LayoutWidgetBuilder {

    LayoutBuilderImpl(WidgetLayoutEntity widget, EntityContextImpl entityContext) {
        super(widget, entityContext);
    }

    @Override
    public LayoutWidgetBuilder setLayoutDimension(int rows, int columns) {
        if (rows > 30 || columns > 15) {
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
