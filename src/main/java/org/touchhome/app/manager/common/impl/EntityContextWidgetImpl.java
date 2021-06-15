package org.touchhome.app.manager.common.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.entity.widget.WidgetTabEntity;

import java.util.function.Consumer;

@Log4j2
@RequiredArgsConstructor
public class EntityContextWidgetImpl implements EntityContextWidget {
    @Getter
    private final EntityContextImpl entityContext;

    @Override
    public void createLineChartWidget(String entityID, String name, Consumer<LineChartSeriesBuilder> chartBuilder,
                                      Consumer<LineChartWidgetBuilder> lineChartWidgetBuilder, WidgetTabEntity tabEntity) {
        if (!entityID.startsWith(EntityContextWidget.LINE_CHART_WIDGET_PREFIX)) {
            throw new IllegalArgumentException("Line chart widget must starts with prefix: " + EntityContextWidget.LINE_CHART_WIDGET_PREFIX);
        }
        WidgetLineChartEntity widgetLineChartEntity = new WidgetLineChartEntity()
                .setEntityID(entityID).setName(name);
        LineChartWidgetBuilder builder = new LineChartWidgetBuilder() {
            @Override
            public LineChartWidgetBuilder showButtons(boolean on) {
                widgetLineChartEntity.setShowButtons(on);
                return this;
            }

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

            @Override
            public LineChartWidgetBuilder timeline(String value) {
                widgetLineChartEntity.setTimeline(value);
                return this;
            }
        };
        lineChartWidgetBuilder.accept(builder);

        if (tabEntity == null) {
            tabEntity = entityContext.getEntity(WidgetTabEntity.GENERAL_WIDGET_TAB_NAME);
        }
        widgetLineChartEntity.setWidgetTabEntity(tabEntity);

        WidgetLineChartEntity savedWidget = entityContext.save(widgetLineChartEntity);

        LineChartSeriesBuilder lineChartSeriesBuilder = (color, lineChartSeries) -> {
            WidgetLineChartSeriesEntity seriesEntity = new WidgetLineChartSeriesEntity();
            seriesEntity.setColor(color);
            seriesEntity.setDataSource(lineChartSeries.getEntityID());
            seriesEntity.setWidgetEntity(savedWidget);
            entityContext.save(seriesEntity);
        };
        chartBuilder.accept(lineChartSeriesBuilder);
    }
}
