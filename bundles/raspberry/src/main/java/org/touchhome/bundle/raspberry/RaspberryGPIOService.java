package org.touchhome.bundle.raspberry;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.wiringpi.Gpio;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.WirelessManager;
import org.touchhome.bundle.api.util.RaspberryGpioPin;
import org.touchhome.bundle.api.util.UpdatableValue;
import org.touchhome.bundle.raspberry.settings.RaspberryOneWireIntervalSetting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class RaspberryGPIOService {
    private final EntityContext entityContext;
    private final WirelessManager wirelessManager;
    private final Map<String, DefaultKeyValue<Long, Float>> ds18B20Values = new HashMap<>();
    @Getter
    private final Map<RaspberryGpioPin, List<PinListener>> digitalListeners = new ConcurrentHashMap<>();
    private final Map<RaspberryGpioPin, UpdatableValue<Boolean>> inputGpioValues = new ConcurrentHashMap<>();
    private GpioController gpio;
    private Boolean available;
    @Value("${w1BaseDir:/sys/devices/w1_bus_master1}")
    private Path w1BaseDir;

    @SneakyThrows
    void init() {
        for (RaspberryGpioPin pin : RaspberryGpioPin.values(PinMode.DIGITAL_INPUT, null)) {
            digitalListeners.put(pin, new CopyOnWriteArrayList<>());
        }

        if (isGPIOAvailable()) {
            RaspberryGpioPin.occupyPins("1-Wire", RaspberryGpioPin.PIN7);
            for (RaspberryGpioPin pin : RaspberryGpioPin.values(PinMode.DIGITAL_INPUT, PinPullResistance.PULL_DOWN)) {
                if (pin.getOccupied() == null) {
                    GpioPinDigital gpioPinDigital = getDigitalInput(pin, PinPullResistance.PULL_DOWN);
                    if (gpioPinDigital.isHigh()) {
                        gpioPinDigital.setMode(PinMode.DIGITAL_OUTPUT);
                        ((GpioPinDigitalOutput) gpioPinDigital).setState(PinState.LOW);
                        gpioPinDigital.setMode(PinMode.DIGITAL_INPUT);
                    }
                    gpioPinDigital.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
                    inputGpioValues.put(pin, UpdatableValue.wrap(gpioPinDigital.isHigh(), pin.name()));
                    gpioPinDigital.addListener((GpioPinListenerDigital) event -> digitalListeners.get(pin).forEach(t -> t.consumer.accept(event)));
                    digitalListeners.get(pin).add(new PinListener("INPUT_VALUE_LISTENER", event -> inputGpioValues.get(pin).update(event.getState().isHigh())));
                }
            }

            /*addGpioListener(RaspberryGpioPin.PIN40, PinState.HIGH, () -> {
                log.info("Fire switch HotSpot on Gpio event");
                wirelessManager.enableHotspot(60);
            });*/
        }
    }

    private GpioController getGpio() {
        if (gpio == null) {
            gpio = GpioFactory.getInstance();
        }
        return gpio;
    }

    public boolean isGPIOAvailable() {
        if (available == null) {
            if (EntityContext.isTestEnvironment()) {
                available = false;
            } else {
                try {
                    getGpio();
                    available = true;
                } catch (Throwable ignore) {
                    available = false;
                }
            }
            if (!available) {
                entityContext.disableFeature(EntityContext.DeviceFeature.GPIO);
            }
        }
        return available;
    }

    public boolean getValue(RaspberryGpioPin pin) {
        return inputGpioValues.get(pin).getValue();
    }

    public void setValue(RaspberryGpioPin pin, PinState pinState) {
        if (available) {
            log.info("Set pin <{}> value <{}>", pin.name(), pinState.name());
            getDigitalOutput(pin).setState(pinState);
        }
    }

    public void setPwmValue(RaspberryGpioPin pin, int value) {
        if (available) {
            assertDigitalInputPin(pin, PinMode.PWM_OUTPUT, "Unable to set pwm output for pin: " + pin.name());
            if (value < 0 || value > 255) {
                throw new IllegalArgumentException("Unable to set pwm value: " + value + ".");
            }
            log.info("Set pwm pin <{}> value <{}>", pin.name(), value);
            GpioPinPwmOutput gpioPinPwmOutput = (GpioPinPwmOutput) gpioPinDigital(pin, PinMode.PWM_OUTPUT, null);
            gpioPinPwmOutput.setPwmRange(255);
            gpioPinPwmOutput.setPwm(value);
        }
    }

    private GpioPinDigitalInput getDigitalInput(RaspberryGpioPin pin, PinPullResistance pinPullResistance) {
        return (GpioPinDigitalInput) gpioPinDigital(pin, PinMode.DIGITAL_INPUT, pinPullResistance);
    }

    private GpioPinDigitalOutput getDigitalOutput(RaspberryGpioPin pin) {
        return (GpioPinDigitalOutput) gpioPinDigital(pin, PinMode.DIGITAL_OUTPUT, null);
    }

    private GpioPinDigital gpioPinDigital(RaspberryGpioPin pin, PinMode pinMode, PinPullResistance pinPullResistance) {
        if (pin.getOccupied() != null) {
            throw new IllegalArgumentException("Unable to get GpioPinDigital for occupied pin by: " + pin.getOccupied());
        }
        assertDigitalInputPin(pin, PinMode.DIGITAL_INPUT, "Unable to get GpioPinDigital for pin: " + pin.name());
        GpioPinDigital provisionedPin = (GpioPinDigital) getGpio().getProvisionedPin(pin.getPin());
        if (provisionedPin == null) {
            provisionedPin = (GpioPinDigital) getGpio().provisionPin(pin.getPin(), pin.name(), pinMode);
        }
        if (provisionedPin.getMode() != pinMode) {
            provisionedPin.setMode(pinMode);
        }
        if (pinPullResistance != null && pin.getPin().supportsPinPullResistance() && provisionedPin.getPullResistance() != pinPullResistance) {
            provisionedPin.setPullResistance(pinPullResistance);
        }
        return provisionedPin;
    }

    /*public void removeAllTriggersAndPinsFor(HasTriggersEntity hasTriggersEntity) {
        if (activeTriggers.containsKey(hasTriggersEntity)) {
            activeTriggers.get(hasTriggersEntity).forEach(activeTrigger -> {

                log.info("Remove trigger: " + getTriggerName(activeTrigger.gpioTrigger));
                activeTrigger.gpioPIN.removeTrigger(activeTrigger.gpioTrigger);

                log.info("Unprovise source Pin: " + activeTrigger.gpioPIN.getName());
                /* TODO: some problem if(getGpio().getProvisionedPin(activeTrigger.gpioPIN.findPin()) != null) {
                    getGpio().unprovisionPin(activeTrigger.gpioPIN);
                }*/

              /*  if (activeTrigger.gpioTrigger instanceof OutputTargetedGpioTrigger) {
                    GpioPinDigitalOutput targetPin = ((GpioSetStateTrigger) activeTrigger.gpioTrigger).getTargetPin();
                    resetAndUnprovisePin(targetPin);
                } else {
                    throw new IllegalStateException("Developer. need improve additional trigger to remove");
                }
            });
            activeTriggers.remove(hasTriggersEntity);
        }
    }*/

    public void addGpioListener(String name, RaspberryGpioPin pin, PinState pinState, Runnable listener) {
        if (pinState == null) {
            throw new IllegalArgumentException("PinState must be not null");
        }
        assertDigitalInputPin(pin, PinMode.DIGITAL_INPUT, "Unable to add pin listener for not input pin mode");
        this.setGpioPinMode(pin, PinMode.DIGITAL_INPUT, pinState == PinState.HIGH ? PinPullResistance.PULL_DOWN : PinPullResistance.PULL_UP);

        this.digitalListeners.get(pin).add(new PinListener(name, event -> {
            if (event.getState() == pinState) {
                listener.run();
            }
        }));
    }

    public void addGpioListener(String name, RaspberryGpioPin pin, Consumer<PinState> listener) {
        assertDigitalInputPin(pin, PinMode.DIGITAL_INPUT, "Unable to add pin listener for not input pin mode");
        this.setGpioPinMode(pin, PinMode.DIGITAL_INPUT, null);
        this.digitalListeners.get(pin).add(new PinListener(name, event -> {
            listener.accept(event.getState());
        }));
    }

    private void assertDigitalInputPin(RaspberryGpioPin pin, PinMode digitalInput, String s) {
        if (!pin.getPin().getSupportedPinModes().contains(digitalInput)) {
            throw new IllegalArgumentException(s);
        }
    }

    public void setPullResistance(RaspberryGpioPin pin, PinPullResistance pullResistance) {
        getDigitalInput(pin, pullResistance);
    }

    public void setGpioPinMode(RaspberryGpioPin pin, PinMode pinMode, PinPullResistance pinPullResistance) {
        if (available) {
            GpioPin input = getDigitalInput(pin, pinPullResistance);
            if (input.getMode() != pinMode) {
                input.setMode(pinMode);
            }
        }
    }

  /*  public void addTrigger(HasTriggersEntity hasTriggersEntity, TriggerBaseEntity triggerBaseEntity, GpioPinDigitalInput input, GpioTrigger trigger) {
        log.info("Activate trigger: " + getTriggerName(trigger) + " for input: " + getPINName(input.getPin().getName()) + ". Device owner: " + hasTriggersEntity.getEntityID());
        input.addTrigger(trigger);
        if (!activeTriggers.containsKey(hasTriggersEntity)) {
            activeTriggers.put(hasTriggersEntity, new ArrayList<>());
        }
        List<ActiveTrigger> triggers = activeTriggers.get(hasTriggersEntity);
        triggers.addEnum(new ActiveTrigger(hasTriggersEntity, triggerBaseEntity, trigger, input));
    }*/

    /*public SpiDevice spiOpen(SpiChannel channel, int speed, SpiMode mode) throws IOException {
        return SpiFactory.getInstance(channel, speed, mode);
    }*/

    public void delay(int howLong) {
        Gpio.delay(howLong);
    }

    public byte[] spiXfer(SpiDevice handle, byte txByte) throws IOException {
        return handle.write(txByte);
    }

    public int spiXfer(SpiDevice handle, byte[] txBytes, byte[] data) throws IOException {
        byte[] bytes = handle.write(txBytes);
        for (int i = 0; i < data.length; i++) {
            if (bytes.length < i) {
                break;
            }
            data[i] = bytes[i];
        }
        return bytes.length;
    }

    public Set<String> getIButtons() throws IOException {
        return Files.list(w1BaseDir)
                .map(path -> path.getFileName().toString())
                .filter(path -> path.startsWith("01-")).collect(Collectors.toSet());
    }

    public Float getDS18B20Value(String sensorID) {
        DefaultKeyValue<Long, Float> pair = ds18B20Values.get(sensorID);
        if (pair != null) {
            if (System.currentTimeMillis() - pair.getKey() < entityContext.getSettingValue(RaspberryOneWireIntervalSetting.class) * 1000) {
                return pair.getValue();
            }
        } else {
            pair = new DefaultKeyValue<>(-1L, -1F);
            ds18B20Values.put(sensorID, pair);
        }
        pair.setKey(System.currentTimeMillis());

        List<String> rawDataAsLines = getRawDataAsLines(sensorID);
        float value = -1;
        if (rawDataAsLines != null) {
            String line = rawDataAsLines.get(1);
            value = Float.parseFloat(line.substring(line.indexOf("t=") + "t=".length())) / 1000;
        }
        pair.setValue(value);

        return pair.getValue();
    }

    private List<String> getRawDataAsLines(String sensorID) {
        if (EntityContext.isTestEnvironment()) {
            Random r = new Random(System.currentTimeMillis());
            return Arrays.asList("", "sd sd sd sd ff zz cc vv aa t=" + (10000 + r.nextInt(40000)));
        }

        Path path = w1BaseDir.resolve(sensorID).resolve("w1_slave");
        try {
            return FileUtils.readLines(path.toFile());
        } catch (IOException e) {
            log.error("Error while get RawData for sensor with id: " + sensorID);
            return null;
        }
    }

    @SneakyThrows
    public List<String> getDS18B20() {
        if (EntityContext.isTestEnvironment()) {
            return Collections.singletonList("28-test000011");
        }
        return Files.readAllLines(w1BaseDir.resolve("w1_master_slaves")).stream()
                .filter(sensorID -> sensorID != null && sensorID.startsWith("28-"))
                .collect(Collectors.toList());
    }

    public GpioPin getGpioPin(RaspberryGpioPin gpioPin) {
        return getGpio().getProvisionedPin(gpioPin.getPin());
    }

    @AllArgsConstructor
    public static class PinListener {
        @Getter
        private String name;
        private Consumer<GpioPinDigitalStateChangeEvent> consumer;
    }
}
