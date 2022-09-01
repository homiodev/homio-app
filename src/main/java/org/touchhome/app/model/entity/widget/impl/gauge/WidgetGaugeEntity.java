package org.touchhome.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.impl.HasIcon;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.HasTimePeriod;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;

@Entity
public class WidgetGaugeEntity extends WidgetBaseEntity<WidgetGaugeEntity>
        implements HasSingleValueDataSource, HasTimePeriod, HasIcon {

    public static final String PREFIX = "wgtgg_";

    @UIField(order = 1)
    @UIFieldGroup(value = "UI", order = 5, borderColor = "#21B584")
    public GaugeType getDisplayType() {
        return getJsonDataEnum("displayType", GaugeType.arch);
    }

    public WidgetGaugeEntity setDisplayType(GaugeType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 2, label = "gauge.min")
    @UIFieldGroup("UI")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public WidgetGaugeEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    @UIField(order = 3, label = "gauge.max")
    @UIFieldGroup("UI")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public WidgetGaugeEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    @UIField(order = 4, type = UIFieldType.Slider, label = "gauge.thick")
    @UIFieldNumber(min = 1, max = 10)
    @UIFieldGroup("UI")
    public Integer getThick() {
        return getJsonData("thick", 6);
    }

    public WidgetGaugeEntity setThick(Integer value) {
        setJsonData("thick", value);
        return this;
    }

    @UIField(order = 5)
    @UIFieldGroup("UI")
    public LineType getGaugeCapType() {
        return getJsonDataEnum("gaugeCapType", LineType.round);
    }

    public WidgetGaugeEntity setGaugeCapType(LineType lineType) {
        setJsonDataEnum("gaugeCapType", lineType);
        return this;
    }

    @UIField(order = 6)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getGaugeForeground() {
        return getJsonData("gfc", UI.Color.WHITE);
    }

    public void setGaugeForeground(String value) {
        setJsonData("gfc", value);
    }

    @UIField(order = 1, type = UIFieldType.StringTemplate)
    @UIFieldGroup(value = "Value", order = 20)
    public String getValueTemplate() {
        return getJsonData("vt", "~~~℃");
    }

    public void setValueTemplate(String value) {
        setJsonData("vt", value);
    }

    @UIField(order = 2, label = "label")
    @UIFieldGroup(value = "Value")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 24)
    public Boolean getAnimations() {
        return getJsonData("animations", Boolean.FALSE);
    }

    public WidgetGaugeEntity setAnimations(Boolean value) {
        setJsonData("animations", value);
        return this;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public String getLayout() {
        throw new IllegalStateException("MNC");
    }

    @Override
    public String getImage() {
        return "fas fa-tachometer-alt";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        String valueDataSource = getSingleValueDataSource().getKey();
        if (valueDataSource != null && entityContext.getEntity(valueDataSource) == null) {
            this.setValueDataSource(null);
            return true;
        }
        return false;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    protected void beforePersist() {
        if (!getJsonData().has("gfg")) {
            setGaugeForeground(UI.Color.random());
        }
    }

    public enum GaugeType {
        full, semi, arch
    }

    public enum LineType {
        round, butt
    }
}
