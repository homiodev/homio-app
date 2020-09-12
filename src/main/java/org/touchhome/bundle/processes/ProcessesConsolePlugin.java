package org.touchhome.bundle.processes;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.app.json.bgp.BackgroundProcessServiceJSON;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.HasEntityIdentifier;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProcessesConsolePlugin implements ConsolePlugin {

    private final EntityContextImpl entityContextImpl;

    @Override
    public List<? extends HasEntityIdentifier> drawEntity() {
        return entityContextImpl.getSchedulers().values()
                .stream().filter(EntityContextImpl.ThreadContextImpl::isShowOnUI).map(e -> {
                    BackgroundProcessServiceJSON bgp = new BackgroundProcessServiceJSON();
                    bgp.setEntityID(e.getName());
                    bgp.setProcessName(e.getName());
                    bgp.setDescription(e.getDescription());
                    bgp.setState(e.getState());
                    bgp.setRunCount(e.getRunCount());
                    bgp.setCreationTime(e.getCreationTime());
                    bgp.setScheduleType(e.getScheduleType().name());
                    bgp.setPeriod(e.getPeriod() == null ? null : e.getPeriod() / 1000);
                    bgp.setTimeToNextSchedule(e.getTimeToNextSchedule());

                    return bgp;
                }).collect(Collectors.toList());
    }

    @Override
    public int order() {
        return 1000;
    }
}
