package org.homio.app.builder.widget.hasBuilder;

import org.homio.api.ContextWidget.HasActionOnClick;
import org.homio.api.entity.BaseEntity;
import org.jetbrains.annotations.Nullable;

public interface HasActionOnClickBuilder<T extends BaseEntity & org.homio.app.model.entity.widget.attributes.HasActionOnClick, R>
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
