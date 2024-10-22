package org.homio.app.model.entity.widget.impl.simple;

import jakarta.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
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
    public WidgetGroup getGroup() {
        return WidgetGroup.Simple;
    }

    @Override
    public @NotNull String getImage() {
        return "fab fa-pix";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "sim-val";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
