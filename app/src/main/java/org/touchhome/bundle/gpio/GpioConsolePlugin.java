package org.touchhome.bundle.gpio;

import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigital;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldColorMatch;
import org.touchhome.bundle.api.ui.field.UIFieldColorRef;
import org.touchhome.bundle.api.util.RaspberryGpioPin;
import org.touchhome.bundle.raspberry.RaspberryGPIOService;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GpioConsolePlugin implements ConsolePlugin {

    private final EntityContext entityContext;
    private final RaspberryGPIOService raspberryGPIOService;

    @Override
    public List<? extends HasEntityIdentifier> drawEntity() {
        List<GpioPluginEntity> list = new ArrayList<>();

        for (RaspberryGpioPin gpioPin : RaspberryGpioPin.values()) {
            GpioPin pin = raspberryGPIOService.getGpioPin(gpioPin);
            GpioPluginEntity gpioPluginEntity = new GpioPluginEntity();
            gpioPluginEntity.setName(gpioPin.name());
            gpioPluginEntity.setDescription(gpioPin.getName());
            gpioPluginEntity.setRaspiPin(gpioPin.getPin().getName());
            gpioPluginEntity.setBcmPin(gpioPin.getBcmPin() == null ? null : gpioPin.getBcmPin().getName());
            gpioPluginEntity.setSupportedPinModes(gpioPin.getPin().getSupportedPinModes().stream().map(p -> p.getName().toUpperCase()).collect(Collectors.toSet()));
            gpioPluginEntity.setOccupied(gpioPin.getOccupied());
            gpioPluginEntity.setColor(gpioPin.getColor());
            gpioPluginEntity.setSupportedPinPullResistance(gpioPin.getPin().getSupportedPinPullResistance().stream().map(p -> p.getName().toUpperCase()).collect(Collectors.toSet()));
            if (pin != null) {
                gpioPluginEntity.setValue(pin instanceof GpioPinDigital ? ((GpioPinDigital) pin).getState() : null);
                gpioPluginEntity.setMode(pin.getMode());
                gpioPluginEntity.setPinPullResistance(pin.getPullResistance());
            }
            list.add(gpioPluginEntity);
        }

        Collections.sort(list);
        return list;
    }

    @Override
    public int order() {
        return 2000;
    }

    @Override
    public boolean isEnabled() {
        return entityContext.isFeatureEnabled(EntityContext.DeviceFeature.GPIO);
    }

    @Getter
    @Setter
    private static class GpioPluginEntity implements HasEntityIdentifier, Comparable<GpioPluginEntity> {
        @UIField(order = 1)
        @UIFieldColorRef("color")
        private String name;

        @UIField(order = 2)
        private String description;

        @UIField(order = 3, label = "Raspi Pin")
        private String raspiPin;

        @UIField(order = 4, label = "BCM Pin")
        private String bcmPin;

        @UIField(order = 5, label = "Pull Resistance")
        private PinPullResistance pinPullResistance;

        @UIField(order = 6, label = "Mode")
        private PinMode mode;

        @UIField(order = 7, label = "Occupied")
        private String occupied;

        @UIField(order = 8, label = "Supported modes")
        private Set<String> supportedPinModes;

        @UIField(order = 9, label = "Supported Pull Resistance")
        private Set<String> supportedPinPullResistance;

        @UIField(order = 10)
        @UIFieldColorMatch(value = "true", color = "#1F8D2D")
        @UIFieldColorMatch(value = "false", color = "#B22020")
        private Object value;

        private String color;

        @Override
        public String getEntityID() {
            return name;
        }

        @Override
        public int compareTo(@NotNull GpioPluginEntity o) {
            return this.name.compareTo(o.name);
        }
    }
}
