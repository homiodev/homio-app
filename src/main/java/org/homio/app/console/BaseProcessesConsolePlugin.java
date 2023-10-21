package org.homio.app.console;

import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.Date;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.ContextBGP;
import org.homio.api.console.ConsolePluginTable;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.model.Status;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextBGPImpl;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public abstract class BaseProcessesConsolePlugin implements ConsolePluginTable<BaseProcessesConsolePlugin.BackgroundProcessJSON> {

    private final @Getter @Accessors(fluent = true) ContextImpl context;

    @Override
    public String getParentTab() {
        return "hardware";
    }

    @Override
    public Class<BackgroundProcessJSON> getEntityClass() {
        return BackgroundProcessJSON.class;
    }

    @Override
    public Collection<BackgroundProcessJSON> getValue() {
        Collection<BackgroundProcessJSON> result = context
                .bgp().getSchedulers().values()
                .stream()
                .filter(ContextBGPImpl.ThreadContextImpl::isShowOnUI)
                .filter(threadContext -> {
                    if (BaseProcessesConsolePlugin.this.handleThreads()) {
                        return threadContext.getPeriod() == null;
                    }
                    return threadContext.getPeriod() != null &&
                        threadContext.getScheduleType() != ContextBGPImpl.ScheduleType.SINGLE;
                })
                .map(e -> {
                    BackgroundProcessJSON bgp = new BackgroundProcessJSON();
                    if (e.getLogFile() != null && Files.exists(e.getLogFile())) {
                        bgp = new BackgroundProcessJSONWithLogs();
                    }
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
                    bgp.period = e.getPeriod() == null ? null : e.getPeriod().toString().substring(2);
                    bgp.timeToNextSchedule = e.getTimeToNextSchedule();
                    if (StringUtils.isNotEmpty(e.getError())) {
                        bgp.bigDescription = "Error: " + e.getError();
                    }

                    return bgp;
                }).collect(Collectors.toList());

        ContextBGP.ThreadPuller threadPuller = new ContextBGP.ThreadPuller() {
            @Override
            public ContextBGP.ThreadPuller addThread(@NotNull String name, String description, @NotNull Date creationTime,
                                                           String state, String errorMessage, String bigDescription) {
                if (BaseProcessesConsolePlugin.this.handleThreads()) {
                    result.add(new BackgroundProcessJSON(name, name, description, creationTime, null, null,
                            null, null, errorMessage, null, null, bigDescription));
                }
                return this;
            }

            @Override
            public ContextBGP.@NotNull ThreadPuller addScheduler(@NotNull String name, String description, @NotNull Date creationTime, String state,
                                                                       String errorMessage, Duration period, int runCount,
                                                                       String bigDescription) {
                if (!BaseProcessesConsolePlugin.this.handleThreads()) {
                    result.add(new BackgroundProcessJSON(name, name, description, creationTime, state,
                        ContextBGPImpl.ScheduleType.DELAY.name(), null, null, errorMessage,
                            period.toString(), runCount, bigDescription));
                }
                return this;
            }
        };
        for (Consumer<ContextBGP.ThreadPuller> pullerConsumer : context.bgp().getThreadsPullers().values()) {
            pullerConsumer.accept(threadPuller);
        }

        return result;
    }

    @Override
    public int order() {
        return 1000;
    }

    protected abstract boolean handleThreads();

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BackgroundProcessJSON implements HasEntityIdentifier {

        private String entityID;

        @UIField(hideInEdit = true, order = 1)
        private String processName;

        @UIField(hideInEdit = true, order = 3)
        private String description;

        @UIField(order = 4, hideInEdit = true)
        private Date creationTime;

        @UIField(hideInEdit = true, order = 5)
        private String state;

        @UIField(hideInEdit = true, order = 19)
        private String scheduleType;

        @UIField(hideInEdit = true, order = 20)
        private String timeToNextSchedule;

        @UIField(hideInEdit = true, order = 21)
        private Status status;

        @UIField(hideInEdit = true, order = 22)
        private String errorMessage;

        @UIField(hideInEdit = true, order = 23)
        private String period;

        @UIField(hideInEdit = true, order = 24)
        private Integer runCount;

        @UIField(hideInEdit = true, order = 25, style = "max-width: 300px;overflow: hidden;white-space: nowrap;")
        private String bigDescription;
    }

    public static class BackgroundProcessJSONWithLogs extends BackgroundProcessJSON {

        @UIContextMenuAction("CONTEXT.ITEM.SHOW_LOGS")
        public ActionResponseModel showLogs(BackgroundProcessJSON json) {
            return ActionResponseModel.showSuccess("!!!!!!!!");
        }
    }
}
