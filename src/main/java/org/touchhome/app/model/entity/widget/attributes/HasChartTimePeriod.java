package org.touchhome.app.model.entity.widget.attributes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.touchhome.app.model.entity.widget.UIEditReloadWidget;
import org.touchhome.app.model.entity.widget.UIFieldFunction;
import org.touchhome.app.model.entity.widget.UIFieldTimeSlider;
import org.touchhome.bundle.api.entity.HasJsonData;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldGroup;
import org.touchhome.bundle.api.ui.field.UIFieldReadDefaultValue;
import org.touchhome.bundle.api.ui.field.UIFieldSlider;

public interface HasChartTimePeriod extends HasJsonData {

    @UIField(order = 1)
    @UIFieldTimeSlider
    @UIFieldGroup(value = "Time period", order = 7, borderColor = "#166F37")
    @UIEditReloadWidget
    @UIFieldReadDefaultValue
    default int getChartMinutesToShow() {
        return getJsonData("mts", 60);
    }

    default void setChartMinutesToShow(int value) {
        setJsonData("mts", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 600, step = 5) // max 10 point per minute
    @UIFieldGroup(value = "Time period")
    @UIEditReloadWidget
    @UIFieldReadDefaultValue
    default int getChartPointsPerHour() {
        return getJsonData("pph", 30);
    }

    default void setChartPointsPerHour(int value) {
        setJsonData("pph", value);
    }

    @UIField(order = 3, disableEdit = true)
    @UIFieldGroup(value = "Time period")
    @UIFieldFunction("return (context.get('chartMinutesToShow') / context.get('chartPointsPerHour')).toFixed(2)")
    default String getMinutesPerPoint() {
        return null;
    }

    @UIField(order = 4, disableEdit = true)
    @UIFieldGroup(value = "Time period")
    @UIFieldFunction("return Math.ceil(context.get('chartMinutesToShow') * context.get('chartPointsPerHour') / 60)")
    default String getTotalPointsCount() {
        return null;
    }

    default TimePeriod buildTimePeriod() {
        return new TimePeriod() {
            private final int minutes = getChartMinutesToShow();
            private final int pointsPerHour = getChartPointsPerHour();

            @Override
            public Date getFrom() {
                return new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes));
            }

            @Override
            public List<Date> evaluateDateRange() {
                long endTime =
                        Optional.ofNullable(getTo())
                                .map(Date::getTime)
                                .orElse(System.currentTimeMillis());
                long startTime = endTime - TimeUnit.MINUTES.toMillis(minutes);
                double requiredNumOfPoints = Math.ceil(minutes * pointsPerHour / 60F);
                double diff = (endTime - startTime) / requiredNumOfPoints;
                List<Date> dates = new ArrayList<>();
                for (int i = 0; i < requiredNumOfPoints; i++) {
                    dates.add(new Date((long) (startTime + i * diff)));
                }
                return dates;
            }
        };
    }

    interface TimePeriod {

        // This method is not idempotent. Every call return new value
        @NotNull
        Date getFrom();

        // This method is not idempotent. Every call return new value
        @Nullable
        default Date getTo() {
            return null;
        }

        List<Date> evaluateDateRange();
    }
}
