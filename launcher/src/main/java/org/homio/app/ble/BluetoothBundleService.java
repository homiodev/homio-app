package org.homio.app.ble;


import org.springframework.stereotype.Controller;
import org.homio.hquery.hardware.network.NetworkHardwareRepository;
import org.homio.hquery.hardware.other.MachineHardwareRepository;


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
