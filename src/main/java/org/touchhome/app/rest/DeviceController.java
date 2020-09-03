package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.bluetooth.BluetoothBundleEntrypoint;

import java.util.Map;

@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private final BluetoothBundleEntrypoint bluetoothBundleEntrypoint;

    @GetMapping("characteristic")
    public Map<String, String> getDeviceCharacteristics() {
        return bluetoothBundleEntrypoint.getDeviceCharacteristics();
    }

    @PutMapping("characteristic/{uuid}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothBundleEntrypoint.setDeviceCharacteristic(uuid, value);
    }
}
