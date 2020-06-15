package org.touchhome.bundle.zigbee.setting;

import com.fazecast.jSerialComm.SerialPort;
import org.apache.commons.lang.StringUtils;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ZigbeePortSetting implements BundleSettingPlugin<SerialPort> {

    @Override
    public SettingType getSettingType() {
        return SettingType.SelectBoxDynamic;
    }

    @Override
    public List<Option> loadAvailableValues(EntityContext entityContext) {
        return Arrays.stream(SerialPort.getCommPorts()).map(p ->
                new Option(p.getSystemPortName(), p.getSystemPortName())).collect(Collectors.toList());
    }

    @Override
    public SerialPort parseValue(EntityContext entityContext, String value) {
        return StringUtils.isEmpty(value) ? null :
                Stream.of(SerialPort.getCommPorts())
                        .filter(p -> p.getSystemPortName().equals(value)).findAny().orElse(null);
    }

    @Override
    public int order() {
        return 100;
    }
}
