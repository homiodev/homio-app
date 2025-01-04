package org.homio.app.model.entity.widget.impl.gauge;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import org.homio.api.entity.validation.MaxItems;
import org.homio.api.entity.widget.ability.HasGetStatusValue;
import org.homio.api.entity.widget.ability.HasSetStatusValue;
import org.homio.api.ui.UI;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.attributes.HasSetSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasSingleValueDataSource;
import org.homio.app.model.entity.widget.attributes.HasValueConverter;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.List;
import java.util.Set;

@Entity
public class WidgetGaugeEntity extends WidgetEntityAndSeries<WidgetGaugeEntity, WidgetGaugeSeriesEntity>
        implements
        HasName,
        HasSingleValueDataSource,
        HasSetSingleValueDataSource,
        HasValueConverter,
        WidgetGaugeUITab,
        HasMargin {

    @Override
    @MaxItems(3)
    public Set<WidgetGaugeSeriesEntity> getSeries() {
        return super.getSeries();
    }

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
    public String getSetValueDataSource() {
        return HasSetSingleValueDataSource.super.getSetValueDataSource();
    }

    @UIField(order = 250)
    @UIFieldSlider(min = 0, max = 2)
    @UIFieldGroup("SET_VALUE")
    public int getValuePrecision() {
        return getJsonData("prec", 0);
    }

    public void setValuePrecision(int value) {
        setJsonData("prec", value);
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

    @UIField(order = 10)
    @UIFieldGroup(value = "SECOND_VALUE", order = 20, borderColor = "#BF00FF")
    @UIFieldTab("ADVANCED")
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIEditReloadWidget
    public String getSecondValueDataSource() {
        return getJsonData("vdss");
    }

    public void setSecondValueDataSource(String value) {
        setJsonData("vdss", value);
    }

    public @NotNull JSONObject getSecondValueDynamicParameterFields() {
        return getDynamicParameterFields("secondValueDataSource");
    }

    @UIField(order = 9)
    @UIFieldSlider(min = 0, max = 20)
    @UIFieldGroup("SECOND_VALUE")
    @UIFieldTab("ADVANCED")
    public int getSecondDotRadiusWidth() {
        return getJsonData("dotsrw", 0);
    }

    public void setSecondDotRadiusWidth(int value) {
        setJsonData("dotsrw", value);
    }

    @UIField(order = 11)
    @UIFieldGroup("SECOND_VALUE")
    @UIFieldTab("ADVANCED")
    @UIFieldColorPicker
    public String getSecondDotColor() {
        return getJsonData("dotsc", UI.Color.WHITE);
    }

    public void setSecondDotColor(String value) {
        setJsonData("dotsc", value);
    }

    @UIField(order = 1)
    @UIFieldGroup(value = "GAUGE", borderColor = "#FF00FF", order = 100)
    public Boolean isUpdateOnMove() {
        return getJsonData("uom", Boolean.FALSE);
    }

    public void setUpdateOnMove(boolean value) {
        setJsonData("uom", value);
    }

    @Override
    public void beforePersist() {
        setOverflow(Overflow.hidden);
        if (!getJsonData().has("gfg")) {
            setGaugeForeground(UI.Color.random());
            setDotBorderColor(getGaugeForeground());
        }
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
}
