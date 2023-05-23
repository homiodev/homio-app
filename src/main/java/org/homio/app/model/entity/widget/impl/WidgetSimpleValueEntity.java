package org.homio.app.model.entity.widget.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.attributes.HasActionOnClick;
import org.homio.app.model.entity.widget.attributes.HasAlign;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasPadding;
import org.homio.app.model.entity.widget.attributes.HasSingleValueAggregatedDataSource;
import org.homio.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;
import org.homio.bundle.api.exception.ProhibitedExecution;
import org.homio.bundle.api.ui.field.UIFieldIgnore;

@Entity
public class WidgetSimpleValueEntity extends WidgetBaseEntity<WidgetSimpleValueEntity>
    implements
    HasIcon,
    HasActionOnClick,
    HasSingleValueAggregatedDataSource,
    HasValueTemplate,
    HasPadding,
    HasAlign,
    HasValueConverter,
    HasSourceServerUpdates {

    public static final String PREFIX = "wgtvs_";

    @Override
    public String getImage() {
        return null;
    }

    @Override
    public boolean isVisible() {
        return false;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public Boolean getShowLastUpdateTimer() {
        throw new ProhibitedExecution();
    }
}
