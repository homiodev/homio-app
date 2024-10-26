package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasValueTemplate;
import org.homio.api.ContextWidget.VerticalAlign;
import org.homio.api.ui.field.UIFieldStringTemplate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasValueTemplateBuilder<T extends org.homio.app.model.entity.widget.attributes.HasValueTemplate, R>
        extends HasValueTemplate<R> {

    T getWidget();

    @Override
    default @NotNull R setValueTemplate(@Nullable String prefix, @Nullable String suffix) {
        UIFieldStringTemplate.StringTemplate template = getWidget().getValueTemplate();
        template.setP(prefix).setS(suffix);
        return (R) this;
    }

    @Override
    default @NotNull R setValueColor(@Nullable String value) {
        getWidget().getValueTemplate().setVc(value);
        return (R) this;
    }

    @Override
    default @NotNull R setNoValueText(@Nullable String value) {
        getWidget().getValueTemplate().setNvt(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueFontSize(double value) {
        getWidget().getValueTemplate().setVfs(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixFontSize(double value) {
        getWidget().getValueTemplate().setPfs(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixFontSize(double value) {
        getWidget().getValueTemplate().setSfs(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().getValueTemplate().setPa(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().getValueTemplate().setSa(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().getValueTemplate().setVa(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixColor(String value) {
        getWidget().getValueTemplate().setPc(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixColor(String value) {
        getWidget().getValueTemplate().setSc(value);
        return (R) this;
    }

    @Override
    default @NotNull R setValueSourceClickHistory(boolean value) {
        getWidget().getValueTemplate().setHoc(value);
        return (R) this;
    }
}
