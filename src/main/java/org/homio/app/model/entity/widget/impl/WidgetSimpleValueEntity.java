package org.homio.app.model.entity.widget.impl;

import jakarta.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.*;
import org.jetbrains.annotations.NotNull;

@Entity
public class WidgetSimpleValueEntity extends WidgetEntity<WidgetSimpleValueEntity>
        implements
        HasBackground,
        HasIcon,
        HasActionOnClick,
        HasSingleValueDataSource,
        HasValueTemplate,
        HasPadding,
        HasAlign,
        HasValueConverter {

    @Override
    public @NotNull String getImage() {
        return "";
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-value";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
