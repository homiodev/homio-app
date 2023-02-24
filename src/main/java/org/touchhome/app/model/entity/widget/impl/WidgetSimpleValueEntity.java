package org.touchhome.app.model.entity.widget.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.attributes.HasActionOnClick;
import org.touchhome.app.model.entity.widget.attributes.HasAlign;
import org.touchhome.app.model.entity.widget.attributes.HasIcon;
import org.touchhome.app.model.entity.widget.attributes.HasPadding;
import org.touchhome.app.model.entity.widget.attributes.HasSingleValueAggregatedDataSource;
import org.touchhome.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.touchhome.app.model.entity.widget.attributes.HasValueConverter;
import org.touchhome.app.model.entity.widget.attributes.HasValueTemplate;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

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
