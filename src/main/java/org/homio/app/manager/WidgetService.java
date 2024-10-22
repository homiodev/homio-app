package org.homio.app.manager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.state.JsonType;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.entity.widget.WidgetGroup;
import org.homio.app.model.entity.widget.WidgetTabEntity;
import org.homio.app.model.entity.widget.impl.media.WidgetCalendarEntity;
import org.homio.app.spring.ContextCreated;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.locks.ReentrantLock;

@Log4j2
@Component
@RequiredArgsConstructor
public class WidgetService implements ContextCreated {

    private final Context context;
    // keep all events that not expired yet
    private final Set<WidgetCalendarEntity.CalendarEvent> notExpiredEvents = new ConcurrentSkipListSet<>();
    private final Set<WidgetCalendarEntity.CalendarEvent> activeEvents = new ConcurrentSkipListSet<>();
    private final List<WidgetEntity<?>> widgetBaseEntities;
    private final ReentrantLock lock = new ReentrantLock();

    @Override
    public void onContextCreated(ContextImpl context) {
        WidgetTabEntity.ensureMainTabExists(context);

        registerCalendarEvents();
        context.event().addEntityUpdateListener(WidgetCalendarEntity.class, "calendar-events",
                c -> registerCalendarEvents());
        context.event().addEntityRemovedListener(WidgetCalendarEntity.class, "calendar-events",
                c -> registerCalendarEvents());

        context.bgp().addLowPriorityRequest("calendar-events", () -> {
            try {
                lock.lock();
                long curTime = System.currentTimeMillis();
                notExpiredEvents.removeIf(calendarEvent -> calendarEvent.getEnd() > curTime);

                // cancel expired active events
                for (WidgetCalendarEntity.CalendarEvent activeEvent : activeEvents) {
                    if (activeEvent.getEnd() > curTime) {
                        activeEvents.remove(activeEvent);
                        context.bgp().cancelThread("calendar-repeat-event-" + activeEvent.getId());
                    }
                }

                // move event to active list if ber not null or fire event
                for (WidgetCalendarEntity.CalendarEvent event : notExpiredEvents) {
                    if (event.getStart() >= curTime) {
                        notExpiredEvents.remove(event);
                        if (event.getBroadcastEventRepeat() != null) {
                            scheduleRepeatEvent(event);
                        } else {
                            fireCalendarEvent(event);
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        });
    }

    private void scheduleRepeatEvent(WidgetCalendarEntity.CalendarEvent event) {
        activeEvents.add(event);
        context.bgp().builder("calendar-repeat-event-" + event.getId())
                .interval(Duration.ofSeconds(event.getBroadcastEventRepeat()))
                .execute(() -> fireCalendarEvent(event));
    }

    private void fireCalendarEvent(WidgetCalendarEntity.CalendarEvent event) {
        if (event.getBroadcastEventPayload() == null) {
            context.event().fireEvent(event.getBroadcastEvent(), null);
        } else {
            context.event().fireEvent(event.getBroadcastEvent(), new JsonType(event.getBroadcastEventPayload()));
        }
    }

    private void registerCalendarEvents() {
        List<WidgetCalendarEntity> calendars = context.db().findAll(WidgetCalendarEntity.class);
        long curTime = System.currentTimeMillis();
        try {
            lock.lock();
            notExpiredEvents.clear();
            activeEvents.clear();

            for (WidgetCalendarEntity calendar : calendars) {
                for (WidgetCalendarEntity.CalendarEvent event : calendar.getEvents()) {
                    if (event.getEnd() < curTime && event.getBroadcastEvent() != null) {
                        event.setCalendar(calendar);
                        notExpiredEvents.add(event);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public List<AvailableWidget> getAvailableWidgets() {
        List<AvailableWidget> options = new ArrayList<>();
        Map<WidgetGroup, AvailableWidget> widgetMap = new HashMap<>();

        for (WidgetEntity<?> entity : this.widgetBaseEntities) {
            if (entity.isVisible()) {
                AvailableWidget widget = new AvailableWidget(entity.getType(), entity.getImage(), null);

                if (entity.getGroup() == null) {
                    options.add(widget); // Add directly if group is null
                } else {
                    widgetMap.computeIfAbsent(entity.getGroup(), group -> {
                        AvailableWidget parentWidget = new AvailableWidget(group.name().toLowerCase() + "-widgets", group.getIcon(), new ArrayList<>());
                        options.add(parentWidget);
                        return parentWidget;
                    }).children.add(widget);
                }
            }
        }

        options.sort(Comparator.comparing((AvailableWidget w) -> w.children == null || w.children.isEmpty())
                .thenComparing(AvailableWidget::getKey));

        AvailableWidget extraWidgets = new AvailableWidget("extra-widgets", "fas fa-cheese", new ArrayList<>());
        extraWidgets.children.add(new AvailableWidget("IBKR", "fas fa-user", null));
        options.add(extraWidgets);

        /*AvailableWidget extraWidgets = new AvailableWidget("extra-widgets", "fas fa-cheese", new ArrayList<>());
        Map<ParentWidget, AvailableWidget> widgetMap = new HashMap<>();

        for (WidgetBaseTemplate template : context.getBeansOfType(WidgetBaseTemplate.class)) {
                ParentWidget parent = template.getParent();
                widgetMap.computeIfAbsent(parent, parentWidget ->
                             new AvailableWidget(parentWidget.name(), parentWidget.getIcon(), new ArrayList<>()).setColor(parentWidget.getColor()))
                         .getChildren()
                         .add(new AvailableWidget(template.getName(), template.getIcon().getIcon(), null).setColor(template.getIcon().getColor()));
        }
        extraWidgets.children.addAll(widgetMap.values());
        if (!extraWidgets.children.isEmpty()) {
            options.add(extraWidgets);
        }*/
        return options;
    }

    @Getter
    @RequiredArgsConstructor
    public static class AvailableWidget {

        private @NotNull
        final String key;
        private final String icon;
        private final List<AvailableWidget> children;
        private @Setter
        @Accessors(chain = true) String color;
    }
}
