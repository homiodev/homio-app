package org.homio.app.model.entity.widget.attributes;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.homio.api.entity.HasJsonData;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldReadDefaultValue;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.app.model.entity.widget.UIEditReloadWidget;
import org.homio.app.model.entity.widget.UIFieldFunction;
import org.homio.app.model.entity.widget.UIFieldTimeSlider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface HasChartTimePeriod extends HasJsonData {

    @UIField(order = 1)
    @UIFieldTimeSlider
    @UIFieldGroup(value = "TIME_PERIOD", order = 55, borderColor = "#166F37")
    @UIEditReloadWidget
    default int getChartMinutesToShow() {
        return getJsonData("mts", 60);
    }

    default void setChartMinutesToShow(int value) {
        setJsonData("mts", value);
    }

    @UIField(order = 2)
    @UIFieldSlider(min = 1, max = 600, step = 5) // max 10 point per minute
    @UIFieldGroup(value = "TIME_PERIOD")
    @UIEditReloadWidget
    @UIFieldReadDefaultValue
    default int getChartPointsPerHour() {
        return getJsonData("pph", 60);
    }

    default void setChartPointsPerHour(int value) {
        setJsonData("pph", value);
    }

    @UIField(order = 3, disableEdit = true)
    @UIFieldGroup(value = "TIME_PERIOD")
    @UIFieldFunction("return (context.get('chartMinutesToShow') / context.get('chartPointsPerHour')).toFixed(3)")
    default String getMinutesPerPoint() {
        return null;
    }

    @UIField(order = 4, disableEdit = true)
    @UIFieldGroup(value = "TIME_PERIOD")
    @UIFieldFunction("return Math.ceil(context.get('chartMinutesToShow') * context.get('chartPointsPerHour') / 60)")
    default String getTotalPointsCount() {
        return null;
    }

    default TimePeriod buildTimePeriod() {
        return new TimePeriod() {
            private final int minutes = getChartMinutesToShow();
            private final int pointsPerHour = getChartPointsPerHour();

            @Override
            public TimeRange snapshot() {
                long to = System.currentTimeMillis();
                long from = to - TimeUnit.MINUTES.toMillis(minutes);

                double requiredNumOfPoints = Math.ceil(minutes * pointsPerHour / 60F);
                double diff = (to - from) / requiredNumOfPoints;
                List<Date> dates = new ArrayList<>();
                for (int i = 0; i < requiredNumOfPoints; i++) {
                    dates.add(new Date((long) (from + i * diff)));
                }
                return new TimeRange(new Date(from), null, dates);
            }
        };
    }

    interface TimePeriod {

        // Create snapshot for to and from and dataRange and make getFrom()/getTo()/evaluateDateRange() idempotent
        TimeRange snapshot();
    }

    @Getter
    @AllArgsConstructor
    class TimeRange {

        private final @NotNull Date from;
        private final @Nullable Date to;
        private final @NotNull List<Date> range;
    }
}
