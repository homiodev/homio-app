package org.touchhome.app.model.entity.widget.impl.chart;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.util.Pair;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public enum ChartPeriod {
    Minute {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -1);
            IntStream.rangeClosed(0, 60).forEach(i -> dates.add(DateUtils.addSeconds(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 60000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-1m";
        }
    },
    FiveMinute {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -5);
            IntStream.rangeClosed(0, 15).forEach(i -> dates.add(DateUtils.addSeconds(minDate, i * 20)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 5 * 60000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-5m";
        }
    },
    FifteenMinute {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -15);
            IntStream.rangeClosed(0, 15).forEach(i -> dates.add(DateUtils.addMinutes(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 15 * 60000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-15m";
        }
    },
    Hour {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -60);
            IntStream.rangeClosed(0, 60).forEach(i -> dates.add(DateUtils.addMinutes(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 3600000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-1h";
        }
    },
    Day {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addHours(DateUtils.truncate(new Date(), Calendar.SECOND), -24);
            IntStream.rangeClosed(0, 23).forEach(i -> dates.add(DateUtils.addHours(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 24 * 3600000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-1d";
        }
    }, Week {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.truncate(DateUtils.addWeeks(new Date(), -1), Calendar.DATE);
            IntStream.rangeClosed(0, 7).forEach(i -> {
                dates.add(DateUtils.addDays(minDate, i));
            });
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 7 * 24 * 3600000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-7d";
        }
    }, Month {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.truncate(DateUtils.addMonths(new Date(), -1), Calendar.DATE);
            IntStream.rangeClosed(1, 31).forEach(i -> {
                dates.add(DateUtils.addDays(minDate, i));
            });
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 30 * 24 * 3600000L), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-30d";
        }
    }, Year {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.truncate(DateUtils.addYears(new Date(), -1), Calendar.DATE);
            IntStream.rangeClosed(0, 11).forEach(i -> {
                dates.add(DateUtils.addMonths(minDate, i));
            });
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 365 * 24 * 3600 * 1000), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "-365d";
        }
    }, All {
        @Override
        public List<Date> getDates(List<Object[]> chartItems) {
            long min = Long.MAX_VALUE, max = Long.MIN_VALUE;
            for (Object[] chartItem : chartItems) {
                min = Math.min(min, (long) chartItem[0]);
                max = Math.max(max, (long) chartItem[0]);
            }
            long delta = (max - min) / 30;
            long finalMin = min;
            return IntStream.range(0, 30).mapToObj(value -> new Date(finalMin + delta * value)).collect(Collectors.toList());
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(0), new Date());
        }

        @Override
        public String getDateFromNow() {
            return "0";
        }
    };

    public static ChartPeriod fromValue(String value) {
        return Stream.of(ChartPeriod.values()).filter(e -> e.name().equals(value)).findFirst().orElse(null);
    }

    @JsonIgnore
    public abstract List<Date> getDates(List<Object[]> chartItems);

    public abstract Pair<Date, Date> getDateRange();

    @JsonIgnore
    public abstract String getDateFromNow();
}
