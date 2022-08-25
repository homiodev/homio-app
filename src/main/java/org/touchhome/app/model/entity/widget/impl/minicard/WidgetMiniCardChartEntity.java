package org.touchhome.app.model.entity.widget.impl.minicard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.touchhome.app.model.entity.widget.impl.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.HasSingleValueDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.ChartBaseEntity;
import org.touchhome.bundle.api.ui.TimePeriod;
import org.touchhome.bundle.api.ui.UI;
import org.touchhome.bundle.api.ui.field.*;

import javax.persistence.Entity;
import java.util.Set;

@Getter
@Setter
@Entity
public class WidgetMiniCardChartEntity
        extends ChartBaseEntity<WidgetMiniCardChartEntity, WidgetMiniCardChartSeriesEntity>
        implements HasChartDataSource<WidgetMiniCardChartEntity>,
        HasSingleValueDataSource<WidgetMiniCardChartEntity> {

    public static final String PREFIX = "wgtmcc_";

    @Override
    public String getImage() {
        return "fas fa-diagram-successor";
    }

    @UIField(order = 2)
    @UIFieldGroup("Value")
    public String getUnit() {
        return getJsonData("unit", "°C");
    }

    @UIField(order = 3)
    @UIFieldGroup("Value")
    @UIFieldSlider(min = 14, max = 40)
    public int getValueFontSize() {
        return getJsonData("vfs", 28);
    }

    // ### icon group

    @UIField(order = 1)
    @UIFieldIconPicker(allowThreshold = true, allowEmptyIcon = true)
    @UIFieldGroup(value = "UI", order = 2)
    public String getIcon() {
        return getJsonData("icon", "fas fa-temperature-half");
    }

    public void setIcon(String value) {
        setJsonData("icon", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getIconColor() {
        return getJsonData("iconColor", "#44739E");
    }

    public void setIconColor(String value) {
        setJsonData("iconColor", value);
    }

    @UIField(order = 3)
    @UIFieldGroup("UI")
    @UIFieldColorPicker(allowThreshold = true)
    public String getTextColor() {
        return getJsonData("txtCol", UI.Color.WHITE);
    }

    public void setTextColor(String value) {
        setJsonData("txtCol", value);
    }

    @Override
    protected void beforePersist() {
        setInitChartColor(UI.Color.random());
    }

    // ### text group

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    // ### ignore UIFields!!!
    @Override
    @UIFieldIgnore
    @JsonIgnore
    public TimePeriod getTimePeriod() {
        throw new IllegalStateException("MNC");
    }

    @Override
    @UIFieldIgnore
    public Boolean getLegendShow() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public LegendPosition getLegendPosition() {
        return super.getLegendPosition();
    }

    @Override
    @UIFieldIgnore
    public LegendAlign getLegendAlign() {
        return super.getLegendAlign();
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowAxisX() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowAxisY() {
        return false;
    }

    @Override
    @UIFieldIgnore
    public String getAxisLabelX() {
        return super.getAxisLabelX();
    }

    @Override
    @UIFieldIgnore
    public String getAxisLabelY() {
        return super.getAxisLabelY();
    }

    @Override
    public boolean getShowDataLabels() {
        return getJsonData("sdl", false);
    }

    @Override
    @UIFieldIgnore
    public Boolean getShowTimeButtons() {
        return false;
    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public Set<WidgetMiniCardChartSeriesEntity> getSeries() {
        throw new IllegalStateException("MNC");
    }

    public void setUnit(String value) {
        setJsonData("unit", value);
    }

    public void setValueFontSize(int value) {
        setJsonData("vfs", value);
    }
}
