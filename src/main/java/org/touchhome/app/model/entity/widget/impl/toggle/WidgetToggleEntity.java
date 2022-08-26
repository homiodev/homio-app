package org.touchhome.app.model.entity.widget.impl.toggle;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;

import javax.persistence.Entity;

@Entity
public class WidgetToggleEntity extends WidgetBaseEntityAndSeries<WidgetToggleEntity, WidgetToggleSeriesEntity> {

    public static final String PREFIX = "wgttg_";

    @Override
    @JsonIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Header")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "Header", order = 1)
    public Boolean getShowAllButton() {
        return getJsonData("all", Boolean.TRUE);
    }

    @UIField(order = 32)
    public ToggleType getDisplayType() {
        return getJsonDataEnum("displayType", ToggleType.Slide);
    }

    public WidgetToggleEntity setDisplayType(ToggleType value) {
        setJsonData("displayType", value);
        return this;
    }

    @Override
    public String getImage() {
        return "fas fa-toggle-on";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    enum ToggleType {
        Regular, Slide
    }

    @Override
    @UIFieldIgnore
    public TimePeriod getTimePeriod() {
        return super.getTimePeriod();
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        return super.getShowTimeButtons();
    }

    public void setShowAllButton(Boolean value) {
        setJsonData("all", value);
    }
}
