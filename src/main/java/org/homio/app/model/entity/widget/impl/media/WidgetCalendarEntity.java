package org.homio.app.model.entity.widget.impl.media;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.state.JsonType;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.jetbrains.annotations.NotNull;

import java.time.*;
import java.util.*;
import java.util.function.Function;

@Entity
public class WidgetCalendarEntity extends WidgetEntity<WidgetCalendarEntity> {

  public WidgetCalendarEntity() {
    setBw(3);
    setBh(3);
  }

  @Override
  protected @NotNull String getWidgetPrefix() {
    return "clndr";
  }

  @Override
  public WidgetGroup getGroup() {
    return WidgetGroup.Media;
  }

  @Override
  public @NotNull String getImage() {
    return "fas fa-calendar-days";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  public Set<CalendarEvent> getEvents() {
    return getJsonDataSet("events", CalendarEvent.class);
  }

  public void setEvents(Set<CalendarEvent> value) {
    setJsonDataObject("events", value);
  }

  @Getter
  @Setter
  @NoArgsConstructor
  public static final class CalendarEvent implements Comparable<CalendarEvent> {
    private String id;
    private String bid;
    private String title;
    private String color;
    private String icon;
    @JsonProperty("rpt")
    private RepeatInterval repeat;
    @JsonProperty("rpt_u")
    private Long repeatUntil;
    private Long start;
    private Long end;
    @JsonProperty("msg")
    private String message;
    // Broadcast event
    @JsonProperty("be")
    private String broadcastEvent;
    // Broadcast event payload
    @JsonProperty("bep")
    private String broadcastEventPayload;
    // repeat fire event every N seconds
    @JsonProperty("ber")
    private Long broadcastEventRepeat;

    @JsonIgnore
    private WidgetCalendarEntity calendar;

    public CalendarEvent(CalendarEvent event, long start, long end) {
      this.start = start;
      this.end = end;
      this.id = event.bid + "-" + start;
      this.bid = event.bid;
      updateFrom(event);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      CalendarEvent event = (CalendarEvent) o;
      return Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(id);
    }

    public void validateDates() {
      if (start == null) {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        start = startOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
      }
      if (end == null) {
        LocalDateTime endOfDay = LocalDate.now().atTime(23, 59, 59);
        end = endOfDay.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
      }
      if (start > end) {
        throw new IllegalArgumentException("Start date must be less than or equal to end date");
      }
    }

    public void validate() {
      validateDates();
      if (repeat == null) {
        repeat = RepeatInterval.REPEAT_NO;
      }
      if (repeat != RepeatInterval.REPEAT_NO) {
        if (repeatUntil == null) {
          throw new IllegalArgumentException("Repeat until required if repeat interval is selected");
        }
        if (repeatUntil < start) {
          throw new IllegalArgumentException("Repeat until must be in the future");
        }
      }
      if (StringUtils.isNotBlank(broadcastEventPayload)) {
        // validate payload to be json format
        new JsonType(broadcastEventPayload);
      } else {
        broadcastEventPayload = null;
      }
    }

    public void updateFrom(CalendarEvent event) {
      title = event.title;
      color = event.color;
      icon = event.icon;
      repeat = event.repeat;
      repeatUntil = event.repeatUntil;
      message = event.message;
      broadcastEvent = event.broadcastEvent;
      broadcastEventPayload = event.broadcastEventPayload;
      broadcastEventRepeat = event.broadcastEventRepeat;
    }

    @Override
    public int compareTo(@NotNull WidgetCalendarEntity.CalendarEvent o) {
      if (this.start == null || o.getStart() == null) {
        return 0;
      }
      return this.start.compareTo(o.start);
    }

    @RequiredArgsConstructor
    public enum RepeatInterval {
      REPEAT_NO(dateTime -> null),
      REPEAT_DAILY(dateTime -> dateTime.plusDays(1)),
      REPEAT_WEEKLY(dateTime -> dateTime.plusWeeks(1)),
      REPEAT_BI_WEEKLY(dateTime -> dateTime.plusWeeks(2)),
      REPEAT_MONTHLY(dateTime -> dateTime.plusMonths(1)),
      REPEAT_YEARLY(dateTime -> dateTime.plusYears(1));

      private final Function<LocalDateTime, LocalDateTime> incrementFunction;

      public @NotNull Collection<CalendarEvent> populateEvents(CalendarEvent event) {
        if (event.repeat == REPEAT_NO) {
          return Set.of();
        }
        List<CalendarEvent> events = new ArrayList<>();

        LocalDateTime startDateTime = Instant.ofEpochMilli(event.getStart())
          .atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime endDateTime = Instant.ofEpochMilli(event.getEnd())
          .atZone(ZoneId.systemDefault()).toLocalDateTime();
        LocalDateTime repeatUntilDateTime = Instant.ofEpochMilli(event.getRepeatUntil())
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime()
          .with(LocalTime.MAX);

        // Generate repeated events based on the interval
        if (StringUtils.isEmpty(event.bid)) {
          event.bid = String.valueOf(System.currentTimeMillis());
        }
        startDateTime = incrementFunction.apply(startDateTime);
        endDateTime = incrementFunction.apply(endDateTime);

        while (!startDateTime.isAfter(repeatUntilDateTime)) {
          // Clone the original event and set the new start time
          long start = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
          long end = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
          events.add(new CalendarEvent(event, start, end));

          // Get the next occurrence based on the current interval
          startDateTime = incrementFunction.apply(startDateTime);
          endDateTime = incrementFunction.apply(endDateTime);
        }
        return events;
      }
    }
  }
}
