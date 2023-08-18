package org.homio.app.builder.widget;

import java.util.Objects;
import java.util.function.Consumer;

import lombok.Getter;
import org.homio.api.EntityContextWidget.PulseBuilder;
import org.homio.api.EntityContextWidget.ThresholdBuilder;
import org.homio.api.EntityContextWidget.WidgetBaseBuilder;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.model.entity.widget.WidgetBaseEntity;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.model.entity.widget.impl.WidgetLayoutEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class WidgetBaseBuilderImpl<T, W extends WidgetBaseEntity> implements WidgetBaseBuilder<T> {

    protected final W widget;
    private final EntityContextImpl entityContext;

    WidgetBaseBuilderImpl(W widget, EntityContextImpl entityContext) {
        this.widget = widget;
        this.entityContext = entityContext;
    }

    @Override
    public @NotNull T setZIndex(int index) {
        widget.setIndex(index);
        return (T) this;
    }

    @Override
    public @NotNull T setName(String name) {
        widget.setName(name);
        return (T) this;
    }

    @Override
    public @NotNull T setStyle(String... styles) {
        widget.setStyle(String.join("~~~", styles));
        return (T) this;
    }

    @Override
    public @NotNull T setBackground(@Nullable String backgroundColor,
                                    @Nullable Consumer<ThresholdBuilder> colorBuilder,
                                    @Nullable Consumer<PulseBuilder> pulseBuilder) {
        if (colorBuilder == null && pulseBuilder == null) {
            getWidget().setBackground(backgroundColor);
        } else {
            ThresholdBuilderImpl builder = new ThresholdBuilderImpl(backgroundColor);
            if (colorBuilder != null) {
                colorBuilder.accept(builder);
            }
            if (pulseBuilder != null) {
                pulseBuilder.accept(builder);
            }
            getWidget().setBackground(builder.build());
        }
        return (T) this;
    }

    @Override
    public @NotNull T setBackground(String value) {
        widget.setBackground(value);
        return (T) this;
    }

    @Override
    public @NotNull T attachToTab(@NotNull String name) {
        for (WidgetTabEntity widgetTabEntity : entityContext.findAll(WidgetTabEntity.class)) {
            if (Objects.equals(widgetTabEntity.getName(), name)) {
                widget.setWidgetTabEntity(widgetTabEntity);
                break;
            }
        }
        return (T) this;
    }

    @Override
    public @NotNull T attachToLayout(@NotNull String layoutEntityID, int rowNum, int columnNum) {
        WidgetLayoutEntity entity = entityContext.getEntity(WidgetLayoutEntity.class, layoutEntityID);
        if (entity == null) {
            throw new IllegalArgumentException("Unable to find layout: " + layoutEntityID);
        }
        String[] items = entity.getLayout().split("x");
        if (Integer.parseInt(items[1]) < rowNum + widget.getBh()) {
            throw new IllegalArgumentException("Unable to put widget into layout. No height left");
        }
        if (Integer.parseInt(items[0]) < columnNum + widget.getBw()) {
            throw new IllegalArgumentException("Unable to put widget into layout. No width left");
        }
        widget.setXb(columnNum);
        widget.setYb(rowNum);
        widget.setParent(entity.getEntityID());
        return (T) this;
    }

    @Override
    public @NotNull T setBlockSize(int width, int height) {
        widget.setBw(width);
        widget.setBh(height);
        return (T) this;
    }
}
