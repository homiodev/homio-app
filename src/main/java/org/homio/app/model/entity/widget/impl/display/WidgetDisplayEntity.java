package org.homio.app.model.entity.widget.impl.display;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.Entity;
import lombok.SneakyThrows;
import org.homio.api.ContextWidget.ChartType;
import org.homio.api.ui.field.*;
import org.homio.api.ui.field.UIFieldLayout.HorizontalAlign;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.dynamic.HasDynamicParameterFields;
import org.homio.app.model.entity.widget.UIFieldJSONLine;
import org.homio.app.model.entity.widget.WidgetEntityAndSeries;
import org.homio.app.model.entity.widget.attributes.HasActionOnClick;
import org.homio.app.model.entity.widget.attributes.HasLayout;
import org.homio.app.model.entity.widget.attributes.HasMargin;
import org.homio.app.model.entity.widget.attributes.HasName;
import org.homio.app.model.entity.widget.impl.chart.HasChartDataSource;
import org.homio.app.model.entity.widget.impl.chart.HasHorizontalLine;
import org.homio.app.model.entity.widget.impl.chart.HasLineChartBehaviour;
import org.homio.app.model.rest.EntityUIMetaData;
import org.homio.app.utils.UIFieldUtils.ConfigureFieldsService;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@SuppressWarnings("unused")
@Entity
public class WidgetDisplayEntity
        extends WidgetEntityAndSeries<WidgetDisplayEntity, WidgetDisplaySeriesEntity>
        implements HasLineChartBehaviour,
        HasDynamicParameterFields,
        HasChartDataSource,
        HasHorizontalLine,
        HasLayout,
        HasName,
        HasActionOnClick,
        HasMargin,
        ConfigureFieldsService {

    @UIField(order = 1)
    @UIFieldGroup(value = "NAME", order = 3)
    public String getName() {
        return super.getName();
    }

    @Override
    public @NotNull String getImage() {
        return "fas fa-tv";
    }

    @Override
    protected @NotNull String getWidgetPrefix() {
        return "display";
    }

    @UIField(order = 50, hideInView = true)
    @UIFieldLayout(options = {"name", "value", "icon"}, rows = "1:10")
    public String getLayout() {
        return getJsonData("layout", getDefaultLayout());
    }

    @JsonIgnore
    @UIFieldIgnore
    public String getChartLabel() {
        return "";
    }

    @UIField(order = 4)
    @UIFieldSlider(min = 20, max = 100)
    @UIFieldGroup("CHART_UI")
    public int getChartHeight() {
        return getJsonData("ch", 30);
    }

    public void setChartHeight(int value) {
        setJsonData("ch", value);
    }

    @UIField(order = 20)
    @UIFieldJSONLine(
            template = "{\"top\": number}, \"right\": number, \"bottom\": number, \"left\": number")
    @UIFieldGroup("CHART_UI")
    @UIFieldShowOnCondition("return context.get('chartType') == 'bar'")
    public String getBarBorderWidth() {
        return getJsonData("bbw", "{\"top\": 0, \"right\": 0, \"bottom\": 0, \"left\": 0}");
    }

    public void setBarBorderWidth(String value) {
        setJsonData("bbw", value);
    }

    @UIField(order = 7)
    @UIFieldGroup("CHART")
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

    @UIField(order = 8)
    @UIFieldGroup("CHART_UI")
    public boolean getShowChartFullScreenButton() {
        return getJsonData("sfsb", Boolean.FALSE);
    }

    public void setShowChartFullScreenButton(boolean value) {
        setJsonData("sfsb", value);
    }

    @UIField(order = 2)
    @UIFieldGroup("CHART")
    public boolean isShowChart() {
        return getJsonData("showC", true);
    }

    public void setShowChart(boolean value) {
        setJsonData("showC", value);
    }

    @Override
    @UIFieldColorPicker
    public String getNameColor() {
        return HasName.super.getNameColor();
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

    /**
     * Scan all ui fields if they has 'GROUP.CHART' group and put it into separate tab
     */
    @SneakyThrows
    @Override
    public void configure(@NotNull List<EntityUIMetaData> result) {
        for (EntityUIMetaData entityUIMetaData : result) {
            ObjectNode meta = OBJECT_MAPPER.readValue(entityUIMetaData.getTypeMetaData(), ObjectNode.class);
            if (meta.path("group").asText().startsWith("GROUP.CHART")) {
                meta.put("tab", "CHART");
                entityUIMetaData.setTypeMetaData(OBJECT_MAPPER.writeValueAsString(meta));
            }
        }
    }

    @Override
    public void beforePersist() {
        setOverflow(Overflow.hidden);
    }
}
