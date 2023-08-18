package org.homio.app.model.entity.widget.impl.fm;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldIgnoreParent;
import org.homio.api.ui.field.selection.UIFieldTreeNodeSelection;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;

@Entity
public class WidgetFMSeriesEntity extends WidgetSeriesEntity<WidgetFMEntity>
        implements HasSingleValueDataSource {

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
    protected String getSeriesPrefix() {
        return "fm";
    }

    @Override
    public String getDefaultName() {
        return null;
    }
}
