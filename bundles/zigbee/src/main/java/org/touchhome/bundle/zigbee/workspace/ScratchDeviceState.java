package org.touchhome.bundle.zigbee.workspace;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.touchhome.bundle.zigbee.ZigBeeDevice;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterEndpoint;
import org.touchhome.bundle.zigbee.model.State;

@Setter
@Getter
@RequiredArgsConstructor
public class ScratchDeviceState {
    private final ZigBeeDevice zigBeeDevice;
    private final ZigBeeDeviceStateUUID uuid;
    private final State state;
    private final long date = System.currentTimeMillis();
    private boolean isHandled = false;
}
