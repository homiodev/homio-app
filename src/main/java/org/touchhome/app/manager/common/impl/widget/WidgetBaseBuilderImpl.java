package org.touchhome.app.manager.common.impl.widget;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.model.entity.widget.WidgetBaseEntity;
import org.touchhome.app.model.entity.widget.WidgetTabEntity;
import org.touchhome.bundle.api.EntityContextWidget.WidgetBaseBuilder;

@Getter
public class WidgetBaseBuilderImpl<T, W extends WidgetBaseEntity> implements WidgetBaseBuilder<T> {

    protected final W widget;
    private final EntityContextImpl entityContext;

    WidgetBaseBuilderImpl(W widget, EntityContextImpl entityContext) {
        this.widget = widget;
        this.entityContext = entityContext;
    }

    @Override
    public T setName(String name) {
        widget.setName(name);
        return (T) this;
    }

    @Override
    public T attachToTab(@NotNull String name) {
        for (WidgetTabEntity widgetTabEntity : entityContext.findAll(WidgetTabEntity.class)) {
            if (widgetTabEntity.getName().equals(name)) {
                widget.setWidgetTabEntity(widgetTabEntity);
                break;
            }
        }
        return (T) this;
    }

    @Override
    public T setBlockSize(int width, int height) {
        widget.setBw(width);
        widget.setBh(height);
        return (T) this;
    }
}
