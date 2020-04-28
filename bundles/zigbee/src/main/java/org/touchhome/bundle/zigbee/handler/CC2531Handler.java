package org.touchhome.bundle.zigbee.handler;

import com.zsmartsystems.zigbee.dongle.cc2531.ZigBeeDongleTiCc2531;
import com.zsmartsystems.zigbee.transport.TransportConfig;
import com.zsmartsystems.zigbee.transport.TransportConfigOption;
import com.zsmartsystems.zigbee.transport.ZigBeePort.FlowControl;
import com.zsmartsystems.zigbee.transport.ZigBeeTransportTransmit;
import com.zsmartsystems.zigbee.zcl.clusters.ZclIasZoneCluster;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeChannelConverterFactory;
import org.touchhome.bundle.zigbee.internal.ZigBeeSerialPort;
import org.touchhome.bundle.zigbee.setting.ZigbeePortBaudSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeePortSetting;

import java.util.HashSet;
import java.util.Set;

@Log4j2
@Component
public class CC2531Handler extends ZigBeeCoordinatorHandler {

    public CC2531Handler(ZigBeeChannelConverterFactory channelFactory, EntityContext entityContext) {
        super(channelFactory, entityContext);
    }

    @Override
    protected void initializeDongle() {
        log.debug("Initializing ZigBee CC2531 serial bridge handler.");

        ZigBeeTransportTransmit dongle = createDingle();
        TransportConfig transportConfig = createTransportConfig();

        startZigBee(dongle, transportConfig);
    }

    private ZigBeeTransportTransmit createDingle() {
        ZigBeeSerialPort serialPort = new ZigBeeSerialPort(
                "cc2531",
                entityContext,
                entityContext.getSettingValue(ZigbeePortSetting.class),
                entityContext.getSettingValue(ZigbeePortBaudSetting.class),
                FlowControl.FLOWCONTROL_OUT_RTSCTS,
                () -> this.updateStatus(DeviceStatus.OFFLINE, "PORT_COMMUNICATION_ERROR"));
        return new ZigBeeDongleTiCc2531(serialPort);
    }

    private TransportConfig createTransportConfig() {
        TransportConfig transportConfig = new TransportConfig();

        // The CC2531EMK dongle doesn't pass the MatchDescriptor commands to the stack, so we can't manage our services
        // directly. Instead, register any services we want to support so the CC2531EMK can handle the MatchDescriptor.
        Set<Integer> clusters = new HashSet<>();
        clusters.add(ZclIasZoneCluster.CLUSTER_ID);
        transportConfig.addOption(TransportConfigOption.SUPPORTED_OUTPUT_CLUSTERS, clusters);
        return transportConfig;
    }
}
