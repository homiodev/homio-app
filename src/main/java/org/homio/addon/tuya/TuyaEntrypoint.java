package org.homio.addon.tuya;

import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.homio.addon.tuya.service.TuyaDeviceService;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TuyaEntrypoint implements AddonEntrypoint {

    private final EntityContext entityContext;

    public URL getAddonImageURL() {
        return getResource("images/tuya.png");
    }

    @Override
    public void init() {

    }

    @Override
    public void destroy() {
        TuyaDeviceService.destroyAll();
    }
}
