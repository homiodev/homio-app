package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasValueTemplate;
import org.homio.api.ContextWidget.VerticalAlign;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasValueTemplateBuilder<T extends org.homio.app.model.entity.widget.attributes.HasValueTemplate, R>
        extends HasValueTemplate<R> {

    T getWidget();

    @Override
    default @NotNull R setValueTemplate(@Nullable String prefix, @Nullable String suffix) {
        getWidget().applyValueTemplate(t -> t.setP(prefix).setS(suffix));
        return (R) this;
    }

    @Override
    default @NotNull R setValueColor(@Nullable String value) {
        getWidget().applyValueTemplate(t -> t.setVc(value));
        return (R) this;
    }

    @Override
    default @NotNull R setNoValueText(@Nullable String value) {
        getWidget().applyValueTemplate(t -> t.setNvt(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValueFontSize(double value) {
        assertFontSize(value);
        getWidget().applyValueTemplate(t -> t.setVfs(value));
        return (R) this;
    }

    private static void assertFontSize(double value) {
        if(value < 0 || value > 2) {
            throw new IllegalArgumentException("Font size must be between 0 and 2");
        }
    }

    @Override
    default @NotNull R setValuePrefixFontSize(double value) {
        assertFontSize(value);
        getWidget().applyValueTemplate(t -> t.setPfs(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixFontSize(double value) {
        assertFontSize(value);
        getWidget().applyValueTemplate(t -> t.setSfs(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().applyValueTemplate(t -> t.setPa(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().applyValueTemplate(t -> t.setSa(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValueVerticalAlign(@NotNull VerticalAlign value) {
        getWidget().applyValueTemplate(t -> t.setVa(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValuePrefixColor(String value) {
        getWidget().applyValueTemplate(t -> t.setPc(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValueSuffixColor(String value) {
        getWidget().applyValueTemplate(t -> t.setSc(value));
        return (R) this;
    }

    @Override
    default @NotNull R setValueSourceClickHistory(boolean value) {
        getWidget().applyValueTemplate(t -> t.setHoc(value));
        return (R) this;
    }
}
