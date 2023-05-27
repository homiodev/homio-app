package org.homio.app.rest;

import static org.homio.api.util.Constants.ADMIN_ROLE_AUTHORIZE;

import lombok.RequiredArgsConstructor;
import org.homio.addon.bluetooth.BluetoothEntrypoint;
import org.homio.api.model.OptionModel;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private final BluetoothEntrypoint bluetoothEntrypoint;

    @GetMapping("/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        String characteristic = bluetoothEntrypoint.getDeviceCharacteristic(uuid);
        return characteristic == null ? null : OptionModel.key(characteristic);
    }

    @PutMapping("/characteristic/{uuid}")
    @PreAuthorize(ADMIN_ROLE_AUTHORIZE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothEntrypoint.setDeviceCharacteristic(uuid, value);
    }
}
