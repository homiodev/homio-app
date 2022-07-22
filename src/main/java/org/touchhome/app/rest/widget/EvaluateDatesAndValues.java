package org.touchhome.app.rest.widget;

import org.apache.commons.lang3.time.DateUtils;
import org.touchhome.app.model.entity.widget.impl.chart.TimeSeriesChartBaseEntity;
import org.touchhome.bundle.api.entity.widget.AggregationType;
import org.touchhome.bundle.api.ui.TimePeriod;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public final class EvaluateDatesAndValues {

    public static List<Float> aggregate(List<List<Float>> values, AggregationType aggregationType) {
        return values.stream().map(items -> {
            Stream<Float> stream = items.stream();
            if (aggregationType.isRequireSorting()) {
                stream = stream.sorted();
            }
            return aggregationType.evaluate(stream);
        }).collect(Collectors.toList());
    }

    public static List<Date> calculateDates(TimePeriod timePeriod, List<TimeSeriesValues> timeSeriesValues,
                                            TimeSeriesChartBaseEntity entity) {
        // get dates split by algorithm
        List<Date> dates = evaluateDates(timePeriod, timeSeriesValues);
        List<Date> initialDates = new ArrayList<>(dates);
        // minimum number not 0 values to fit requirements
        int minDateSize = initialDates.size() / 2;
        // fill values with remove 0 points. Attention: dates are modified by iterator
        fulfillValues(dates, timeSeriesValues, entity);
        int index = 2;
        int prevDates = -1; // prevDates uses to avoid extra iterations if prevDates == datesWithMultiplier
        while (index != 5 && prevDates != dates.size()) {
            prevDates = dates.size();
            dates = new ArrayList<>(initialDates.size() * index);
            for (int i = 0; i < initialDates.size() - 1; i++) {
                dates.add(initialDates.get(i));
                dates.add(new Date(((initialDates.get(i + 1).getTime() + initialDates.get(i).getTime())) / 2));
            }
            dates.add(initialDates.get(initialDates.size() - 1));
            initialDates = new ArrayList<>(dates);
            fulfillValues(dates, timeSeriesValues, entity);
            index++;
        }
        return dates;
    }

    private static List<Date> evaluateDates(TimePeriod timePeriod, List<TimeSeriesValues> timeSeriesValues) {
        List<Date> dates = new ArrayList<>();
        Date minDate;
        switch (timePeriod) {
            case Minute:
                minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -1);
                IntStream.rangeClosed(0, 60).forEach(i -> dates.add(DateUtils.addSeconds(minDate, i)));
                return dates;
            case FiveMinute:
                minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -5);
                IntStream.rangeClosed(0, 15).forEach(i -> dates.add(DateUtils.addSeconds(minDate, i * 20)));
                return dates;
            case FifteenMinute:
                minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -15);
                IntStream.rangeClosed(0, 15).forEach(i -> dates.add(DateUtils.addMinutes(minDate, i)));
                return dates;
            case Hour:
                minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -60);
                IntStream.rangeClosed(0, 60).forEach(i -> dates.add(DateUtils.addMinutes(minDate, i)));
                return dates;
            case Day:
                minDate = DateUtils.addHours(DateUtils.truncate(new Date(), Calendar.SECOND), -24);
                IntStream.rangeClosed(0, 23).forEach(i -> dates.add(DateUtils.addHours(minDate, i)));
                return dates;
            case Week:
                minDate = DateUtils.truncate(DateUtils.addWeeks(new Date(), -1), Calendar.DATE);
                IntStream.rangeClosed(0, 7).forEach(i -> {
                    dates.add(DateUtils.addDays(minDate, i));
                });
                return dates;
            case Month:
                minDate = DateUtils.truncate(DateUtils.addMonths(new Date(), -1), Calendar.DATE);
                IntStream.rangeClosed(1, 31).forEach(i -> {
                    dates.add(DateUtils.addDays(minDate, i));
                });
                return dates;
            case Year:
                minDate = DateUtils.truncate(DateUtils.addYears(new Date(), -1), Calendar.DATE);
                IntStream.rangeClosed(0, 11).forEach(i -> {
                    dates.add(DateUtils.addMonths(minDate, i));
                });
                return dates;
            case All:
                long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
                for (TimeSeriesValues timeSeriesValue : timeSeriesValues) {
                    for (TimeSeriesContext timeSeriesContext : timeSeriesValue.getItemSeries()) {
                        for (Object[] chartItem : timeSeriesContext.getValue()) {
                            min = Math.min(min, (long) chartItem[0]);
                            max = Math.max(max, (long) chartItem[0]);
                        }
                    }
                }
                long delta = (max - min) / 30;
                long finalMin = min;
                return IntStream.range(0, 30).mapToObj(value -> new Date(finalMin + delta * value)).collect(Collectors.toList());
        }
        throw new IllegalStateException("Unable to evaluate dates for TimePeriod: " + timePeriod);
    }

    private static void fulfillValues(List<Date> dates, List<TimeSeriesValues> timeSeriesValues,
                                      TimeSeriesChartBaseEntity entity) {
        List<TimeSeriesContext> contextList = new ArrayList<>();
        boolean calcMissingValues = true;
        List<Iterator<List<Float>>> fullChartValueIterators = new ArrayList<>();

        for (TimeSeriesValues timeSeriesValue : timeSeriesValues) {
            for (TimeSeriesContext timeSeriesContext : timeSeriesValue.getItemSeries()) {
                if (timeSeriesContext.getValue().isEmpty()) {
                    continue;
                }
                List<List<Float>> values = new ArrayList<>(dates.size());
                IntStream.range(0, dates.size()).forEach(value -> values.add(new ArrayList<>()));
                // push values to date between buckets
                for (Object[] chartItem : timeSeriesContext.getValue()) {
                    long time = chartItem[0] instanceof Date ? ((Date) chartItem[0]).getTime() : (Long) chartItem[0];
                    int index = getDateIndex(dates, time);
                    values.get(index).add((Float) chartItem[1]);
                }
                timeSeriesContext.setValues(values);
                contextList.add(timeSeriesContext);

                calcMissingValues = calcMissingValues && !entity.fillMissingValues(timeSeriesContext.getSeriesEntity());
                fullChartValueIterators.add(values.iterator());

                /*if (!entity.fillMissingValues(timeSeriesContext.getSeriesEntity())) {
                    Iterator<Date> dateIterator = dates.iterator();
                    for (Iterator<List<Float>> iterator = values.iterator(); iterator.hasNext(); ) {
                        List<Float> filledValues = iterator.next();
                        dateIterator.next();
                        if (filledValues.isEmpty()) {
                            iterator.remove();
                            dateIterator.remove();
                        }
                    }
                }*/
            }
        }

        // need erase dates only through all datasets
        if (calcMissingValues) {
            for (Iterator<Date> dateIterator = dates.iterator(); dateIterator.hasNext(); ) {
                List<List<Float>> valuesList = fullChartValueIterators.stream().map(i -> i.next()).collect(Collectors.toList());

                boolean notEmptyBlock = valuesList.stream().anyMatch(v -> !v.isEmpty());
                dateIterator.next();

                if (!notEmptyBlock) {
                    fullChartValueIterators.forEach(listIterator -> {
                        listIterator.remove();
                    });
                    dateIterator.remove();
                }
            }
        }
    }

    private static int getDateIndex(List<Date> dateList, long time) {
        for (int i = 0; i < dateList.size(); i++) {
            if (time < dateList.get(i).getTime()) {
                return i - 1;
            }
        }
        return dateList.size() - 1;
    }

    public static List<String> buildLabels(TimePeriod timePeriod, List<Date> dates) {
        DateFormat dateFormat = getDateFormat(timePeriod, dates);
        return dates.stream().map(dateFormat::format).collect(Collectors.toList());
    }

    public static DateFormat getDateFormat(TimePeriod timePeriod, List<Date> dates) {
        switch (timePeriod) {
            case Minute:
            case FiveMinute:
            case FifteenMinute:
                return new SimpleDateFormat("mm:ss");
            case Hour:
                return new SimpleDateFormat("HH:mm:ss");
            case Day:
                return new SimpleDateFormat("dd:HH:mm");
            case Month:
                return new SimpleDateFormat("MM-dd:HH:mm");
            case Year:
                return new SimpleDateFormat("yy-MM-dd:HH");
            default:
                LongSummaryStatistics statistics = dates.stream().mapToLong(Date::getTime).summaryStatistics();
                TimePeriod calcPeriod =
                        EvaluateDatesAndValues.findTimePeriodFromMilliseconds(statistics.getMax() - statistics.getMin());
                return calcPeriod == null ? new SimpleDateFormat("yy-MM-dd") : getDateFormat(calcPeriod, dates);
        }
    }

    private static TimePeriod findTimePeriodFromMilliseconds(long timeout) {
        int minutes = (int) (timeout / 60000);
        if (minutes < 1) {
            return TimePeriod.Minute;
        } else if (minutes < 5) {
            return TimePeriod.FiveMinute;
        } else if (minutes < 15) {
            return TimePeriod.FifteenMinute;
        } else if (minutes < 60) {
            return TimePeriod.Hour;
        } else if (minutes < 1440) {
            return TimePeriod.Day;
        } else if (minutes < 10_080) {
            return TimePeriod.Week;
        } else if (minutes < 43_800) {
            return TimePeriod.Month;
        } else if (minutes < 525_600) {
            return TimePeriod.Year;
        }
        return null;
    }
}
