package org.touchhome.app.model.entity.widget.impl;

import org.apache.commons.lang3.tuple.Pair;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasSingleValueDataSource extends HasDynamicParameterFields {

    /**
     * ValueDataSource must contains dataSource~~~UIFieldEntityByClassSelection
     */
    @UIField(order = 10, required = true)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    @UIFieldGroup(value = "Value", order = 10)
    @UIEditReloadWidget
    default String getValueDataSource() {
        return getJsonData("vds");
    }

    default Pair<String, String> getSingleValueDataSource() {
        String data = getJsonData("vds");
        if (data == null) {
            return Pair.of(null, null);
        }
        String[] vds = data.split("~~~");
        return Pair.of(vds[0], vds.length > 1 ? vds[1] : "");
    }

    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "Value")
    @UIEditReloadWidget
    default String getSetValueDataSource() {
        return getJsonData("svds");
    }

    default Pair<String, String> getSetSingleValueDataSource() {
        String data = getJsonData("svds");
        if (data == null) {
            return Pair.of(null, null);
        }
        String[] vds = data.split("~~~");
        return Pair.of(vds[0], vds.length > 1 ? vds[1] : "");
    }

    @UIField(order = 10)
    @UIFieldGroup(value = "Value", order = 2)
    @UIEditReloadWidget
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
