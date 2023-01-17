package org.touchhome.app.rest;

import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.bluetooth.BluetoothBundleEntrypoint;

@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private final BluetoothBundleEntrypoint bluetoothBundleEntrypoint;

    @GetMapping("/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        String characteristic = bluetoothBundleEntrypoint.getDeviceCharacteristic(uuid);
        return characteristic == null ? null : OptionModel.key(characteristic);
    }

    @PutMapping("/characteristic/{uuid}")
    @Secured(ADMIN_ROLE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothBundleEntrypoint.setDeviceCharacteristic(uuid, value);
    }
}
