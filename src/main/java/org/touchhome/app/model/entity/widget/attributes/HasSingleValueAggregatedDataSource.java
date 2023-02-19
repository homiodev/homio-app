package org.touchhome.app.model.entity.widget.attributes;

import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.UIFieldTimeSlider;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.entity.widget.ability.HasAggregateValueFromSeries;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.selection.UIFieldBeanSelection;
import org.touchhome.bundle.api.ui.field.selection.UIFieldEntityByClassSelection;

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
