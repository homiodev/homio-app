package org.homio.app.builder.widget.hasBuilder;

import java.util.function.Consumer;

import org.homio.api.EntityContextWidget.HasIcon;
import org.homio.api.EntityContextWidget.ThresholdBuilder;
import org.homio.api.entity.BaseEntity;
import org.homio.app.builder.widget.ThresholdBuilderImpl;
import org.jetbrains.annotations.Nullable;

public interface HasIconColorThresholdBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.attributes.HasIcon, R>
        extends HasIcon<R> {

    T getWidget();

    @Override
    default R setIcon(@Nullable String icon, @Nullable Consumer<ThresholdBuilder> iconBuilder) {
        if (iconBuilder == null) {
            getWidget().setIcon(icon);
        } else {
            ThresholdBuilderImpl builder = new ThresholdBuilderImpl(icon);
            iconBuilder.accept(builder);
            getWidget().setIcon(builder.build());
        }
        return (R) this;
    }

    @Override
    default R setIconColor(@Nullable String color, @Nullable Consumer<ThresholdBuilder> colorBuilder) {
        if (colorBuilder == null) {
            getWidget().setIconColor(color);
        } else {
            ThresholdBuilderImpl builder = new ThresholdBuilderImpl(color);
            colorBuilder.accept(builder);
            getWidget().setIconColor(builder.build());
        }
        return (R) this;
    }
}
