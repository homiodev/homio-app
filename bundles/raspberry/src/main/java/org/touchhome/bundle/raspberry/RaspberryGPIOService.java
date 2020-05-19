package org.touchhome.bundle.raspberry;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.gpio.trigger.GpioSetStateTrigger;
import com.pi4j.io.gpio.trigger.GpioTrigger;
import com.pi4j.io.spi.SpiChannel;
import com.pi4j.io.spi.SpiDevice;
import com.pi4j.io.spi.SpiFactory;
import com.pi4j.io.spi.SpiMode;
import com.pi4j.wiringpi.Gpio;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.hardware.WirelessManager;
import org.touchhome.bundle.api.util.UpdatableValue;
import org.touchhome.bundle.raspberry.settings.RaspberryOneWireIntervalSetting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class RaspberryGPIOService {
    private final EntityContext entityContext;
    private final WirelessManager wirelessManager;

    private GpioController gpio;
    private Boolean available;
    private final Map<String, DefaultKeyValue<Long, Float>> ds18B20Values = new HashMap<>();
    private final Map<RaspberryGpioPin, UpdatableValue<PinState>> gpioDigitalListeners = new ConcurrentHashMap<>();

    @Value("${w1BaseDir:/sys/devices/w1_bus_master1}")
    private Path w1BaseDir;

    @SneakyThrows
    void init() {
        if (isGPIOAvailable()) {
            addGpioListenerDigital(RaspberryGpioPin.PIN40, PinMode.DIGITAL_INPUT, event -> {
                log.info("Fired HotSpot creation on GPIO event state: <{}>", event.getState().isHigh());
                if (event.getState().isHigh()) {
                    wirelessManager.enableHotspot(60);
                }
            });
        }
    }

//    private Map<HasTriggersEntity, List<ActiveTrigger>> activeTriggers = new HashMap<>();

    private GpioController getGpio() {
        if (gpio == null) {
            gpio = GpioFactory.getInstance();
        }
        return gpio;
    }

    private boolean isGPIOAvailable() {
        if (available == null) {
            if (EntityContext.isTestApplication()) {
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

    public PinState getState(RaspberryGpioPin pin) {
        UpdatableValue<PinState> pinStateUpdatableValue = gpioDigitalListeners.get(pin);
        if (pinStateUpdatableValue == null) {
            GpioPinDigital gpioPinDigital = getGpioPinDigital(pin.getPin(), PinMode.DIGITAL_OUTPUT);
            pinStateUpdatableValue = UpdatableValue.wrap(gpioPinDigital.getState(), pin.name());
            gpioPinDigital.addListener((GpioPinListenerDigital) event -> gpioDigitalListeners.get(pin).update(event.getState()));
            gpioDigitalListeners.put(pin, pinStateUpdatableValue);
        }

        return pinStateUpdatableValue.getValue();
    }

    private GpioPin getGpioPin(Pin pin, PinMode pinMode) {
        GpioPin provisionedPin = getGpio().getProvisionedPin(pin);
        if (provisionedPin != null) {
            return provisionedPin;
        }
        return getGpio().provisionPin(pin, pinMode);
    }

    private GpioPinDigital getGpioPinDigital(Pin pin, PinMode pinMode) {
        GpioPin provisionedPin = getGpio().getProvisionedPin(pin);
        if (provisionedPin != null) {
            return (GpioPinDigital) provisionedPin;
        }
        return (GpioPinDigital) getGpio().provisionPin(pin, pinMode);
    }

  /*  public GpioPinDigitalInput getDigitalInput(RaspberryGpioPin raspberryGpioPin, PinPullResistance pinPullResistance) {
        return getDigitalInput(raspberryGpioPin.getPin(), pinPullResistance);
    }*/

    private GpioPinDigitalInput getDigitalInput(RaspberryGpioPin raspberryGpioPin, PinPullResistance pinPullResistance) {
        Pin pin = raspberryGpioPin.getPin();
        GpioPinDigitalInput input = (GpioPinDigitalInput) getGpio().getProvisionedPin(pin);
        if (input == null) {
            log.info("Acquire Gpio input pin: " + pin.getName() + ". PullResistance: " + pinPullResistance.getName());
            input = getGpio().provisionDigitalInputPin(pin, pin.getName(), pinPullResistance);
        }
        return input;
    }

    private GpioPinDigitalOutput getDigitalOutput(RaspberryGpioPin raspberryGpioPin) {
        Pin pin = raspberryGpioPin.getPin();
        GpioPinDigitalOutput output = (GpioPinDigitalOutput) getGpio().getProvisionedPin(pin);
        if (output == null) {
            // output = getGpio().provisionDigitalOutputPin(pin, getPINName(pin.getName()), PinState.LOW);
        }
        return output;
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

    public void addGpioListenerDigital(RaspberryGpioPin pin, PinMode pinMode, GpioPinListenerDigital gpioPinListenerDigital) {
        log.info("Request adding listener for GPIO pin: <{}>. Mode: <{}>", pin.getPin().getName(), pinMode.getName());
        GpioPin input = getGpioPin(pin.getPin(), pinMode);
        input.setPullResistance(PinPullResistance.PULL_DOWN);
        input.addListener(gpioPinListenerDigital);
        log.info("Successes request adding listener for GPIO pin: <{}>. Mode: <{}>", pin.getPin().getName(), pinMode.getName());
    }

    public void setGpioPinMode(RaspberryGpioPin pin, PinMode pinMode, PinPullResistance pinPullResistance) {
        log.info("Set Mode <{}> for GPIO pin <{}>", pinMode.getName(), pin.getPin().getName());
        GpioPin input = getGpioPin(pin.getPin(), pinMode);
        input.setPullResistance(pinPullResistance);
    }

  /*  public void addTrigger(HasTriggersEntity hasTriggersEntity, TriggerBaseEntity triggerBaseEntity, GpioPinDigitalInput input, GpioTrigger trigger) {
        log.info("Activate trigger: " + getTriggerName(trigger) + " for input: " + getPINName(input.getPin().getName()) + ". Device owner: " + hasTriggersEntity.getEntityID());
        input.addTrigger(trigger);
        if (!activeTriggers.containsKey(hasTriggersEntity)) {
            activeTriggers.put(hasTriggersEntity, new ArrayList<>());
        }
        List<ActiveTrigger> triggers = activeTriggers.get(hasTriggersEntity);
        triggers.add(new ActiveTrigger(hasTriggersEntity, triggerBaseEntity, trigger, input));
    }*/

    private String getTriggerName(GpioTrigger trigger) {
        if (trigger instanceof GpioSetStateTrigger) {
            return "GpioSetStateTrigger for PIN: " + ((GpioSetStateTrigger) trigger).getTargetPin().getName(); // targetPIN - output
        }
        return trigger.toString();
    }

    private void resetAndUnprovisePin(GpioPin gpioPin) {
        if (gpioPin instanceof GpioPinDigitalOutput) {
            log.info("Reset pin: " + gpioPin.getName());
            try {
                getGpio().setState(PinState.HIGH, (GpioPinDigitalOutput) gpioPin);
            } catch (Exception ex) {
                log.warn("Unable reset state pin: " + gpioPin.getName(), ex);
            }
        }
        try {
            log.info("Unprovise pin: " + gpioPin.getName());
            getGpio().unprovisionPin(gpioPin);
        } catch (Exception ex) {
            log.warn("Unable unprovise pin: " + gpioPin.getName(), ex);
        }
    }

    public void setState(RaspberryGpioPin raspberryGpioPin, PinState pinState) {
        getDigitalOutput(raspberryGpioPin).setState(pinState);
        //getGpio().setState(pinState, getDigitalOutput(raspberryGpioPin.getPin()));
    }

    public void assertMode(GpioPin gpioPin, PinMode pinMode) {
        if (!getGpio().isMode(PinMode.DIGITAL_OUTPUT, gpioPin)) {
            throw new IllegalStateException("Assert fail. Gpio pin: " + gpioPin.getName() + " has not: " +
                    pinMode.getName() + " but has: " + getGpio().getMode(gpioPin));
        }
    }

    public SpiDevice spiOpen(SpiChannel channel, int speed, SpiMode mode) throws IOException {
        return SpiFactory.getInstance(channel, speed, mode);
    }

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
        if (EntityContext.isTestApplication()) {
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
        if (EntityContext.isTestApplication()) {
            return Collections.singletonList("28-test000011");
        }
        return Files.readAllLines(w1BaseDir.resolve("w1_master_slaves")).stream()
                .filter(sensorID -> sensorID != null && sensorID.startsWith("28-"))
                .collect(Collectors.toList());
    }

/*    private class ActiveTrigger {
        private HasTriggersEntity hasTriggersEntity;
        private TriggerBaseEntity triggerBaseEntity;
        private GpioTrigger gpioTrigger;
        private GpioPinDigitalInput gpioPIN;

        public ActiveTrigger(HasTriggersEntity hasTriggersEntity, TriggerBaseEntity triggerBaseEntity, GpioTrigger gpioTrigger, GpioPinDigitalInput gpioPIN) {
            this.hasTriggersEntity = hasTriggersEntity;
            this.triggerBaseEntity = triggerBaseEntity;
            this.gpioTrigger = gpioTrigger;
            this.gpioPIN = gpioPIN;
        }
    }*/
}
