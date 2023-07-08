package org.homio.app.ble;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Controller;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;

@Log4j2
@Controller
public class BluetoothBundleService extends BaseBluetoothCharacteristicService {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    public BluetoothBundleService(
        MachineHardwareRepository machineHardwareRepository,
        NetworkHardwareRepository networkHardwareRepository) {
        super(machineHardwareRepository, networkHardwareRepository);
        init();
    }

    @Override
    public void updateBluetoothStatus(String status, String message) {

    }
}
