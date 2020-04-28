package org.touchhome.bundle.cloud;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;

@Log4j2
@Component
@RequiredArgsConstructor
public class CloudBundle implements BundleContext {

    public void init() {

    }

    @Override
    public String getBundleId() {
        return "cloud";
    }

    @Override
    public int order() {
        return 800;
    }
}
