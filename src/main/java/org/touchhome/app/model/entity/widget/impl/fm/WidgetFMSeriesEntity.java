package org.touchhome.app.model.entity.widget.impl.fm;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.exception.ProhibitedExecution;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldIgnoreParent;
import org.touchhome.bundle.api.ui.field.selection.UIFieldFileSelection;

import javax.persistence.Entity;

@Entity
public class WidgetFMSeriesEntity extends WidgetSeriesEntity<WidgetFMEntity>
        implements HasSingleValueDataSource {

    public static final String PREFIX = "wgsfms_";

    @Override
    @UIField(order = 14, required = true)
    @UIFieldFileSelection(allowSelectDirs = true, allowSelectFiles = false, iconColor = "#14A669")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public AggregationType getAggregationType() {
        throw new ProhibitedExecution();
    }
}
