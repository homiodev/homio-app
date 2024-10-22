package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasToggle;
import org.homio.api.entity.BaseEntity;
import org.jetbrains.annotations.NotNull;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

public interface HasToggleBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.impl.toggle.HasToggle, R>
        extends HasToggle<R> {

    T getWidget();

    @Override
    default @NotNull R setColor(String value) {
        getWidget().setToggleColor(value);
        return (R) this;
    }

    @Override
    default @NotNull R setOnName(String value) {
        getWidget().setOnName(value);
        return (R) this;
    }

    @Override
    default @NotNull R setOnValues(String... values) {
        getWidget().setOnValues(String.join(LIST_DELIMITER, values));
        return (R) this;
    }

    @Override
    default @NotNull R setOffName(String value) {
        getWidget().setOffName(value);
        return (R) this;
    }

    @Override
    default @NotNull R setPushToggleOffValue(String value) {
        getWidget().setPushToggleOffValue(value);
        return (R) this;
    }

    @Override
    default @NotNull R setPushToggleOnValue(String value) {
        getWidget().setPushToggleOnValue(value);
        return (R) this;
    }
}
