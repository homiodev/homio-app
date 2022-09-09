package org.touchhome.app.console;

import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextBGPImpl;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.console.ConsolePluginTable;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.field.UIField;

import java.util.Collection;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.ui.field.UIFieldType.StaticDate;

@RequiredArgsConstructor
public abstract class BaseProcessesConsolePlugin implements ConsolePluginTable<BaseProcessesConsolePlugin.BackgroundProcessJSON> {

    @Getter
    private final EntityContextImpl entityContext;

    @Override
    public String getParentTab() {
        return "processes";
    }

    @Override
    public Class<BackgroundProcessJSON> getEntityClass() {
        return BackgroundProcessJSON.class;
    }

    @Override
    public Collection<BackgroundProcessJSON> getValue() {
        Collection<BackgroundProcessJSON> result = entityContext.bgp().getSchedulers().values()
                .stream()
                .filter(EntityContextBGPImpl.ThreadContextImpl::isShowOnUI)
                .filter(threadContext -> {
                    if (BaseProcessesConsolePlugin.this.handleThreads()) {
                        return threadContext.getPeriod() == null;
                    }
                    return threadContext.getPeriod() != null &&
                            threadContext.getScheduleType() != EntityContextBGPImpl.ScheduleType.SINGLE;
                })
                .map(e -> {
                    BackgroundProcessJSON bgp = new BackgroundProcessJSON();
                    bgp.entityID = e.getName();
                    bgp.processName = e.getName();
                    bgp.description = e.getDescription();
                    // skip state for single thread
                    if (!BaseProcessesConsolePlugin.this.handleThreads()) {
                        bgp.state = e.getState();
                        bgp.runCount = e.getRunCount();
                        bgp.scheduleType = e.getScheduleType().name();
                    }
                    bgp.creationTime = e.getCreationTime();
                    bgp.period = e.getPeriod() == null ? null : e.getPeriod() / 1000;
                    bgp.timeToNextSchedule = e.getTimeToNextSchedule();
                    if (StringUtils.isNotEmpty(e.getError())) {
                        bgp.bigDescription = "Error: " + e.getError();
                    }

                    return bgp;
                }).collect(Collectors.toList());

        EntityContextBGP.ThreadPuller threadPuller = new EntityContextBGP.ThreadPuller() {
            @Override
            public EntityContextBGP.ThreadPuller addThread(String name, String description, Date creationTime,
                                                           String state, String errorMessage, String bigDescription) {
                if (BaseProcessesConsolePlugin.this.handleThreads()) {
                    result.add(new BackgroundProcessJSON(name, name, description, creationTime, null, null,
                            null, null, errorMessage, null, null, bigDescription));
                }
                return this;
            }

            @Override
            public EntityContextBGP.ThreadPuller addScheduler(String name, String description, Date creationTime, String state,
                                                              String errorMessage, int period, int runCount,
                                                              String bigDescription) {
                if (!BaseProcessesConsolePlugin.this.handleThreads()) {
                    result.add(new BackgroundProcessJSON(name, name, description, creationTime, state,
                            EntityContextBGPImpl.ScheduleType.DELAY.name(), -1L, null, errorMessage, (long) period, runCount,
                            bigDescription));
                }
                return this;
            }
        };
        for (Consumer<EntityContextBGP.ThreadPuller> pullerConsumer : entityContext.bgp().getThreadsPullers().values()) {
            pullerConsumer.accept(threadPuller);
        }

        return result;
    }

    protected abstract boolean handleThreads();

    @Override
    public int order() {
        return 1000;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackgroundProcessJSON implements HasEntityIdentifier {

        private String entityID;

        @UIField(readOnly = true, order = 1)
        private String processName;

        @UIField(readOnly = true, order = 3)
        private String description;

        @UIField(order = 4, readOnly = true, type = StaticDate)
        private Date creationTime;

        @UIField(readOnly = true, order = 5)
        private String state;

        @UIField(readOnly = true, order = 19)
        private String scheduleType;

        @UIField(readOnly = true, order = 20)
        private Long timeToNextSchedule;

        @UIField(readOnly = true, order = 21)
        private Status status;

        @UIField(readOnly = true, order = 22)
        private String errorMessage;

        @UIField(readOnly = true, order = 23)
        private Long period;

        @UIField(readOnly = true, order = 24)
        private Integer runCount;

        @UIField(readOnly = true, order = 25, style = "max-width: 50px;")
        private String bigDescription;
    }
}
