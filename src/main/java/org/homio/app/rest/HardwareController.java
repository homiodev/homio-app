package org.homio.app.rest;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.homio.api.model.OptionModel;
import org.homio.app.manager.common.EntityContextImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/hardware")
@RequiredArgsConstructor
public class HardwareController {

    private final EntityContextImpl entityContext;

    @GetMapping("/event")
    public Collection<OptionModel> getHardwareEvents() {
        return entityContext.event().getEvents();
    }
}
