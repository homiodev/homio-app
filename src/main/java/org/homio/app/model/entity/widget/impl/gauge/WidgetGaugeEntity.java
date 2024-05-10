package org.homio.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.app.model.entity.widget.UIFieldMarkers;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.*;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

@Entity
public class WidgetGaugeEntity extends WidgetEntity<WidgetGaugeEntity>
        implements HasSingleValueDataSource,
        HasIcon,
        HasValueConverter,
        HasTextConverter,
        HasName,
        HasValueTemplate,
        HasSourceServerUpdates {

    @UIField(order = 1)
    @UIFieldGroup("UI")
    public GaugeType getDisplayType() {
        return getJsonDataEnum("displayType", GaugeType.arch);
    }

    public void setDisplayType(GaugeType value) {
        setJsonDataEnum("displayType", value);
    }

    @UIField(order = 2, label = "gauge.min")
    @UIFieldGroup("UI")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public void setMin(Integer value) {
        setJsonData("min", value);
    }

    @UIField(order = 3, label = "gauge.max")
    @UIFieldGroup("UI")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public void setMax(Integer value) {
        setJsonData("max", value);
    }

    @UIField(order = 4, type = UIFieldType.Slider, label = "gauge.thick")
    @UIFieldNumber(min = 1, max = 20)
    @UIFieldGroup("UI")
    public Integer getThick() {
        return getJsonData("thick", 6);
    }

    public void setThick(Integer value) {
        setJsonData("thick", value);
    }

    @UIField(order = 5)
    @UIFieldGroup("UI")
    public LineType getGaugeCapType() {
        return getJsonDataEnum("gaugeCapType", LineType.round);
    }

    public void setGaugeCapType(LineType lineType) {
        setJsonDataEnum("gaugeCapType", lineType);
    }

    @UIField(order = 6)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldReadDefaultValue
    public String getGaugeForeground() {
        return getJsonData("gfc", UI.Color.WHITE);
    }

    public void setGaugeForeground(String value) {
        setJsonData("gfc", value);
    }

    @UIField(order = 1)
    @UIFieldGroup("UI")
    @UIFieldMarkers(UIFieldMarkers.MarkerOP.opacity)
    public String getSliceThreshold() {
        return getJsonData("slices", "");
    }

    public void setSliceThreshold(String value) {
        setJsonData("slices", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "TEXT", order = 20, borderColor = "#D4B72B")
    public String getUnit() {
        return getJsonData("unit", "℃");
    }

    public void setUnit(String value) {
        setJsonData("unit", value);
    }

    @UIField(order = 0, hideInView = true, hideInEdit = true)
    public double getUnitFontSize() {
        return getJsonData("unitFS", 1D);
    }

    public void setUnitFontSize(double value) {
        setJsonData("unitFS", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("TEXT")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getUnitColor() {
        return getJsonData("uc", UI.Color.WHITE);
    }

    public void setUnitColor(String value) {
        setJsonData("uc", value);
    }

    @UIField(order = 3, label = "label")
    @UIFieldGroup("TEXT")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 4)
    @UIFieldGroup("TEXT")
    @UIFieldColorPicker(allowThreshold = true)
    @UIFieldReadDefaultValue
    public String getTextColor() {
        return getJsonData("tc", UI.Color.WHITE);
    }

    public void setTextColor(String value) {
        setJsonData("tc", value);
    }

    @UIField(order = 24)
    public Boolean getAnimations() {
        return getJsonData("animations", Boolean.FALSE);
    }

    public void setAnimations(Boolean value) {
        setJsonData("animations", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "MARKERS", order = 500, borderColor = "#1F85B8")
    @UIFieldMarkers(UIFieldMarkers.MarkerOP.label)
    public String getMarkers() {
        return getJsonData("markers", "");
    }

    public void setMarkers(String value) {
        setJsonData("markers", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("MARKERS")
    public MarkerType getMarkerType() {
        return getJsonDataEnum("mt", MarkerType.line);
    }

    public void setMarkerType(MarkerType value) {
        setJsonDataEnum("mt", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("MARKERS")
    @UIFieldSlider(min = 8, max = 14)
    public int getMarkerFontSize() {
        return getJsonData("mfs", 12);
    }

    public void setMarkerFontSize(int value) {
        setJsonData("mfs", value);
    }

    @UIField(order = 4)
    @UIFieldGroup("MARKERS")
    @UIFieldSlider(min = 2, max = 16)
    public int getMarkerSize() {
        return getJsonData("ms", 8);
    }

    public void setMarkerSize(int value) {
        setJsonData("ms", value);
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-tachometer-alt";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "gauge";
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void assembleMissingMandatoryFields(@NotNull Set<String> fields) {

    }

    @Override
    @UIFieldIgnore
    @JsonIgnore
    public String getValueTemplate() {
        return HasValueTemplate.super.getValueTemplate();
    }

    @Override
    public void beforePersist() {
        if (!getJsonData().has("gfg")) {
            setGaugeForeground(UI.Color.random());
        }
        HasIcon.randomColor(this);
    }

    public enum GaugeType {
        full,
        semi,
        arch
    }

    public enum MarkerType {
        line,
        triangle
    }

    public enum LineType {
        round,
        butt
    }
}
