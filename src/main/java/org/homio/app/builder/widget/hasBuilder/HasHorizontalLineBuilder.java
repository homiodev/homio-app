package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasHorizontalLine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasHorizontalLineBuilder<T extends org.homio.app.model.entity.widget.impl.chart.HasHorizontalLine, R>
        extends HasHorizontalLine<R> {

    T getWidget();

    @Override
    default @NotNull R setSingleLinePos(@Nullable Integer value) {
        getWidget().setSingleLinePos(value);
        return (R) this;
    }

    @Override
    default @NotNull R setSingleLineColor(@Nullable String value) {
        getWidget().setSingleLineColor(value);
        return (R) this;
    }

    @Override
    default @NotNull R setSingleLineWidth(@Nullable Integer value) {
        getWidget().setSingleLineWidth(value);
        return (R) this;
    }

    @Override
    default @NotNull R setDynamicLineColor(@Nullable String value) {
        getWidget().setDynamicLineColor(value);
        return (R) this;
    }

    @Override
    default @NotNull R setDynamicLineWidth(@Nullable Integer value) {
        getWidget().setDynamicLineWidth(value);
        return (R) this;
    }
}
