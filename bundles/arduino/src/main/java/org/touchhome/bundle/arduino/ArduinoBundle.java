package org.touchhome.bundle.arduino;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleEntrypoint;

@Log4j2
@Component
@RequiredArgsConstructor
public class ArduinoBundle implements BundleEntrypoint {

    public void init() {

    }

    @Override
    public String getBundleId() {
        return "arduino";
    }

    @Override
    public int order() {
        return 700;
    }
}
