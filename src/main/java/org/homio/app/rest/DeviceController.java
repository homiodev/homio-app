package org.homio.app.rest;

import static org.homio.bundle.api.util.Constants.ADMIN_ROLE;

import javax.annotation.security.RolesAllowed;
import lombok.RequiredArgsConstructor;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.bluetooth.BluetoothBundleEntrypoint;
import org.springframework.security.access.annotation.Secured;
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

    private final BluetoothBundleEntrypoint bluetoothBundleEntrypoint;

    @GetMapping("/characteristic/{uuid}")
    public OptionModel getDeviceCharacteristic(@PathVariable("uuid") String uuid) {
        String characteristic = bluetoothBundleEntrypoint.getDeviceCharacteristic(uuid);
        return characteristic == null ? null : OptionModel.key(characteristic);
    }

    @PutMapping("/characteristic/{uuid}")
    @RolesAllowed(ADMIN_ROLE)
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothBundleEntrypoint.setDeviceCharacteristic(uuid, value);
    }
}
