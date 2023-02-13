package org.touchhome.app.model.entity.widget.impl.display;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasName;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueAggregatedDataSource;
import org.touchhome.app.model.entity.widget.impl.HasValueConverter;
import org.touchhome.app.model.entity.widget.impl.HasValueTemplate;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
    implements HasSingleValueAggregatedDataSource, HasIcon, HasValueTemplate, HasName, HasValueConverter {

    public static final String PREFIX = "wgsdps_";

    @UIField(order = 1, isRevert = true)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    public String getPadding() {
        return getJsonData("padding", "0px");
    }

    public void setPadding(String value) {
        setJsonData("padding", value);
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
    }
}
