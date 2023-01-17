package org.touchhome.app.rest;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.model.OptionModel;

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
