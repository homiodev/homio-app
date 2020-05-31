package org.touchhome.bundle.gpio;

import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigital;
import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.raspberry.RaspberryGPIOService;
import org.touchhome.bundle.raspberry.RaspberryGpioPin;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            list.add(new GpioPluginEntity(gpioPin.name(), gpioPin.getName(), gpioPin.getPin().getName(),
                    gpioPin.getBcmPin().getName(), pin.getPullResistance(), pin.getMode(),
                    pin instanceof GpioPinDigital ? ((GpioPinDigital) pin).getState() : null));
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
    @NoArgsConstructor
    @AllArgsConstructor
    private static class GpioPluginEntity implements HasEntityIdentifier, Comparable<GpioPluginEntity> {
        @UIField(order = 1)
        private String name;

        @UIField(order = 2)
        private String description;

        @UIField(order = 3, label = "Raspi Pin")
        private String raspiPin;

        @UIField(order = 4, label = "BCM Pin")
        private String bcmPin;

        @UIField(order = 5, label = "Pin Pull Resistance")
        private PinPullResistance pinPullResistance;

        @UIField(order = 6)
        private PinMode mode;

        @UIField(order = 7)
        private Object value;

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
