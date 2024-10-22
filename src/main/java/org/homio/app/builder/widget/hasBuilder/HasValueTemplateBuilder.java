package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasValueTemplate;
import org.homio.api.ContextWidget.VerticalAlign;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

public interface HasValueTemplateBuilder<T extends org.homio.app.model.entity.widget.attributes.HasValueTemplate, R>
        extends HasValueTemplate<R> {

    T getWidget();

    @Override
    default @NotNull R setValueTemplate(@Nullable String prefix, @Nullable String suffix) {
        getWidget().setValueTemplate(trimToEmpty(prefix) + LIST_DELIMITER + trimToEmpty(suffix));
        return (R) this;
    }

    @Override
    default @NotNull R setValueColor(@Nullable String value) {
        getWidget().setValueColor(value);
        return (R) this;
    }

    @Override
    default @NotNull R setNoValueText(@Nullable String value) {
        getWidget().setNoValueText(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueFontSize(double value) {
        getWidget().setValueFontSize(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixFontSize(double value) {
        getWidget().setValuePrefixFontSize(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixFontSize(double value) {
        getWidget().setValueSuffixFontSize(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().setValuePrefixVerticalAlign(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().setValueSuffixVerticalAlign(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().setValueVerticalAlign(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixColor(String value) {
        getWidget().setValuePrefixColor(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixColor(String value) {
        getWidget().setValueSuffixColor(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSourceClickHistory(boolean value) {
        getWidget().setValueSourceClickHistory(value);
        return (R) this;
    }
}
