package org.homio.app.model.entity.widget.attributes;

import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.UIFieldTimeSlider;
import org.homio.bundle.api.entity.widget.AggregationType;
import org.homio.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.homio.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldGroup;
import org.homio.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.homio.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.homio.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

public interface HasSingleValueAggregatedDataSource extends HasSingleValueDataSource {

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

    @UIField(order = 20)
    @UIFieldGroup("Value")
    @UIEditReloadWidget
    @UIFieldReadDefaultValue
    default AggregationType getValueAggregationType() {
        return getJsonDataEnum("valAggrType", AggregationType.None);
    }

    default void setValueAggregationType(AggregationType value) {
        setJsonData("valAggrType", value);
    }

    @UIField(order = 30)
    @UIFieldTimeSlider
    @UIFieldGroup("Value")
    @UIFieldReadDefaultValue
    default int getValueAggregationPeriod() {
        return getJsonData("valAggrPeriod", 60);
    }

    default void setValueAggregationPeriod(int value) {
        setJsonData("valAggrPeriod", value);
    }
}