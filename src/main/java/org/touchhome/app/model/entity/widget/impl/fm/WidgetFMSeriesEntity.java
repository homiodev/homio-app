package org.touchhome.app.model.entity.widget.impl.fm;

import org.touchhome.bundle.api.entity.widget.WidgetSeriesEntity;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.selection.UIFieldFileSelection;

import javax.persistence.Entity;

@Entity
public class WidgetFMSeriesEntity extends WidgetSeriesEntity<WidgetFMEntity> {

    public static final String PREFIX = "wgsfms_";

    @UIField(order = 14, required = true)
    @UIFieldFileSelection(allowSelectDirs = true, allowSelectFiles = false, iconColor = "#14A669")
    public String getDataSource() {
        return getJsonData("ds");
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }


}
