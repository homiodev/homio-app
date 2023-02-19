package org.touchhome.app.builder.widget.hasBuilder;

import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContextWidget.HasName;
import org.touchhome.bundle.api.entity.BaseEntity;

public interface HasNameBuilder<T extends BaseEntity & org.touchhome.app.model.entity.widget.attributes.HasName, R>
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
