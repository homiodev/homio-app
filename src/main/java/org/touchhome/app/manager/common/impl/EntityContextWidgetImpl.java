package org.touchhome.app.manager.common.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetTabEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.bundle.api.EntityContextWidget;

import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.defaultString;
import static org.touchhome.app.model.entity.widget.WidgetTabEntity.GENERAL_WIDGET_TAB_NAME;

@Log4j2
@RequiredArgsConstructor
public class EntityContextWidgetImpl implements EntityContextWidget {
    @Getter
    private final EntityContextImpl entityContext;

    @Override
    public void createLineChartWidget(String entityID, String name, Consumer<LineChartSeriesBuilder> chartBuilder,
                                      Consumer<LineChartWidgetBuilder> lineChartWidgetBuilder, String tabEntityID) {
        if (!entityID.startsWith(EntityContextWidget.LINE_CHART_WIDGET_PREFIX)) {
            throw new IllegalArgumentException(
                    "Line chart widget must starts with prefix: " + EntityContextWidget.LINE_CHART_WIDGET_PREFIX);
        }
        WidgetLineChartEntity widgetLineChartEntity = new WidgetLineChartEntity()
                .setEntityID(entityID).setName(name);
        LineChartWidgetBuilder builder = new LineChartWidgetBuilder() {

            @Override
            public LineChartWidgetBuilder showAxisX(boolean on) {
                widgetLineChartEntity.setShowAxisX(on);
                return this;
            }

            @Override
            public LineChartWidgetBuilder showAxisY(boolean on) {
                widgetLineChartEntity.setShowAxisY(on);
                return this;
            }

            @Override
            public LineChartWidgetBuilder axisLabelX(String name) {
                widgetLineChartEntity.setAxisLabelX(name);
                return this;
            }

            @Override
            public LineChartWidgetBuilder axisLabelY(String name) {
                widgetLineChartEntity.setAxisLabelY(name);
                return this;
            }
        };
        lineChartWidgetBuilder.accept(builder);

        WidgetTabEntity tabEntity = entityContext.getEntity(defaultString(tabEntityID, GENERAL_WIDGET_TAB_NAME));

        widgetLineChartEntity.setWidgetTabEntity(tabEntity);

        WidgetLineChartEntity savedWidget = entityContext.save(widgetLineChartEntity);

        LineChartSeriesBuilder lineChartSeriesBuilder = (color, lineChartSeries) -> {
            WidgetLineChartSeriesEntity seriesEntity = new WidgetLineChartSeriesEntity();
            seriesEntity.setChartColor(color);
            seriesEntity.setChartDataSource(lineChartSeries.getEntityID());
            seriesEntity.setWidgetEntity(savedWidget);
            entityContext.save(seriesEntity);
        };
        chartBuilder.accept(lineChartSeriesBuilder);
    }
}
