package org.touchhome.app.model.entity.widget.impl.gauge;

import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.field.*;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

import javax.persistence.Entity;

@Entity
public class WidgetGaugeEntity extends WidgetBaseEntity<WidgetGaugeEntity> implements HasDynamicParameterFields {

    public static final String PREFIX = "wgtgg_";

    @UIField(order = 14, required = true)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    public WidgetGaugeEntity setDataSource(String value) {
        setJsonData("ds", value);
        return this;
    }

    @Override
    public void setDynamicParameterFieldsHolder(JSONObject value) {
        setJsonData("dsp", value);
    }

    @Override
    public JSONObject getDynamicParameterFieldsHolder() {
        return getJsonData().optJSONObject("dsp");
    }

    @UIField(order = 15)
    public AggregationType getAggregationType() {
        return getJsonDataEnum("cvt", AggregationType.Last);
    }

    public WidgetGaugeEntity setAggregationType(AggregationType value) {
        setJsonData("cvt", value);
        return this;
    }

    @UIField(order = 15)
    public GaugeType getDisplayType() {
        return getJsonDataEnum("displayType", GaugeType.arch);
    }

    public WidgetGaugeEntity setDisplayType(GaugeType value) {
        setJsonData("displayType", value);
        return this;
    }

    @UIField(order = 16, label = "gauge.min")
    public Integer getMin() {
        return getJsonData("min", 0);
    }

    public WidgetGaugeEntity setMin(Integer value) {
        setJsonData("min", value);
        return this;
    }

    @UIField(order = 17, label = "gauge.max")
    public Integer getMax() {
        return getJsonData("max", 255);
    }

    public WidgetGaugeEntity setMax(Integer value) {
        setJsonData("max", value);
        return this;
    }

    @UIField(order = 18, type = UIFieldType.Slider, label = "gauge.thick")
    @UIFieldNumber(min = 1, max = 10)
    @UIFieldGroup("UI")
    public Integer getThick() {
        return getJsonData("thick", 6);
    }

    public WidgetGaugeEntity setThick(Integer value) {
        setJsonData("thick", value);
        return this;
    }

    @UIField(order = 19)
    @UIFieldGroup("UI")
    public LineType getGaugeCapType() {
        return getJsonDataEnum("gaugeCapType", LineType.round);
    }

    public WidgetGaugeEntity setGaugeCapType(LineType lineType) {
        setJsonDataEnum("gaugeCapType", lineType);
        return this;
    }

    @UIField(order = 20)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getForeground() {
        return getJsonData("fg", "#009688");
    }

    public WidgetGaugeEntity setForeground(String value) {
        setJsonData("fg", value);
        return this;
    }

    @UIField(order = 22)
    @UIFieldGroup("UI")
    public String getPrepend() {
        return getJsonData("prepend");
    }

    public WidgetGaugeEntity setPrepend(String value) {
        setJsonData("prepend", value);
        return this;
    }

    @UIField(order = 23)
    @UIFieldGroup("UI")
    public String getAppend() {
        return getJsonData("append");
    }

    public WidgetGaugeEntity setAppend(String value) {
        setJsonData("append", value);
        return this;
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
    public String getImage() {
        return "fas fa-tachometer-alt";
    }

    @Override
    public boolean updateRelations(EntityContext entityContext) {
        String dataSource = getDataSource();
        if (dataSource != null && entityContext.getEntity(dataSource) == null) {
            this.setDataSource(null);
            return true;
        }
        return false;
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    public enum GaugeType {
        full, semi, arch
    }

    public enum LineType {
        round, butt
    }
}
