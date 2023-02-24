package org.touchhome.app.model.entity.widget.impl.display;

import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.WidgetSeriesEntity;
import org.touchhome.app.model.entity.widget.attributes.HasIcon;
import org.touchhome.app.model.entity.widget.attributes.HasName;
import org.touchhome.app.model.entity.widget.attributes.HasSingleValueAggregatedDataSource;
import org.touchhome.app.model.entity.widget.attributes.HasStyle;
import org.touchhome.app.model.entity.widget.attributes.HasValueConverter;
import org.touchhome.app.model.entity.widget.attributes.HasValueTemplate;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
    implements HasSingleValueAggregatedDataSource, HasIcon, HasValueTemplate,
    HasName, HasStyle, HasValueConverter {

    public static final String PREFIX = "wgsdps_";

    @UIField(order = 1, isRevert = true)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldReadDefaultValue
    public String getBackground() {
        return getJsonData("bg", "transparent");
    }

    public void setBackground(String value) {
        setJsonData("bg", value);
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
