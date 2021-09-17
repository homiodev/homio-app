package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.model.OptionModel;

import java.util.Collection;

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
