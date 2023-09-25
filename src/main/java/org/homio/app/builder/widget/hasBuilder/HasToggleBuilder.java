package org.homio.app.builder.widget.hasBuilder;

import static org.homio.api.entity.HasJsonData.LIST_DELIMITER;

import org.homio.api.EntityContextWidget.HasToggle;
import org.homio.api.entity.BaseEntity;

public interface HasToggleBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.impl.toggle.HasToggle, R>
        extends HasToggle<R> {

    T getWidget();

    @Override
    default R setColor(String value) {
        getWidget().setColor(value);
        return (R) this;
    }

    @Override
    default R setOnName(String value) {
        getWidget().setOnName(value);
        return (R) this;
    }

    @Override
    default R setOnValues(String... values) {
        getWidget().setOnValues(String.join(LIST_DELIMITER, values));
        return (R) this;
    }

    @Override
    default R setOffName(String value) {
        getWidget().setOffName(value);
        return (R) this;
    }

    @Override
    default R setPushToggleOffValue(String value) {
        getWidget().setPushToggleOffValue(value);
        return (R) this;
    }

    @Override
    default R setPushToggleOnValue(String value) {
        getWidget().setPushToggleOnValue(value);
        return (R) this;
    }
}
