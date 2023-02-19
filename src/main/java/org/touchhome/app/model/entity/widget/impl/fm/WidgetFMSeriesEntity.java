package org.touchhome.app.model.entity.widget.impl.fm;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnoreParent;
import org.touchhome.bundle.api.ui.field.selection.UIFieldTreeNodeSelection;

@Entity
public class WidgetFMSeriesEntity extends WidgetSeriesEntity<WidgetFMEntity>
    implements HasSingleValueDataSource {

    public static final String PREFIX = "wgsfms_";

    @Override
    @UIField(order = 14, required = true)
    @UIFieldTreeNodeSelection(
        allowSelectDirs = true,
        allowSelectFiles = false,
        iconColor = "#14A669")
    @UIFieldIgnoreParent
    public String getValueDataSource() {
        return HasSingleValueDataSource.super.getValueDataSource();
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
