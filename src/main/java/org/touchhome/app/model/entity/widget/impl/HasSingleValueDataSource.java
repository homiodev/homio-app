package org.touchhome.app.model.entity.widget.impl;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.UIFieldTimeSlider;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.entity.widget.ability.HasSetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.touchhome.bundle.api.ui.field.selection.dynamic.HasDynamicParameterFields;

public interface HasSingleValueDataSource extends HasDynamicParameterFields {

    @UIField(order = 10, required = true)
    @UIFieldBeanSelection(value = HasGetStatusValue.class, lazyLoading = true)
    @UIFieldBeanSelection(value = HasAggregateValueFromSeries.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasGetStatusValue.class)
    @UIFieldEntityByClassSelection(HasAggregateValueFromSeries.class)
    @UIFieldGroup(value = "Value", order = 10)
    @UIEditReloadWidget
    default String getValueDataSource() {
        return getJsonData("vds");
    }

    default void setValueDataSource(String value) {
        setJsonData("vds", value);
    }

    @UIFieldBeanSelection(value = HasSetStatusValue.class, lazyLoading = true)
    @UIFieldEntityByClassSelection(HasSetStatusValue.class)
    @UIFieldGroup(value = "Value")
    @UIEditReloadWidget
    default String getSetValueDataSource() {
        return getJsonData("svds");
    }

    default void setSetValueDataSource(String value) {
        setJsonData("svds", value);
    }

    @UIField(order = 20)
    @UIFieldGroup("Value")
    @UIEditReloadWidget
    default AggregationType getValueAggregationType() {
        return getJsonDataEnum("valAggrType", AggregationType.None);
    }

    default void setValueAggregationType(AggregationType value) {
        setJsonData("valAggrType", value);
    }

    @UIField(order = 30)
    @UIFieldTimeSlider
    @UIFieldGroup("Value")
    default int getValueAggregationPeriod() {
        return getJsonData("valAggrPeriod", 60);
    }

    default void setValueAggregationPeriod(int value) {
        setJsonData("valAggrPeriod", value);
    }
}
