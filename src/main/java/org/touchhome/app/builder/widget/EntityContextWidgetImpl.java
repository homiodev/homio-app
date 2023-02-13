package org.touchhome.app.builder.widget;

import static org.touchhome.app.model.entity.widget.WidgetTabEntity.GENERAL_WIDGET_TAB_NAME;

import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.WidgetTabEntity;
import org.touchhome.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.touchhome.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.touchhome.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.touchhome.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
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
    public void createLayoutWidget(@NotNull String entityID, @NotNull Consumer<LayoutWidgetBuilder> widgetBuilder) {
        WidgetLayoutEntity widget = createStubWidget(entityID, new WidgetLayoutEntity(), WidgetLayoutEntity.PREFIX);
        LayoutBuilderImpl builder = new LayoutBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createDisplayWidget(@NotNull String entityID, @NotNull Consumer<DisplayWidgetBuilder> widgetBuilder) {
        WidgetDisplayEntity widget = createStubWidget(entityID, new WidgetDisplayEntity(), WidgetDisplayEntity.PREFIX);
        DisplayBuilderImpl builder = new DisplayBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetDisplayEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetDisplaySeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
    }

    @Override
    public void createToggleWidget(@NotNull String entityID, @NotNull Consumer<ToggleWidgetBuilder> widgetBuilder) {
        WidgetToggleEntity widget = createStubWidget(entityID, new WidgetToggleEntity(), WidgetToggleEntity.PREFIX);
        ToggleBuilderImpl builder = new ToggleBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetToggleEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetToggleSeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
    }

    @Override
    public void createColorWidget(
        @NotNull String entityID,
        @NotNull Consumer<ColorWidgetBuilder> widgetBuilder) {

        WidgetColorEntity widget = createStubWidget(entityID, new WidgetColorEntity(), WidgetColorEntity.PREFIX);
        ColorWidgetBuilderImpl builder = new ColorWidgetBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createLineChartWidget(
        @NotNull String entityID,
        @NotNull Consumer<LineChartBuilder> chartBuilder) {

       /* WidgetLineChartEntity widget = createStubWidget(entityID, new WidgetLineChartEntity(), LINE_CHART_WIDGET_PREFIX);
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
        chartBuilder.accept(lineChartSeriesBuilder);*/
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
