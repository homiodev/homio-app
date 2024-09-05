package org.homio.app.model.entity.widget.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.apache.commons.lang3.NotImplementedException;
import org.homio.api.ui.field.UIFieldIgnore;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

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
        HasValueConverter,
        HasSourceServerUpdates {

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

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowLastUpdateTimer() {
        throw new NotImplementedException();
    }
}
