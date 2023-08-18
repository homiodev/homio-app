package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.EntityContextWidget.HasName;
import org.homio.api.entity.BaseEntity;
import org.jetbrains.annotations.Nullable;

public interface HasNameBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.attributes.HasName, R>
        extends HasName<R> {

    T getWidget();

    @Override
    default R setName(@Nullable String value) {
        getWidget().setName(value);
        return (R) this;
    }

    @Override
    default R setShowName(boolean value) {
        getWidget().setShowName(value);
        return (R) this;
    }

    @Override
    default R setNameColor(@Nullable String value) {
        getWidget().setNameColor(value);
        return (R) this;
    }
}
