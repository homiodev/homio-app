package org.touchhome.app.console;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextBGPImpl;
import org.touchhome.bundle.api.console.ConsolePluginTable;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.field.UIField;

import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

import static org.touchhome.bundle.api.ui.field.UIFieldType.StaticDate;

@RequiredArgsConstructor
public abstract class BaseProcessesConsolePlugin implements ConsolePluginTable<BaseProcessesConsolePlugin.BackgroundProcessJSON> {

    private final EntityContextImpl entityContextImpl;

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
        return entityContextImpl.bgp().getSchedulers().values()
                .stream()
                .filter(EntityContextBGPImpl.ThreadContextImpl::isShowOnUI)
                .filter(this::matchProcess)
                .map(e -> {
                    BackgroundProcessJSON bgp = new BackgroundProcessJSON();
                    bgp.entityID = e.getName();
                    bgp.processName = e.getName();
                    bgp.description = e.getDescription();
                    bgp.state = e.getState();
                    bgp.runCount = e.getRunCount();
                    bgp.creationTime = e.getCreationTime();
                    bgp.scheduleType = e.getScheduleType().name();
                    bgp.period = e.getPeriod() == null ? null : e.getPeriod() / 1000;
                    bgp.timeToNextSchedule = e.getTimeToNextSchedule();

                    return bgp;
                }).collect(Collectors.toList());
    }

    protected abstract boolean matchProcess(EntityContextBGPImpl.ThreadContextImpl<?> threadContext);

    @Override
    public int order() {
        return 1000;
    }

    @Getter
    @Setter
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
        private int runCount;

        @Override
        public Integer getId() {
            return null;
        }
    }
}
