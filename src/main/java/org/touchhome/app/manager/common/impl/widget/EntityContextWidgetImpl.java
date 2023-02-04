package org.touchhome.app.manager.common.impl.widget;

import static org.touchhome.app.model.entity.widget.WidgetTabEntity.GENERAL_WIDGET_TAB_NAME;
import static org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity.LINE_CHART_WIDGET_PREFIX;

import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.WidgetTabEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.touchhome.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.touchhome.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.touchhome.bundle.api.EntityContextWidget;
import org.touchhome.bundle.api.model.OptionModel;

@Log4j2
@RequiredArgsConstructor
public class EntityContextWidgetImpl implements EntityContextWidget {

    @Getter private final EntityContextImpl entityContext;

    @Override
    public @NotNull List<OptionModel> getDashboardTabs() {
        return OptionModel.entityList(entityContext.findAll(WidgetTabEntity.class));
    }

    @Override
    public @NotNull String getDashboardDefaultID() {
        return GENERAL_WIDGET_TAB_NAME;
    }

    @Override
    public void createColorWidget(
        @NotNull String entityID,
        @NotNull String name,
        @NotNull Consumer<ColorWidgetBuilder> widgetBuilder) {

        WidgetColorEntity widget = createStubWidget(entityID, new WidgetColorEntity(), WidgetColorEntity.PREFIX);
        ColorWidgetBuilderImpl builder = new ColorWidgetBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createLineChartWidget(
        @NotNull String entityID,
        @NotNull String name,
        @NotNull Consumer<LineChartSeriesBuilder> chartBuilder,
        @NotNull Consumer<LineChartWidgetBuilder> lineChartWidgetBuilder) {

        WidgetLineChartEntity widget = createStubWidget(entityID, new WidgetLineChartEntity(), LINE_CHART_WIDGET_PREFIX);
        LineChartWidgetBuilderImpl builder = new LineChartWidgetBuilderImpl(widget, entityContext);
        lineChartWidgetBuilder.accept(builder);

        WidgetLineChartEntity savedWidget = entityContext.save(widget);

        LineChartSeriesBuilder lineChartSeriesBuilder =
            (color, lineChartSeries) -> {
                WidgetLineChartSeriesEntity seriesEntity = new WidgetLineChartSeriesEntity();
                seriesEntity.setChartColor(color);
                seriesEntity.setChartDataSource(lineChartSeries.getEntityID());
                seriesEntity.setWidgetEntity(savedWidget);
                entityContext.save(seriesEntity);
            };
        chartBuilder.accept(lineChartSeriesBuilder);
    }

    private String ensureWidgetNotExists(@NotNull String entityID, String prefix) {
        if (!entityID.startsWith(prefix)) {
            entityID = prefix + entityID;
        }
        if (entityContext.getEntity(entityID) != null) {
            throw new IllegalArgumentException("Widget with such entityID already exists");
        }
        return entityID;
    }

    @SuppressWarnings("rawtypes")
    private <T extends WidgetBaseEntity> T createStubWidget(@NotNull String entityID, T widget, String prefix) {
        entityID = ensureWidgetNotExists(entityID, prefix);

        WidgetTabEntity generalTabEntity = entityContext.getEntity(GENERAL_WIDGET_TAB_NAME);
        widget.setEntityID(entityID);
        widget.setWidgetTabEntity(generalTabEntity);
        return widget;
    }
}
