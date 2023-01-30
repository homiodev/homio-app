package org.touchhome.app.model.entity.widget.impl.display;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldJSONLine;
import org.touchhome.app.model.entity.widget.UIFieldLayout;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.impl.HasLayout;
import org.touchhome.app.model.entity.widget.impl.HasSourceServerUpdates;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.touchhome.app.model.entity.widget.impl.chart.HasLineChartBehaviour;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;
import org.touchhome.bundle.api.ui.field.condition.UIFieldShowOnCondition;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

@Entity
public class WidgetDisplayEntity
    extends WidgetBaseEntityAndSeries<WidgetDisplayEntity, WidgetDisplaySeriesEntity>
    implements HasLineChartBehaviour,
    HasDynamicParameterFields,
    HasChartDataSource,
    HasHorizontalLine,
    HasLayout,
    HasSourceServerUpdates {

    public static final String PREFIX = "wgtdp_";

    @Override
    public String getImage() {
        return "fas fa-tv";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @UIField(order = 50)
    @UIFieldLayout(
        options = {"name", "value", "icon"},
        rows = "1:10")
    public String getLayout() {
        return getJsonData("layout");
    }

    @Override
    @UIFieldColorPicker(allowThreshold = true)
    public String getBackground() {
        return super.getBackground();
    }

    @JsonIgnore
    @UIFieldIgnore
    public String getChartLabel() {
        return "";
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 20, max = 100)
    @UIFieldGroup("Chart ui")
    public int getChartHeight() {
        return getJsonData("ch", 30);
    }

    public void setChartHeight(int value) {
        setJsonData("ch", value);
    }

    @UIField(order = 20)
    @UIFieldJSONLine(
        template = "{\"top\": number}, \"left\": number, \"bottom\": number, \"right\": number")
    @UIFieldGroup("Chart ui")
    @UIFieldShowOnCondition("return context.get('chartType') == 'bar'")
    public String getBarBorderWidth() {
        return getJsonData("bbw", "{\"top\": 0, \"left\": 0, \"bottom\": 0, \"right\": 0}");
    }

    public void setBarBorderWidth(String value) {
        setJsonData("bbw", value);
    }

    @UIField(order = 7)
    @UIFieldGroup("Chart")
    public ChartType getChartType() {
        return getJsonDataEnum("ct", ChartType.line);
    }

    public void setChartType(ChartType value) {
        setJsonData("ct", value);
    }

    @Override
    public String getDefaultName() {
        return null;
    }

    @Override
    protected void beforePersist() {
        super.beforePersist();
        setLayout(UIFieldLayout.LayoutBuilder
            .builder(50, 50)
            .addRow(rb -> rb
                .addCol("name", UIFieldLayout.HorizontalAlign.left)
                .addCol("icon", UIFieldLayout.HorizontalAlign.right))
            .addRow(rb -> rb
                .addCol("value", UIFieldLayout.HorizontalAlign.left)
                .addCol("none", UIFieldLayout.HorizontalAlign.center))
            .addRow(rb -> rb
                .addCol("none", UIFieldLayout.HorizontalAlign.center)
                .addCol("none", UIFieldLayout.HorizontalAlign.center))
            .build());
    }
}
