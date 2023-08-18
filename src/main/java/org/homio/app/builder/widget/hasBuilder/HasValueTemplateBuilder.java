package org.homio.app.builder.widget.hasBuilder;

import static org.apache.commons.lang3.StringUtils.trimToEmpty;

import org.homio.api.EntityContextWidget.HasValueTemplate;
import org.homio.api.EntityContextWidget.VerticalAlign;
import org.jetbrains.annotations.Nullable;

public interface HasValueTemplateBuilder<T extends org.homio.app.model.entity.widget.attributes.HasValueTemplate, R>
        extends HasValueTemplate<R> {

    T getWidget();

    @Override
    default R setValueTemplate(@Nullable String prefix, @Nullable String suffix) {
        getWidget().setValueTemplate(trimToEmpty(prefix) + "~~~" + trimToEmpty(suffix));
        return (R) this;
    }

    @Override
    default R setValueColor(@Nullable String value) {
        getWidget().setValueColor(value);
        return (R) this;
    }

    @Override
    default R setNoValueText(@Nullable String value) {
        getWidget().setNoValueText(value);
        return (R) this;
    }

    @Override
    default R setValueFontSize(double value) {
        getWidget().setValueFontSize(value);
        return (R) this;
    }

    @Override
    default R setValuePrefixFontSize(double value) {
        getWidget().setValuePrefixFontSize(value);
        return (R) this;
    }

    @Override
    default R setValueSuffixFontSize(double value) {
        getWidget().setValueSuffixFontSize(value);
        return (R) this;
    }

    @Override
    default R setValuePrefixVerticalAlign(VerticalAlign value) {
        getWidget().setValuePrefixVerticalAlign(value);
        return (R) this;
    }

    @Override
    default R setValueSuffixVerticalAlign(VerticalAlign value) {
        getWidget().setValueSuffixVerticalAlign(value);
        return (R) this;
    }

    @Override
    default R setValueVerticalAlign(VerticalAlign value) {
        getWidget().setValueVerticalAlign(value);
        return (R) this;
    }

    @Override
    default R setValuePrefixColor(String value) {
        getWidget().setValuePrefixColor(value);
        return (R) this;
    }

    @Override
    default R setValueSuffixColor(String value) {
        getWidget().setValueSuffixColor(value);
        return (R) this;
    }

    @Override
    default R setValueSourceClickHistory(boolean value) {
        getWidget().setValueSourceClickHistory(value);
        return (R) this;
    }
}
