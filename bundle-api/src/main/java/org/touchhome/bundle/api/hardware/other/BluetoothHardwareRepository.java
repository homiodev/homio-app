package org.touchhome.bundle.api.hardware.other;

import org.touchhome.bundle.api.hquery.api.HardwareQuery;
import org.touchhome.bundle.api.hquery.api.HardwareRepositoryAnnotation;

@HardwareRepositoryAnnotation
public interface BluetoothHardwareRepository {

    @HardwareQuery(value = "test -f /etc/machine-info && echo true || echo false", printOutput = true)
    boolean isBluethothFileNameExists();
}









