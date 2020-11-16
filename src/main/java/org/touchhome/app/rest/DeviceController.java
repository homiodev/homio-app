package org.touchhome.app.rest;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.bundle.bluetooth.BluetoothBundleEntryPoint;

@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private final BluetoothBundleEntryPoint bluetoothBundleEntrypoint;

    @GetMapping("characteristic/{uuid}")
    public Option getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        return Option.key(bluetoothBundleEntrypoint.getDeviceCharacteristic(uuid));
    }

    @PutMapping("characteristic/{uuid}")
    @Secured(TouchHomeUtils.ADMIN_ROLE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothBundleEntrypoint.setDeviceCharacteristic(uuid, value);
    }
}
