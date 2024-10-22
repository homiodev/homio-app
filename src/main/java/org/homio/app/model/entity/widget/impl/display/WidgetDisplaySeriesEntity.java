package org.homio.app.model.entity.widget.impl.display;

import jakarta.persistence.Entity;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.WidgetSeriesEntity;
import org.homio.app.model.entity.widget.attributes.*;

import java.util.List;

@Entity
public class WidgetDisplaySeriesEntity extends WidgetSeriesEntity<WidgetDisplayEntity>
        implements HasSingleValueDataSource, HasIcon, HasValueTemplate,
        HasName, HasValueConverter {

    @UIField(order = 1)
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
    protected String getSeriesPrefix() {
        return "display";
    }

    @Override
    public void beforePersist() {
        HasIcon.randomColor(this);
    }

    @UIField(order = 500, type = UIFieldType.Chips)
    @UIFieldGroup("UI")
    public List<String> getStyle() {
        return getJsonDataList("style");
    }

    public void setStyle(String value) {
        setJsonData("style", value);
    }
}
