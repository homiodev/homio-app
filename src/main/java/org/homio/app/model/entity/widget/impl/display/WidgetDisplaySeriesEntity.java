package org.homio.app.model.entity.widget.impl.display;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldColorPicker;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.HasIcon;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSingleValueAggregatedDataSource;
import org.homio.app.model.entity.widget.attributes.HasStyle;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.homio.app.model.entity.widget.attributes.HasValueTemplate;
import org.jetbrains.annotations.NotNull;

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
    public @NotNull String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        HasIcon.randomColor(this);
    }
}
