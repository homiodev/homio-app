package org.touchhome.app.builder.widget.hasBuilder;

import org.jetbrains.annotations.Nullable;
import org.touchhome.bundle.api.EntityContextWidget.HasActionOnClick;
import org.touchhome.bundle.api.entity.BaseEntity;

public interface HasActionOnClickBuilder<T extends BaseEntity & org.touchhome.app.model.entity.widget.attributes.HasActionOnClick, R>
    extends HasActionOnClick<R> {

    T getWidget();

    @Override
    default R setValueToPushSource(String value) {
        getWidget().setSetValueDataSource(value);
        return (R) this;
    }

    @Override
    default R setValueOnClick(@Nullable String value) {
        getWidget().setValueOnClick(value);
        return (R) this;
    }

    @Override
    default R setValueOnDoubleClick(@Nullable String value) {
        getWidget().setValueOnDoubleClick(value);
        return (R) this;
    }

    @Override
    default R setValueOnHoldClick(@Nullable String value) {
        getWidget().setValueOnHoldClick(value);
        return (R) this;
    }

    @Override
    default R setValueOnHoldReleaseClick(@Nullable String value) {
        getWidget().setValueOnHoldReleaseClick(value);
        return (R) this;
    }

    @Override
    default R setValueToPushConfirmMessage(String value) {
        getWidget().setValueToPushConfirmMessage(value);
        return (R) this;
    }
}
