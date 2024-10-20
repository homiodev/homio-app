package org.homio.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.UIFieldMarkers;
import org.homio.app.model.entity.widget.UIFieldOptionFontSize;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.attributes.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Entity
public class WidgetGaugeEntity extends WidgetEntity<WidgetGaugeEntity>
        implements
        HasSingleValueDataSource,
        HasSetSingleValueDataSource,
        HasIcon,
        HasValueConverter,
        HasTextConverter,
        HasName,
        HasValueTemplate,
        WidgetGaugeUITab {

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public List<String> getStyle() {
        return super.getStyle();
    }

    @UIField(order = 100, label = "gauge.min")
    @UIFieldGroup("VALUE")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public void setMin(Integer value) {
        setJsonData("min", value);
    }

    @UIField(order = 110, label = "gauge.max")
    @UIFieldGroup("VALUE")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public void setMax(Integer value) {
        setJsonData("max", value);
    }

    @Override
    @UIField(order = 12)
    @UIFieldGroup(value = "SET_VALUE", order = 2, borderColor = "#4CC9FE")
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIEditReloadWidget
    public String getSetValueDataSource() {
        return HasSetSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 250)
    @UIFieldSlider(min = 0, max = 2)
    @UIFieldGroup("SET_VALUE")
    @UIFieldReadDefaultValue
    public int getValuePrecision() {
        return getJsonData("prec", 0);
    }

    public void setValuePrecision(int value) {
        setJsonData("prec", value);
    }

    @UIField(order = 1)
    public String getName() {
        return super.getName();
    }

    @UIField(order = 2)
    @UIFieldGroup("NAME")
    @UIFieldOptionFontSize
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

    @UIField(order = 3)
    @UIFieldGroup("NAME")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getUnitColor() {
        return getJsonData("uc", UI.Color.WHITE);
    }

    public void setUnitColor(String value) {
        setJsonData("uc", value);
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
    @UIFieldIgnore
    @JsonIgnore
    public String getValueTemplate() {
        return HasValueTemplate.super.getValueTemplate();
    }

    @UIField(order = 10, required = true)
    @UIFieldGroup(value = "SECOND_VALUE", order = 20, borderColor = "#BF00FF")
    @UIFieldTab("ADVANCED")
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIEditReloadWidget
    public String getSecondValueDataSource() {
        return getJsonData("vds");
    }

    public void setSecondValueDataSource(String value) {
        setJsonData("vds", value);
    }

    @UIField(order = 9)
    @UIFieldSlider(min = 0, max = 20)
    @UIFieldGroup("SECOND_VALUE")
    @UIFieldTab("ADVANCED")
    @UIFieldReadDefaultValue
    public int getSecondDotRadiusWidth() {
        return getJsonData("dotbrw", 0);
    }

    public void setSecondDotRadiusWidth(int value) {
        setJsonData("dotbrw", value);
    }

    @UIField(order = 11)
    @UIFieldGroup("SECOND_VALUE")
    @UIFieldTab("ADVANCED")
    @UIFieldColorPicker
    @UIFieldReadDefaultValue
    public String getSecondDotColor() {
        return getJsonData("dotsc", UI.Color.WHITE);
    }

    public void setSecondDotColor(String value) {
        setJsonData("dotsc", value);
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
