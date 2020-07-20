package org.touchhome.app.model.entity.widget.impl.chart;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.data.util.Pair;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum ChartPeriod {
    Minute {
        @Override
        public List<Date> getDates() {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -1);
            IntStream.rangeClosed(0, 60).forEach(i -> dates.add(DateUtils.addSeconds(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 60000), new Date());
        }
    },
    Hour {
        @Override
        public List<Date> getDates() {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addMinutes(DateUtils.truncate(new Date(), Calendar.SECOND), -60);
            IntStream.rangeClosed(0, 60).forEach(i -> dates.add(DateUtils.addMinutes(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 3600000), new Date());
        }
    },
    Day {
        @Override
        public List<Date> getDates() {
            List<Date> dates = new ArrayList<>();
            Date minDate = DateUtils.addHours(DateUtils.truncate(new Date(), Calendar.SECOND), -24);
            IntStream.rangeClosed(0, 23).forEach(i -> dates.add(DateUtils.addHours(minDate, i)));
            return dates;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(System.currentTimeMillis() - 24 * 3600000), new Date());
        }
    }, Week {
        @Override
        public List<Date> getDates() {
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
    }, Month {
        @Override
        public List<Date> getDates() {
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
    }, Year {
        @Override
        public List<Date> getDates() {
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
    }, All {
        @Override
        public List<Date> getDates() {
            return null;
        }

        @Override
        public Pair<Date, Date> getDateRange() {
            return Pair.of(new Date(0), new Date());
        }
    };

    @JsonCreator
    public static ChartPeriod fromValue(String value) {
        return Stream.of(ChartPeriod.values()).filter(e -> e.name().equals(value)).findFirst().orElse(null);
    }

    @JsonValue
    public String toValue() {
        return name();
    }

    @JsonIgnore
    public abstract List<Date> getDates();

    public abstract Pair<Date, Date> getDateRange();
}
