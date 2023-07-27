package org.homio.app.builder.widget;

import static org.homio.app.model.entity.widget.WidgetTabEntity.GENERAL_WIDGET_TAB_NAME;

import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.EntityContextWidget;
import org.homio.api.model.OptionModel;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.homio.app.model.entity.widget.impl.WidgetSimpleValueEntity;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartEntity;
import org.homio.app.model.entity.widget.impl.chart.bar.WidgetBarTimeChartSeriesEntity;
import org.homio.app.model.entity.widget.impl.chart.line.WidgetLineChartEntity;
import org.homio.app.model.entity.widget.impl.chart.line.WidgetLineChartSeriesEntity;
import org.homio.app.model.entity.widget.impl.color.WidgetColorEntity;
import org.homio.app.model.entity.widget.impl.color.WidgetSimpleColorEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplayEntity;
import org.homio.app.model.entity.widget.impl.display.WidgetDisplaySeriesEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderEntity;
import org.homio.app.model.entity.widget.impl.slider.WidgetSliderSeriesEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetSimpleToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleEntity;
import org.homio.app.model.entity.widget.impl.toggle.WidgetToggleSeriesEntity;
import org.jetbrains.annotations.NotNull;

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
        WidgetLayoutEntity widget = createStubWidget(entityID, new WidgetLayoutEntity());
        LayoutBuilderImpl builder = new LayoutBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createDisplayWidget(@NotNull String entityID, @NotNull Consumer<DisplayWidgetBuilder> widgetBuilder) {
        WidgetDisplayEntity widget = createStubWidget(entityID, new WidgetDisplayEntity());
        DisplayBuilderImpl builder = new DisplayBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetDisplayEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetDisplaySeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
    }

    @Override
    public void createSimpleValueWidget(@NotNull String entityID, @NotNull Consumer<SimpleValueWidgetBuilder> widgetBuilder) {
        WidgetSimpleValueEntity widget = createStubWidget(entityID, new WidgetSimpleValueEntity());
        SimpleValueBuilderImpl builder = new SimpleValueBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createToggleWidget(@NotNull String entityID, @NotNull Consumer<ToggleWidgetBuilder> widgetBuilder) {
        WidgetToggleEntity widget = createStubWidget(entityID, new WidgetToggleEntity());
        ToggleBuilderImpl builder = new ToggleBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetToggleEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetToggleSeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
    }

    @Override
    public void createSimpleToggleWidget(@NotNull String entityID, @NotNull Consumer<SimpleToggleWidgetBuilder> widgetBuilder) {
        WidgetSimpleToggleEntity widget = createStubWidget(entityID, new WidgetSimpleToggleEntity());
        SimpleToggleBuilderImpl builder = new SimpleToggleBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createSliderWidget(@NotNull String entityID, @NotNull Consumer<SliderWidgetBuilder> widgetBuilder) {
        WidgetSliderEntity widget = createStubWidget(entityID, new WidgetSliderEntity());
        SliderBuilderImpl builder = new SliderBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetSliderEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetSliderSeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
    }

    @Override
    public void createSimpleColorWidget(@NotNull String entityID, @NotNull Consumer<SimpleColorWidgetBuilder> widgetBuilder) {
        WidgetSimpleColorEntity widget = createStubWidget(entityID, new WidgetSimpleColorEntity());
        SimpleColorBuilderImpl builder = new SimpleColorBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createColorWidget(
        @NotNull String entityID,
        @NotNull Consumer<ColorWidgetBuilder> widgetBuilder) {

        WidgetColorEntity widget = createStubWidget(entityID, new WidgetColorEntity());
        ColorWidgetBuilderImpl builder = new ColorWidgetBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        entityContext.save(builder.getWidget());
    }

    @Override
    public void createBarTimeChartWidget(@NotNull String entityID, @NotNull Consumer<BarTimeChartBuilder> widgetBuilder) {
        WidgetBarTimeChartEntity widget = createStubWidget(entityID, new WidgetBarTimeChartEntity());
        BarTimeChartBuilderImpl builder = new BarTimeChartBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetBarTimeChartEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetBarTimeChartSeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
    }

    @Override
    public void createLineChartWidget(
        @NotNull String entityID,
        @NotNull Consumer<LineChartBuilder> widgetBuilder) {
        WidgetLineChartEntity widget = createStubWidget(entityID, new WidgetLineChartEntity());
        LineChartBuilderImpl builder = new LineChartBuilderImpl(widget, entityContext);
        widgetBuilder.accept(builder);
        WidgetLineChartEntity savedWidget = entityContext.save(builder.getWidget());
        for (WidgetLineChartSeriesEntity entity : builder.getSeries()) {
            entity.setWidgetEntity(savedWidget);
            entityContext.save(entity);
        }
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
    private <T extends WidgetBaseEntity> T createStubWidget(@NotNull String entityID, T widget) {
        entityID = ensureWidgetNotExists(entityID, widget.getEntityPrefix());

        WidgetTabEntity generalTabEntity = entityContext.getEntity(GENERAL_WIDGET_TAB_NAME);
        widget.setEntityID(entityID);
        widget.setWidgetTabEntity(generalTabEntity);
        return widget;
    }
}
