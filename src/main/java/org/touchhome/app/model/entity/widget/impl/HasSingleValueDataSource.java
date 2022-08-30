package org.touchhome.app.model.entity.widget.impl;

import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasSingleValueDataSource<T> extends HasDynamicParameterFields<T> {

    @UIField(order = 10, required = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    @UIFieldGroup(value = "Value", order = 10)
    default String getValueDataSource() {
        return getJsonData("vds");
    }

    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "Value")
    default String getSetValueDataSource() {
        return getJsonData("svds");
    }

    @UIField(order = 10)
    @UIFieldGroup(value = "Value", order = 2)
    default AggregationType getAggregationType() {
        return getJsonDataEnum("aggrType", AggregationType.Last);
    }

    default void setAggregationType(AggregationType value) {
        setJsonData("aggrType", value);
    }

    default void setValueDataSource(String value) {
        setJsonData("vds", value);
    }

    default void setSetValueDataSource(String value) {
        setJsonData("svds", value);
    }
}
