package org.touchhome.bundle.raspberry;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.BundleContext;

@Log4j2
@Component
@RequiredArgsConstructor
public class RaspberryBundle implements BundleContext {

    public void init() {

    }

    @Override
    public String getBundleId() {
        return "raspberry";
    }

    @Override
    public int order() {
        return 100;
    }

    @Override
    public BundleImageColorIndex getBundleImageColorIndex() {
        return BundleImageColorIndex.ONE;
    }
}
