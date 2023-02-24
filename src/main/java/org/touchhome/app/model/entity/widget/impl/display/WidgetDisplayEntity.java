package org.touchhome.app.model.entity.widget.impl.display;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import org.touchhome.app.model.entity.widget.UIFieldJSONLine;
import org.touchhome.app.model.entity.widget.UIFieldOptionFontSize;
import org.touchhome.app.model.entity.widget.WidgetBaseEntityAndSeries;
import org.touchhome.app.model.entity.widget.attributes.HasLayout;
import org.touchhome.app.model.entity.widget.attributes.HasName;
import org.touchhome.app.model.entity.widget.attributes.HasPadding;
import org.touchhome.app.model.entity.widget.attributes.HasActionOnClick;
import org.touchhome.app.model.entity.widget.attributes.HasSourceServerUpdates;
import org.touchhome.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.touchhome.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.touchhome.app.model.entity.widget.impl.chart.HasLineChartBehaviour;
import org.touchhome.bundle.api.EntityContextWidget.ChartType;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorPicker;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.UIFieldLayout;
import org.touchhome.bundle.api.ui.field.UIFieldLayout.HorizontalAlign;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;
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
    HasName,
    HasActionOnClick,
    HasPadding,
    HasSourceServerUpdates {

    public static final String PREFIX = "wgtdp_";

    @UIField(order = 1)
    @UIFieldGroup(value = "Name", order = 3)
    @UIFieldOptionFontSize
    public String getName() {
        return super.getName();
    }

    @Override
    public String getImage() {
        return "fas fa-tv";
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @UIField(order = 50, isRevert = true)
    @UIFieldLayout(options = {"name", "value", "icon"}, rows = "1:10")
    @UIFieldReadDefaultValue
    public String getLayout() {
        return getJsonData("layout", getDefaultLayout());
    }

    @Override
    @UIFieldColorPicker(allowThreshold = true, animateColorCondition = true)
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
        template = "{\"top\": number}, \"right\": number, \"bottom\": number, \"left\": number")
    @UIFieldGroup("Chart ui")
    @UIFieldShowOnCondition("return context.get('chartType') == 'bar'")
    public String getBarBorderWidth() {
        return getJsonData("bbw", "{\"top\": 0, \"right\": 0, \"bottom\": 0, \"left\": 0}");
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

    private String getDefaultLayout() {
        return UIFieldLayout.LayoutBuilder
            .builder(15, 50, 35)
            .addRow(rb -> rb
                .addCol("icon", HorizontalAlign.left)
                .addCol("name", UIFieldLayout.HorizontalAlign.left)
                .addCol("value", HorizontalAlign.right))
            .build();
    }
}
