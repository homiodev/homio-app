package org.touchhome.app.rest;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.touchhome.bundle.api.hardware.wifi.WirelessHardwareRepository;
import org.touchhome.bundle.bluetooth.BluetoothService;

import java.util.Map;

@RestController
@RequestMapping("/rest/device")
@RequiredArgsConstructor
public class DeviceController {

    private final WirelessHardwareRepository wirelessHardwareRepository;
    private final BluetoothService bluetoothService;
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("ssh/open")
    public void startSSH() {
        if (!wirelessHardwareRepository.isSshGenerated()) {
            wirelessHardwareRepository.generateSSHKeys();
        }
    }

    @GetMapping("ssh/{token}")
    public SessionStatusModel startSSH(@PathVariable("token") String token) {
        return restTemplate.getForObject("https://tmate.io/api/t/" + token, SessionStatusModel.class);
    }

    @GetMapping("characteristic")
    public Map<String, String> getDeviceCharacteristics() {
        return bluetoothService.getDeviceCharacteristics();
    }

    @PutMapping("characteristic/{uuid}")
    public void setDeviceCharacteristic(@PathVariable("uuid") String uuid, @RequestBody byte[] value) {
        bluetoothService.setDeviceCharacteristic(uuid, value);
    }

    @Getter
    @Setter
    private static class SessionStatusModel {
        private boolean closed;
        private String closed_at;
        private String created_at;
        private String disconnected_at;
        private String ssh_cmd_fmt;
        private String ws_url_fmt;
    }
}
