package org.touchhome.app.model.entity.widget.impl.gauge;

import org.touchhome.app.model.entity.widget.SeriesBuilder;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.widget.WidgetBaseEntity;
import org.touchhome.bundle.api.entity.workspace.WorkspaceStandaloneVariableEntity;
import org.touchhome.bundle.api.entity.workspace.var.WorkspaceVariableEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.ui.action.DynamicOptionLoader;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldNumber;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.bundle.api.ui.field.UIKeyValueField;
import org.touchhome.bundle.api.ui.field.selection.UIFieldSelection;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class WidgetGaugeEntity extends WidgetBaseEntity<WidgetGaugeEntity> {

    public static final String PREFIX = "wtgg_";

    @UIField(order = 14, required = true)
    @UIFieldSelection(GaugeDataSourceDynamicOptionLoader.class)
    public String getDataSource() {
        return getJsonData("ds");
    }

    public WidgetGaugeEntity setDataSource(String value) {
        setJsonData("ds", value);
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
    public Integer getThick() {
        return getJsonData("thick", 6);
    }

    public WidgetGaugeEntity setThick(Integer value) {
        setJsonData("thick", value);
        return this;
    }

    @UIField(order = 19)
    public LineType getGaugeCapType() {
        return getJsonDataEnum("gaugeCapType", LineType.round);
    }

    public WidgetGaugeEntity setGaugeCapType(Integer value) {
        setJsonData("gaugeCapType", value);
        return this;
    }

    @UIField(order = 20, type = UIFieldType.Color)
    public String getForeground() {
        return getJsonData("fg", "#009688");
    }

    public WidgetGaugeEntity setForeground(String value) {
        setJsonData("fg", value);
        return this;
    }

    @UIField(order = 21, type = UIFieldType.Color)
    public String getBackground() {
        return getJsonData("bg", "rgba(0, 0, 0, 0.1)");
    }

    public WidgetGaugeEntity setBackground(String value) {
        setJsonData("bg", value);
        return this;
    }

    @UIField(order = 22)
    public String getPrepend() {
        return getJsonData("prepend");
    }

    public WidgetGaugeEntity setPrepend(String value) {
        setJsonData("prepend", value);
        return this;
    }

    @UIField(order = 23)
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

    @UIField(order = 25)
    @UIKeyValueField(maxSize = 5, keyType = UIFieldType.Float, valueType = UIFieldType.Color, defaultKey = "0", defaultValue = "#FFFFFF")
    public String getThreshold() {
        return getJsonData("threshold", "{}");
    }

    public WidgetGaugeEntity setThreshold(String value) {
        setJsonData("threshold", value);
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

    public static class GaugeDataSourceDynamicOptionLoader implements DynamicOptionLoader {

        @Override
        public List<OptionModel> loadOptions(BaseEntity baseEntity, EntityContext entityContext) {
            return SeriesBuilder.seriesOptions()
                    .add(WorkspaceStandaloneVariableEntity.class)
                    .add(WorkspaceVariableEntity.class)
                    .build(entityContext);
        }
    }
}
