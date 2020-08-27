package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.hardware.HardwareEventsImpl;
import org.touchhome.bundle.api.hardware.other.StartupHardwareRepository;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.util.Set;

import static org.touchhome.bundle.api.util.TouchHomeUtils.ADMIN_ROLE;

@RestController
@RequestMapping("/rest/hardware")
@RequiredArgsConstructor
public class HardwareController {

    private final HardwareEventsImpl hardwareEvents;
    private final StartupHardwareRepository startupHardwareRepository;

    @GetMapping("event")
    public Set<Option> getHardwareEvents() {
        return hardwareEvents.getEvents().keySet();
    }

    @PostMapping("app/update")
    @Secured(ADMIN_ROLE)
    public void updateApp() {
        startupHardwareRepository.updateApp(TouchHomeUtils.getFilesPath());
    }
}
