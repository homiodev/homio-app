package org.homio.addon.homekit;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.CharacteristicEnum;
import io.github.hapjava.characteristics.ExceptionalConsumer;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.accessoryinformation.*;
import io.github.hapjava.characteristics.impl.airquality.*;
import io.github.hapjava.characteristics.impl.audio.MuteCharacteristic;
import io.github.hapjava.characteristics.impl.audio.VolumeCharacteristic;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxideLevelCharacteristic;
import io.github.hapjava.characteristics.impl.carbondioxidesensor.CarbonDioxidePeakLevelCharacteristic;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxideLevelCharacteristic;
import io.github.hapjava.characteristics.impl.carbonmonoxidesensor.CarbonMonoxidePeakLevelCharacteristic;
import io.github.hapjava.characteristics.impl.common.*;
import io.github.hapjava.characteristics.impl.fan.*;
import io.github.hapjava.characteristics.impl.filtermaintenance.FilterLifeLevelCharacteristic;
import io.github.hapjava.characteristics.impl.filtermaintenance.ResetFilterIndicationCharacteristic;
import io.github.hapjava.characteristics.impl.garagedoor.CurrentDoorStateCharacteristic;
import io.github.hapjava.characteristics.impl.garagedoor.CurrentDoorStateEnum;
import io.github.hapjava.characteristics.impl.garagedoor.TargetDoorStateCharacteristic;
import io.github.hapjava.characteristics.impl.garagedoor.TargetDoorStateEnum;
import io.github.hapjava.characteristics.impl.humiditysensor.TargetRelativeHumidityCharacteristic;
import io.github.hapjava.characteristics.impl.inputsource.*;
import io.github.hapjava.characteristics.impl.lightbulb.BrightnessCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.ColorTemperatureCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.HueCharacteristic;
import io.github.hapjava.characteristics.impl.lightbulb.SaturationCharacteristic;
import io.github.hapjava.characteristics.impl.lock.LockCurrentStateCharacteristic;
import io.github.hapjava.characteristics.impl.lock.LockCurrentStateEnum;
import io.github.hapjava.characteristics.impl.lock.LockTargetStateCharacteristic;
import io.github.hapjava.characteristics.impl.lock.LockTargetStateEnum;
import io.github.hapjava.characteristics.impl.motionsensor.MotionDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.occupancysensor.OccupancyDetectedCharacteristic;
import io.github.hapjava.characteristics.impl.occupancysensor.OccupancyDetectedEnum;
import io.github.hapjava.characteristics.impl.slat.CurrentTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.slat.TargetTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.television.*;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeControlTypeCharacteristic;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeControlTypeEnum;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeSelectorCharacteristic;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeSelectorEnum;
import io.github.hapjava.characteristics.impl.thermostat.*;
import io.github.hapjava.characteristics.impl.valve.RemainingDurationCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.*;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.homio.addon.homekit.enums.HomekitAccessoryType.*;
import static org.homio.addon.homekit.enums.HomekitCharacteristicType.*;

@Log4j2
public class HomekitCharacteristicFactory {

    public static final int COLOR_TEMPERATURE_MIN_MIREDS = 107; // ~9300 K
    public static final int COLOR_TEMPERATURE_MAX_MIREDS = 556; // ~1800 K
    public static final int CURRENT_TEMPERATURE_MIN_CELSIUS = -100;

    public static final Map<HomekitAccessoryType, HomekitCharacteristicType[]> MANDATORY_CHARACTERISTICS = new HashMap<>() {
        {
            put(AirQualitySensor, new HomekitCharacteristicType[]{AirQuality});
            put(BasicFan, new HomekitCharacteristicType[]{OnState});
            put(Battery, new HomekitCharacteristicType[]{BatteryLevel, BatteryLowStatus});
            put(CarbonDioxideSensor, new HomekitCharacteristicType[]{CarbonDioxideDetectedState});
            put(CarbonMonoxideSensor, new HomekitCharacteristicType[]{CarbonMonoxideDetectedState});
            put(ContactSensor, new HomekitCharacteristicType[]{ContactSensorState});
            put(Door, new HomekitCharacteristicType[]{CurrentPosition, TargetPosition, PositionState});
            put(Doorbell, new HomekitCharacteristicType[]{ProgrammableSwitchEvent});
            put(Fan, new HomekitCharacteristicType[]{ActiveStatus});
            put(Faucet, new HomekitCharacteristicType[]{ActiveStatus});
            put(Filter, new HomekitCharacteristicType[]{FilterChangeIndication});
            put(GarageDoorOpener, new HomekitCharacteristicType[]{CurrentDoorState, TargetDoorState});
            put(HeaterCooler, new HomekitCharacteristicType[]{
                    ActiveStatus, CurrentHeaterCoolerState, TargetHeaterCoolerState, CurrentTemperature
            });
            put(HumiditySensor, new HomekitCharacteristicType[]{RelativeHumidity});
            put(InputSource, new HomekitCharacteristicType[]{});
            put(IrrigationSystem, new HomekitCharacteristicType[]{Active, InUseStatus, ProgramMode});
            put(LeakSensor, new HomekitCharacteristicType[]{LeakDetectedState});
            put(LightSensor, new HomekitCharacteristicType[]{LightLevel});
            put(LightBulb, new HomekitCharacteristicType[]{OnState});
            put(Lock, new HomekitCharacteristicType[]{LockCurrentState, LockTargetState});
            put(Microphone, new HomekitCharacteristicType[]{Mute});
            put(MotionSensor, new HomekitCharacteristicType[]{MotionDetectedState});
            put(OccupancySensor, new HomekitCharacteristicType[]{OccupancyDetectedState});
            put(Outlet, new HomekitCharacteristicType[]{OnState, InUseStatus});
            put(SecuritySystem, new HomekitCharacteristicType[]{SecuritySystemCurrentState, SecuritySystemTargetState});
            put(SmartSpeaker, new HomekitCharacteristicType[]{CurrentMediaState, TargetMediaState});
            put(SmokeSensor, new HomekitCharacteristicType[]{SmokeDetectedState});
            put(Slat, new HomekitCharacteristicType[]{CurrentSlatState});
            put(Speaker, new HomekitCharacteristicType[]{Mute});
            put(PushButton, new HomekitCharacteristicType[]{ProgrammableSwitchEvent});
            put(Switch, new HomekitCharacteristicType[]{OnState});
            put(Television, new HomekitCharacteristicType[]{Active});
            put(TelevisionSpeaker, new HomekitCharacteristicType[]{Mute});
            put(TemperatureSensor, new HomekitCharacteristicType[]{CurrentTemperature});
            put(Thermostat, new HomekitCharacteristicType[]{TargetHeatingCoolingState, CurrentTemperature});
            put(Valve, new HomekitCharacteristicType[]{ActiveStatus, InUseStatus});
            put(Window, new HomekitCharacteristicType[]{CurrentPosition, TargetPosition, PositionState});
            put(WindowCovering, new HomekitCharacteristicType[]{TargetPosition, CurrentPosition, PositionState});
        }
    };


    private static final Map<HomekitCharacteristicType, Function<HomekitEndpointContext, Characteristic>> OPTIONAL_CHARACTERISTICS = new HashMap<>() {{
        put(Manufacturer, HomekitCharacteristicFactory::createManufacturerCharacteristic);
        put(Model, HomekitCharacteristicFactory::createModelCharacteristic);
        put(SerialNumber, HomekitCharacteristicFactory::createSerialNumberCharacteristic);
        put(FirmwareRevision, HomekitCharacteristicFactory::createFirmwareRevisionCharacteristic);
        put(HardwareRevision, HomekitCharacteristicFactory::createHardwareRevisionCharacteristic);
        put(Identify, HomekitCharacteristicFactory::createIdentifyCharacteristic);
        put(Name, c -> createNameCharacteristic(c, null));

        put(OnState, HomekitCharacteristicFactory::createOnStateCharacteristic);
        put(Active, HomekitCharacteristicFactory::createActiveCharacteristic);
        put(Mute, HomekitCharacteristicFactory::createMuteCharacteristic);
        put(ObstructionStatus, HomekitCharacteristicFactory::createObstructionDetectedCharacteristic);

        put(MotionDetectedState, HomekitCharacteristicFactory::createMotionDetectedCharacteristic);
        put(OccupancyDetectedState, HomekitCharacteristicFactory::createOccupancyDetectedCharacteristic);

        put(Brightness, HomekitCharacteristicFactory::createBrightnessCharacteristic);
        put(Hue, HomekitCharacteristicFactory::createHueCharacteristic);
        put(Saturation, HomekitCharacteristicFactory::createSaturationCharacteristic);
        put(ColorTemperature, HomekitCharacteristicFactory::createColorTemperatureCharacteristic);

        put(CurrentTemperature, HomekitCharacteristicFactory::createCurrentTemperatureCharacteristic);
        put(TargetTemperature, HomekitCharacteristicFactory::createTargetTemperatureCharacteristic);
        put(CurrentHeatingCoolingState, HomekitCharacteristicFactory::createCurrentHeatingCoolingStateCharacteristic);
        put(TargetHeatingCoolingState, HomekitCharacteristicFactory::createTargetHeatingCoolingStateCharacteristic);
        put(CoolingThresholdTemperature, HomekitCharacteristicFactory::createCoolingThresholdTemperatureCharacteristic);
        put(HeatingThresholdTemperature, HomekitCharacteristicFactory::createHeatingThresholdTemperatureCharacteristic);
        put(TargetRelativeHumidity, HomekitCharacteristicFactory::createTargetRelativeHumidityCharacteristic);

        put(LockCurrentState, HomekitCharacteristicFactory::createLockCurrentStateCharacteristic);
        put(LockTargetState, HomekitCharacteristicFactory::createLockTargetStateCharacteristic);

        put(HoldPosition, HomekitCharacteristicFactory::createHoldPositionCharacteristic);
        put(CurrentHorizontalTiltAngle, HomekitCharacteristicFactory::createCurrentHorizontalTiltAngleCharacteristic);
        put(TargetHorizontalTiltAngle, HomekitCharacteristicFactory::createTargetHorizontalTiltAngleCharacteristic);
        put(CurrentVerticalTiltAngle, HomekitCharacteristicFactory::createCurrentVerticalTiltAngleCharacteristic);
        put(TargetVerticalTiltAngle, HomekitCharacteristicFactory::createTargetVerticalTiltAngleCharacteristic);
        put(CurrentTiltAngle, HomekitCharacteristicFactory::createCurrentTiltAngleCharacteristic);
        put(TargetTiltAngle, HomekitCharacteristicFactory::createTargetTiltAngleCharacteristic);

        put(CurrentDoorState, HomekitCharacteristicFactory::createCurrentDoorStateCharacteristic);
        put(TargetDoorState, HomekitCharacteristicFactory::createTargetDoorStateCharacteristic);

        put(CurrentFanState, HomekitCharacteristicFactory::createCurrentFanStateCharacteristic);
        put(TargetFanState, HomekitCharacteristicFactory::createTargetFanStateCharacteristic);
        put(RotationDirection, HomekitCharacteristicFactory::createRotationDirectionCharacteristic);
        put(RotationSpeed, HomekitCharacteristicFactory::createRotationSpeedCharacteristic);
        put(SwingMode, HomekitCharacteristicFactory::createSwingModeCharacteristic);

        put(AirQuality, HomekitCharacteristicFactory::createAirQualityCharacteristic);
        put(OzoneDensity, HomekitCharacteristicFactory::createOzoneDensityCharacteristic);
        put(NitrogenDioxideDensity, HomekitCharacteristicFactory::createNitrogenDioxideDensityCharacteristic);
        put(SulphurDioxideDensity, HomekitCharacteristicFactory::createSulphurDioxideDensityCharacteristic);
        put(PM25Density, HomekitCharacteristicFactory::createPM25DensityCharacteristic);
        put(PM10Density, HomekitCharacteristicFactory::createPM10DensityCharacteristic);
        put(VOCDensity, HomekitCharacteristicFactory::createVOCDensityCharacteristic);

        put(CarbonDioxideLevel, HomekitCharacteristicFactory::createCarbonDioxideLevelCharacteristic);
        put(CarbonDioxidePeakLevel, HomekitCharacteristicFactory::createCarbonDioxidePeakLevelCharacteristic);
        put(CarbonMonoxideLevel, HomekitCharacteristicFactory::createCarbonMonoxideLevelCharacteristic);
        put(CarbonMonoxidePeakLevel, HomekitCharacteristicFactory::createCarbonMonoxideLevelCharacteristic);

        put(FilterLifeLevel, HomekitCharacteristicFactory::createFilterLifeLevelCharacteristic);
        put(FilterResetIndication, HomekitCharacteristicFactory::createFilterResetIndicationCharacteristic);

        put(RemainingDuration, HomekitCharacteristicFactory::createRemainingDurationCharacteristic);

        put(Volume, HomekitCharacteristicFactory::createVolumeCharacteristic);
        put(CurrentMediaState, HomekitCharacteristicFactory::createCurrentMediaStateCharacteristic);
        put(TargetMediaState, HomekitCharacteristicFactory::createTargetMediaStateCharacteristic);
        put(VolumeControlType, HomekitCharacteristicFactory::createVolumeControlTypeCharacteristic);
        put(VolumeSelector, HomekitCharacteristicFactory::createVolumeSelectorCharacteristic);

        put(ProgrammableSwitchEvent, HomekitCharacteristicFactory::createProgrammableSwitchEventCharacteristic);

        put(ActiveIdentifier, HomekitCharacteristicFactory::createActiveIdentifierCharacteristic);
        put(ConfiguredName, HomekitCharacteristicFactory::createConfiguredNameCharacteristic);
        put(InputDeviceType, HomekitCharacteristicFactory::createInputDeviceTypeCharacteristic);
        put(InputSourceType, HomekitCharacteristicFactory::createInputSourceTypeCharacteristic);
        put(TargetVisibilityState, HomekitCharacteristicFactory::createTargetVisibilityStateCharacteristic);
        put(SleepDiscoveryMode, HomekitCharacteristicFactory::createSleepDiscoveryModeCharacteristic);
        put(RemoteKey, HomekitCharacteristicFactory::createRemoteKeyCharacteristic);
        put(PowerMode, HomekitCharacteristicFactory::createPowerModeCharacteristic);
        put(PictureMode, HomekitCharacteristicFactory::createPictureModeCharacteristic);
        put(ClosedCaptions, HomekitCharacteristicFactory::createClosedCaptionsCharacteristic);
    }};

    public static List<Characteristic> buildRequiredCharacteristics(HomekitEndpointContext context) {
        List<Characteristic> characteristics = new ArrayList<>();
        var types = MANDATORY_CHARACTERISTICS.get(context.endpoint().getAccessoryType());
        if (types != null) {
            for (HomekitCharacteristicType type : types) {
                characteristics.add(OPTIONAL_CHARACTERISTICS.get(type).apply(context));
            }
        }
        return characteristics;
    }

    public static List<Characteristic> buildInitialCharacteristics(HomekitEndpointContext context,
                                                                   @Nullable String name) {
        List<Characteristic> characteristics = new ArrayList<>();
        addIfNotNull(characteristics, createIdentifyCharacteristic(context));
        addIfNotNull(characteristics, createManufacturerCharacteristic(context));
        addIfNotNull(characteristics, createModelCharacteristic(context));
        addIfNotNull(characteristics, createSerialNumberCharacteristic(context));
        addIfNotNull(characteristics, createFirmwareRevisionCharacteristic(context));
        addIfNotNull(characteristics, createHardwareRevisionCharacteristic(context));
        addIfNotNull(characteristics, createNameCharacteristic(context, name));
        return characteristics;
    }

    public static List<Characteristic> buildOptionalCharacteristics(HomekitEndpointContext context) {
        var characteristics = buildInitialCharacteristics(context, null);

        for (Map.Entry<HomekitCharacteristicType, Function<HomekitEndpointContext, Characteristic>> entry : OPTIONAL_CHARACTERISTICS.entrySet()) {
            HomekitCharacteristicType charType = entry.getKey();
            if (isAccessoryInfoType(charType)) {
                continue;
            }
            try {
                Characteristic characteristic = entry.getValue().apply(context);
                addIfNotNull(characteristics, characteristic);
            } catch (Exception e) {
                log.error("Error creating characteristic {} for endpoint {} [{}]: {}",
                        charType, context.endpoint().getName(), context.endpoint().getName(), e.getMessage(), e);
            }
        }
        return characteristics;
    }

    private static void addIfNotNull(List<Characteristic> list, @Nullable Characteristic c) {
        if (c != null) {
            list.add(c);
        }
    }

    private static boolean isAccessoryInfoType(HomekitCharacteristicType type) {
        return type == Manufacturer || type == Model || type == SerialNumber ||
               type == FirmwareRevision || type == HardwareRevision || type == Name || type == Identify;
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            @Nullable ContextVar.Variable variable, Class<T> klazz,
            @Nullable List<T> customEnumList, boolean invertedDefault) {
        EnumMap<T, Object> map = new EnumMap<>(klazz);
        if (variable == null) {
            for (var k : klazz.getEnumConstants()) map.put(k, Integer.toString(k.getCode()));
            if (customEnumList != null) customEnumList.addAll(map.keySet());
            return map;
        }
        boolean isHomioSwitchLike = variable instanceof OnOffType;
        boolean isHomioNumeric = variable instanceof DecimalType;
        boolean itemSpecificInversion = false; // variable.getJsonData("inverted", false);
        boolean finalInversion = invertedDefault ^ itemSpecificInversion;
        String onValue = OnOffType.ON.toString();
        String offValue = OnOffType.OFF.toString();
        T mappedOffEnumValue = null, mappedOnEnumValue = null;

        for (T k : klazz.getEnumConstants()) {
            int code = k.getCode();
            if (isHomioSwitchLike) {
                if (code == 0) {
                    map.put(k, finalInversion ? onValue : offValue);
                    mappedOffEnumValue = k;
                } else if (code == 1) {
                    map.put(k, finalInversion ? offValue : onValue);
                    mappedOnEnumValue = k;
                } else {
                    map.put(k, Integer.toString(code));
                }
            } else if (isHomioNumeric) {
                map.put(k, Integer.toString(code));
            } else {
                map.put(k, k.toString());
            }
        }
        if (customEnumList != null && customEnumList.isEmpty()) {
            if (isHomioSwitchLike && mappedOffEnumValue != null && mappedOnEnumValue != null) {
                customEnumList.add(mappedOffEnumValue);
                customEnumList.add(mappedOnEnumValue);
            } else {
                customEnumList.addAll(map.keySet());
            }
        }
        return map;
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(@Nullable ContextVar.Variable v, Class<T> k) {
        return createMapping(v, k, null, false);
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(@Nullable ContextVar.Variable v, Class<T> k, @Nullable List<T> l) {
        return createMapping(v, k, l, false);
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(@Nullable ContextVar.Variable v, Class<T> k, boolean i) {
        return createMapping(v, k, null, i);
    }

    public static <T> T getKeyFromMapping(@Nullable ContextVar.Variable variable, Map<T, Object> mapping, T defaultValue) {
        if (variable == null) {
            return defaultValue;
        }
        var state = variable.getValue();
        if (state == null) {
            return defaultValue;
        }
        String valueToMatch;
        switch (state) {
            case DecimalType ignored -> valueToMatch = Integer.toString(state.intValue());
            case OnOffType ignored -> valueToMatch = state.toString();
            case StringType ignored -> valueToMatch = state.toString();
            default -> {
                return defaultValue;
            }
        }
        for (Map.Entry<T, Object> entry : mapping.entrySet()) {
            Object mappingConfigValue = entry.getValue();
            if (mappingConfigValue instanceof String && valueToMatch.equalsIgnoreCase((String) mappingConfigValue))
                return entry.getKey();
            if (mappingConfigValue instanceof List) {
                for (Object listItem : (List<?>) mappingConfigValue) {
                    if (valueToMatch.equalsIgnoreCase(listItem.toString())) return entry.getKey();
                }
            }
        }
        return defaultValue;
    }

    private static <T extends Enum<T> & CharacteristicEnum> CompletableFuture<T> getCurrentEnumValue(@Nullable ContextVar.Variable v, Map<T, Object> m, T d) {
        return (v == null) ? completedFuture(d) : completedFuture(getKeyFromMapping(v, m, d));
    }

    public static <T extends Enum<T>> void setHomioVariableFromEnum(@Nullable ContextVar.Variable variable, T enumFromHk, Map<T, Object> mapping) {
        if (variable == null || enumFromHk == null) return;
        Object homioValueRepresentation = mapping.get(enumFromHk);
        if (homioValueRepresentation instanceof List)
            homioValueRepresentation = ((List<?>) homioValueRepresentation).isEmpty() ? null : ((List<?>) homioValueRepresentation).getFirst();
        if (homioValueRepresentation != null) {
            String stringValueToSet = homioValueRepresentation.toString();
            try {
                if (OnOffType.ON.toString().equalsIgnoreCase(stringValueToSet) || OnOffType.OFF.toString().equalsIgnoreCase(stringValueToSet)) {
                    variable.set(OnOffType.of(stringValueToSet.toUpperCase()));
                } else if (variable instanceof DecimalType) {
                    variable.set(new DecimalType(new BigDecimal(stringValueToSet)));
                } else {
                    variable.set(new StringType(stringValueToSet));
                }
            } catch (Exception e) {
                log.error("Error setting Homio variable {} from HomeKit enum {}: {}", variable, enumFromHk, e.getMessage());
            }
        } else {
            log.warn("No Homio mapping for HK enum {} on var {}", enumFromHk, variable);
        }
    }

    private static Supplier<CompletableFuture<Boolean>> getBooleanSupplier(@Nullable ContextVar.Variable v) {
        return () -> (v == null) ? completedFuture(false) : completedFuture(v.getValue().boolValue());
    }

    private static ExceptionalConsumer<Boolean> setBooleanConsumer(@Nullable ContextVar.Variable v) {
        return val -> {
            if (v != null) v.set(OnOffType.of(val));
        };
    }

    private static Supplier<CompletableFuture<Integer>> getIntSupplier(@Nullable ContextVar.Variable v, int def) {
        return () -> (v == null) ? completedFuture(def) : completedFuture(v.getValue().intValue(def));
    }

    private static ExceptionalConsumer<Integer> setIntConsumer(@Nullable ContextVar.Variable v) {
        return val -> {
            if (v != null) v.set(new DecimalType(val));
        };
    }

    private static Supplier<CompletableFuture<Double>> getDoubleSupplier(@Nullable ContextVar.Variable v, double def) {
        return () -> (v == null) ? completedFuture(def) : completedFuture(v.getValue().doubleValue(def));
    }

    private static ExceptionalConsumer<Double> setDoubleConsumer(@Nullable ContextVar.Variable v) {
        return val -> {
            if (v != null) v.set(new DecimalType(val));
        };
    }

    private static Supplier<CompletableFuture<String>> getStringSupplier(@Nullable ContextVar.Variable v, String def) {
        return () -> (v == null) ? completedFuture(def) : completedFuture(v.getValue().stringValue(def));
    }

    private static ExceptionalConsumer<String> setStringConsumer(@Nullable ContextVar.Variable v) {
        return val -> {
            if (v != null) v.set(new StringType(val));
        };
    }

    private static Supplier<CompletableFuture<Double>> getTemperatureSupplier(@Nullable ContextVar.Variable v, double dC) {
        return () -> (v == null) ? completedFuture(dC) : completedFuture(v.getValue().doubleValue(dC));
    }

    private static ExceptionalConsumer<Double> setTemperatureConsumer(@Nullable ContextVar.Variable v) {
        return valC -> {
            if (v != null) v.set(new DecimalType(valC));
        };
    }

    public static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(
            @Nullable ContextVar.Variable v,
            HomekitEndpointContext c,
            HomekitCharacteristicType t) {
        if (v == null) return cb -> {
        };
        String k = c.owner().getEntityID() + "_" + c.endpoint().getId() + "_" + t.name() + "_sub";
        return cb -> {
            if (cb == null) return;
            v.addListener(k, s -> cb.changed());
        };
    }

    public static Runnable getUnsubscriber(@Nullable ContextVar.Variable v, HomekitEndpointContext c, HomekitCharacteristicType t) {
        if (v == null) return () -> {
        };
        String k = c.owner().getEntityID() + "_" + c.endpoint().getId() + "_" + t.name() + "_sub";
        return () -> v.removeListener(k);
    }

    // Accessory Info
    private static ManufacturerCharacteristic createManufacturerCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getManufacturer());
        return (v == null) ? new ManufacturerCharacteristic(() -> completedFuture("Homio")) : new ManufacturerCharacteristic(getStringSupplier(v, "Homio"));
    }

    private static ModelCharacteristic createModelCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getModel());
        return (v == null) ? new ModelCharacteristic(() -> completedFuture("Virtual")) : new ModelCharacteristic(getStringSupplier(v, "Virtual"));
    }

    private static SerialNumberCharacteristic createSerialNumberCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getSerialNumber());
        return (v == null) ? new SerialNumberCharacteristic(
                () -> completedFuture(c.endpoint().getSerialNumber()))
                : new SerialNumberCharacteristic(getStringSupplier(v, c.endpoint().getSerialNumber()));
    }

    private static FirmwareRevisionCharacteristic createFirmwareRevisionCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getFirmwareRevision());
        return (v == null) ? new FirmwareRevisionCharacteristic(() -> completedFuture("1.0")) : new FirmwareRevisionCharacteristic(getStringSupplier(v, "1.0"));
    }

    private static HardwareRevisionCharacteristic createHardwareRevisionCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getHardwareRevision());
        return (v == null) ? new HardwareRevisionCharacteristic(() -> completedFuture("1.0")) : new HardwareRevisionCharacteristic(getStringSupplier(v, "1.0"));
    }

    private static IdentifyCharacteristic createIdentifyCharacteristic(HomekitEndpointContext c) {
        return new IdentifyCharacteristic((onOff) -> log.info("Identify called for: {}. Value: {}",
                c.endpoint().getName(), onOff));
    }

    private static NameCharacteristic createNameCharacteristic(HomekitEndpointContext c, @Nullable String name) {
        return new NameCharacteristic(() -> completedFuture(Objects.toString(name, c.endpoint().getTitle())));
    }

    // Common Binary
    private static OnCharacteristic createOnStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getOnState());
        if (v == null) return null;
        return new OnCharacteristic(getBooleanSupplier(v), setBooleanConsumer(v), getSubscriber(v, c, OnState), getUnsubscriber(v, c, OnState));
    }

    private static ActiveCharacteristic createActiveCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getActiveState());
        if (v == null) return null;
        Map<ActiveEnum, Object> m = createMapping(v, ActiveEnum.class);
        return new ActiveCharacteristic(
                () -> getCurrentEnumValue(v, m, ActiveEnum.INACTIVE),
                val -> setHomioVariableFromEnum(v, val, m),
                getSubscriber(v, c, Active),
                getUnsubscriber(v, c, Active));
    }

    private static MuteCharacteristic createMuteCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getMute());
        if (v == null) return null;
        return new MuteCharacteristic(
                getBooleanSupplier(v),
                setBooleanConsumer(v),
                getSubscriber(v, c, Mute),
                getUnsubscriber(v, c, Mute));
    }

    private static ObstructionDetectedCharacteristic createObstructionDetectedCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getObstructionDetected());
        if (v == null) return null;
        return new ObstructionDetectedCharacteristic(
                getBooleanSupplier(v),
                getSubscriber(v, c, ObstructionStatus),
                getUnsubscriber(v, c, ObstructionStatus));
    }

    // Sensors
    private static MotionDetectedCharacteristic createMotionDetectedCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getDetectedState());
        if (v == null || c.endpoint().getAccessoryType() != HomekitAccessoryType.MotionSensor) return null;
        return new MotionDetectedCharacteristic(
                getBooleanSupplier(v),
                getSubscriber(v, c, MotionDetectedState),
                getUnsubscriber(v, c, MotionDetectedState));
    }

    private static OccupancyDetectedCharacteristic createOccupancyDetectedCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getDetectedState());
        if (v == null || c.endpoint().getAccessoryType() != HomekitAccessoryType.OccupancySensor) return null;
        Map<OccupancyDetectedEnum, Object> m = createMapping(v, OccupancyDetectedEnum.class);
        return new OccupancyDetectedCharacteristic(() -> getCurrentEnumValue(v, m, OccupancyDetectedEnum.NOT_DETECTED),
                getSubscriber(v, c, OccupancyDetectedState),
                getUnsubscriber(v, c, OccupancyDetectedState));
    }

    // Lightbulb
    private static BrightnessCharacteristic createBrightnessCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getBrightness());
        if (v == null) return null;
        return new BrightnessCharacteristic(getIntSupplier(v, 100), setIntConsumer(v), getSubscriber(v, c, Brightness), getUnsubscriber(v, c, Brightness));
    }

    private static HueCharacteristic createHueCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getHue());
        if (v == null) return null;
        Supplier<CompletableFuture<Double>> getter = () -> completedFuture(v.getValue().doubleValue(0));
        ExceptionalConsumer<Double> setter = hue -> v.set(new DecimalType(hue));
        return new HueCharacteristic(getter, setter, getSubscriber(v, c, Hue), getUnsubscriber(v, c, Hue));
    }

    private static SaturationCharacteristic createSaturationCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getSaturation());
        if (v == null) return null;
        Supplier<CompletableFuture<Double>> getter = () -> completedFuture(v.getValue().doubleValue(0));
        ExceptionalConsumer<Double> setter = sat -> v.set(new DecimalType(sat));
        return new SaturationCharacteristic(getter, setter, getSubscriber(v, c, Saturation), getUnsubscriber(v, c, Saturation));
    }

    private static ColorTemperatureCharacteristic createColorTemperatureCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getColorTemperature());
        if (v == null) return null;
        int minM = (int) v.getMinValue(COLOR_TEMPERATURE_MIN_MIREDS);
        int maxM = (int) v.getMaxValue(COLOR_TEMPERATURE_MAX_MIREDS);
        boolean inv = c.endpoint().isColorTemperatureInverted();
        Supplier<CompletableFuture<Integer>> getter = () -> {
            int mV = v.getValue().intValue(minM);
            if (inv) mV = maxM - (mV - minM);
            return completedFuture(mV);
        };
        ExceptionalConsumer<Integer> setter = mVHk -> {
            int sV = mVHk;
            if (inv) sV = maxM - (sV - minM);
            v.set(new DecimalType(sV));
        };
        return new ColorTemperatureCharacteristic(minM, maxM, getter, setter, getSubscriber(v, c, ColorTemperature), getUnsubscriber(v, c, ColorTemperature));
    }

    // Temperature & Climate
    private static CurrentTemperatureCharacteristic createCurrentTemperatureCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentTemperature());
        if (v == null) return null;
        return new CurrentTemperatureCharacteristic(v.getMinValue(CURRENT_TEMPERATURE_MIN_CELSIUS), v.getMaxValue(100), v.getStep(0.1),
                getTemperatureSupplier(v, 20.0),
                getSubscriber(v, c, CurrentTemperature),
                getUnsubscriber(v, c, CurrentTemperature));
    }

    private static TargetTemperatureCharacteristic createTargetTemperatureCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetTemperature());
        if (v == null) return null;
        return new TargetTemperatureCharacteristic(v.getMinValue(10.0), v.getMaxValue(38.0),
                v.getStep(0.5), getTemperatureSupplier(v, 21.0),
                setTemperatureConsumer(v),
                getSubscriber(v, c, TargetTemperature),
                getUnsubscriber(v, c, TargetTemperature));
    }

    private static CurrentHeatingCoolingStateCharacteristic createCurrentHeatingCoolingStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentHeatingCoolingState());
        if (v == null) return null;
        List<CurrentHeatingCoolingStateEnum> vals = new ArrayList<>();
        Map<CurrentHeatingCoolingStateEnum, Object> m = createMapping(v, CurrentHeatingCoolingStateEnum.class, vals);
        var i = vals.isEmpty() ? null : vals.toArray(new CurrentHeatingCoolingStateEnum[0]);
        return new CurrentHeatingCoolingStateCharacteristic(i,
                () -> getCurrentEnumValue(v, m, CurrentHeatingCoolingStateEnum.OFF),
                getSubscriber(v, c, CurrentHeatingCoolingState),
                getUnsubscriber(v, c, CurrentHeatingCoolingState));
    }

    private static TargetHeatingCoolingStateCharacteristic createTargetHeatingCoolingStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetHeatingCoolingState());
        if (v == null) return null;
        List<TargetHeatingCoolingStateEnum> vals = new ArrayList<>();
        Map<TargetHeatingCoolingStateEnum, Object> m = createMapping(v, TargetHeatingCoolingStateEnum.class, vals);
        return new TargetHeatingCoolingStateCharacteristic(vals.isEmpty() ? null : vals.toArray(new TargetHeatingCoolingStateEnum[0]), () -> getCurrentEnumValue(v, m, TargetHeatingCoolingStateEnum.OFF), newVal -> setHomioVariableFromEnum(v, newVal, m),
                getSubscriber(v, c, TargetHeatingCoolingState), getUnsubscriber(v, c, TargetHeatingCoolingState));
    }

    private static CoolingThresholdTemperatureCharacteristic createCoolingThresholdTemperatureCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCoolingThresholdTemperature());
        if (v == null) return null;
        return new CoolingThresholdTemperatureCharacteristic(v.getMinValue(10.0), v.getMaxValue(35.0), v.getStep(0.5), getTemperatureSupplier(v, 25.0),
                setTemperatureConsumer(v), getSubscriber(v, c, CoolingThresholdTemperature), getUnsubscriber(v, c, CoolingThresholdTemperature));
    }

    private static HeatingThresholdTemperatureCharacteristic createHeatingThresholdTemperatureCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getHeatingThresholdTemperature());
        if (v == null) return null;
        return new HeatingThresholdTemperatureCharacteristic(v.getMinValue(0.0), v.getMaxValue(25.0), v.getStep(0.5), getTemperatureSupplier(v, 18.0),
                setTemperatureConsumer(v), getSubscriber(v, c, HeatingThresholdTemperature), getUnsubscriber(v, c, HeatingThresholdTemperature));
    }

    private static TargetRelativeHumidityCharacteristic createTargetRelativeHumidityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetRelativeHumidity());
        if (v == null) return null;
        return new TargetRelativeHumidityCharacteristic(getDoubleSupplier(v, 45.0F), setDoubleConsumer(v),
                getSubscriber(v, c, TargetRelativeHumidity), getUnsubscriber(v, c, TargetRelativeHumidity));
    }

    // Lock
    private static LockCurrentStateCharacteristic createLockCurrentStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getLockCurrentState());
        if (v == null) return null;
        Map<LockCurrentStateEnum, Object> m = createMapping(v, LockCurrentStateEnum.class);
        return new LockCurrentStateCharacteristic(() -> getCurrentEnumValue(v, m, LockCurrentStateEnum.UNKNOWN),
                getSubscriber(v, c, LockCurrentState), getUnsubscriber(v, c, LockCurrentState));
    }

    private static LockTargetStateCharacteristic createLockTargetStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getLockTargetState());
        if (v == null) return null;
        Map<LockTargetStateEnum, Object> m = createMapping(v, LockTargetStateEnum.class);
        return new LockTargetStateCharacteristic(() -> getCurrentEnumValue(v, m, LockTargetStateEnum.UNSECURED), newVal -> setHomioVariableFromEnum(v, newVal, m),
                getSubscriber(v, c, LockTargetState), getUnsubscriber(v, c, LockTargetState));
    }

    // Window Covering & Slats
    private static HoldPositionCharacteristic createHoldPositionCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getWindowHoldPosition());
        if (v == null) return null;
        return new HoldPositionCharacteristic(value -> {
            if (!value) {
                return;
            }
            v.set(OnOffType.ON);
        });
    }

    private static CurrentHorizontalTiltAngleCharacteristic createCurrentHorizontalTiltAngleCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentHorizontalTiltAngle());
        if (v == null) return null;
        return new CurrentHorizontalTiltAngleCharacteristic(getIntSupplier(v, 0),
                getSubscriber(v, c, CurrentHorizontalTiltAngle),
                getUnsubscriber(v, c, CurrentHorizontalTiltAngle));
    }

    private static TargetHorizontalTiltAngleCharacteristic createTargetHorizontalTiltAngleCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetHorizontalTiltAngle());
        if (v == null) return null;
        return new TargetHorizontalTiltAngleCharacteristic(getIntSupplier(v, 0), setIntConsumer(v),
                getSubscriber(v, c, TargetHorizontalTiltAngle), getUnsubscriber(v, c, TargetHorizontalTiltAngle));
    }

    private static CurrentVerticalTiltAngleCharacteristic createCurrentVerticalTiltAngleCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentVerticalTiltAngle());
        if (v == null) return null;
        return new CurrentVerticalTiltAngleCharacteristic(getIntSupplier(v, 0),
                getSubscriber(v, c, CurrentVerticalTiltAngle), getUnsubscriber(v, c, CurrentVerticalTiltAngle));
    }

    private static TargetVerticalTiltAngleCharacteristic createTargetVerticalTiltAngleCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetVerticalTiltAngle());
        if (v == null) return null;
        return new TargetVerticalTiltAngleCharacteristic(getIntSupplier(v, 0), setIntConsumer(v),
                getSubscriber(v, c, TargetVerticalTiltAngle), getUnsubscriber(v, c, TargetVerticalTiltAngle));
    }

    private static CurrentTiltAngleCharacteristic createCurrentTiltAngleCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentTiltAngle());
        if (v == null) return null;
        return new CurrentTiltAngleCharacteristic(getIntSupplier(v, 0),
                getSubscriber(v, c, CurrentTiltAngle), getUnsubscriber(v, c, CurrentTiltAngle));
    }

    private static TargetTiltAngleCharacteristic createTargetTiltAngleCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetTiltAngle());
        if (v == null) return null;
        return new TargetTiltAngleCharacteristic(getIntSupplier(v, 0), setIntConsumer(v),
                getSubscriber(v, c, TargetTiltAngle), getUnsubscriber(v, c, TargetTiltAngle));
    }

    // Garage Door
    private static CurrentDoorStateCharacteristic createCurrentDoorStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentDoorState());
        if (v == null || c.endpoint().getAccessoryType() != HomekitAccessoryType.GarageDoorOpener) return null;
        Map<CurrentDoorStateEnum, Object> m = createMapping(v, CurrentDoorStateEnum.class, true);
        return new CurrentDoorStateCharacteristic(() -> getCurrentEnumValue(v, m, CurrentDoorStateEnum.CLOSED),
                getSubscriber(v, c, CurrentDoorState), getUnsubscriber(v, c, CurrentDoorState));
    }

    private static TargetDoorStateCharacteristic createTargetDoorStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetDoorState());
        if (v == null || c.endpoint().getAccessoryType() != HomekitAccessoryType.GarageDoorOpener) return null;
        Map<TargetDoorStateEnum, Object> m = createMapping(v, TargetDoorStateEnum.class, true);
        return new TargetDoorStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetDoorStateEnum.CLOSED),
                newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, c, TargetDoorState), getUnsubscriber(v, c, TargetDoorState));
    }

    // Fan
    private static CurrentFanStateCharacteristic createCurrentFanStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentFanState());
        if (v == null) return null;
        Map<CurrentFanStateEnum, Object> m = createMapping(v, CurrentFanStateEnum.class);
        return new CurrentFanStateCharacteristic(() -> getCurrentEnumValue(v, m, CurrentFanStateEnum.INACTIVE),
                getSubscriber(v, c, CurrentFanState), getUnsubscriber(v, c, CurrentFanState));
    }

    private static TargetFanStateCharacteristic createTargetFanStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetFanState());
        if (v == null) return null;
        Map<TargetFanStateEnum, Object> m = createMapping(v, TargetFanStateEnum.class);
        return new TargetFanStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetFanStateEnum.MANUAL), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, c, TargetFanState), getUnsubscriber(v, c, TargetFanState));
    }

    private static RotationDirectionCharacteristic createRotationDirectionCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getRotationDirection());
        if (v == null) return null;
        Map<RotationDirectionEnum, Object> m = createMapping(v, RotationDirectionEnum.class);
        return new RotationDirectionCharacteristic(() -> getCurrentEnumValue(v, m, RotationDirectionEnum.CLOCKWISE), newVal -> setHomioVariableFromEnum(v, newVal, m),
                getSubscriber(v, c, RotationDirection), getUnsubscriber(v, c, RotationDirection));
    }

    private static RotationSpeedCharacteristic createRotationSpeedCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getRotationSpeed());
        if (v == null) return null;
        return new RotationSpeedCharacteristic(getDoubleSupplier(v, 0), setDoubleConsumer(v), getSubscriber(v, c, RotationSpeed), getUnsubscriber(v, c, RotationSpeed));
    }

    private static SwingModeCharacteristic createSwingModeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getFanSwingMode());
        if (v == null) return null;
        Map<SwingModeEnum, Object> m = createMapping(v, SwingModeEnum.class);
        return new SwingModeCharacteristic(() -> getCurrentEnumValue(v, m, SwingModeEnum.SWING_DISABLED), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, c, SwingMode), getUnsubscriber(v, c, SwingMode));
    }

    // Air Quality Sensor
    private static AirQualityCharacteristic createAirQualityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getAirQuality());
        if (v == null) return null;
        Map<AirQualityEnum, Object> m = createMapping(v, AirQualityEnum.class);
        return new AirQualityCharacteristic(() -> getCurrentEnumValue(v, m, AirQualityEnum.UNKNOWN), getSubscriber(v, c, AirQuality), getUnsubscriber(v, c, AirQuality));
    }

    private static OzoneDensityCharacteristic createOzoneDensityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getOzoneDensity());
        if (v == null) return null;
        return new OzoneDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, OzoneDensity), getUnsubscriber(v, c, OzoneDensity));
    }

    private static NitrogenDioxideDensityCharacteristic createNitrogenDioxideDensityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getNitrogenDioxideDensity());
        if (v == null) return null;
        return new NitrogenDioxideDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, NitrogenDioxideDensity), getUnsubscriber(v, c, NitrogenDioxideDensity));
    }

    private static SulphurDioxideDensityCharacteristic createSulphurDioxideDensityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getSulphurDioxideDensity());
        if (v == null) return null;
        return new SulphurDioxideDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, SulphurDioxideDensity), getUnsubscriber(v, c, SulphurDioxideDensity));
    }

    private static PM25DensityCharacteristic createPM25DensityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getPm25Density());
        if (v == null) return null;
        return new PM25DensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, PM25Density), getUnsubscriber(v, c, PM25Density));
    }

    private static PM10DensityCharacteristic createPM10DensityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getPm10Density());
        if (v == null) return null;
        return new PM10DensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, PM10Density), getUnsubscriber(v, c, PM10Density));
    }

    private static VOCDensityCharacteristic createVOCDensityCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getVocDensity());
        if (v == null) return null;
        return new VOCDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, VOCDensity), getUnsubscriber(v, c, VOCDensity));
    }

    // CO2/CO Sensors
    private static CarbonDioxideLevelCharacteristic createCarbonDioxideLevelCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCarbonDioxideLevel());
        if (v == null) return null;
        return new CarbonDioxideLevelCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, c, CarbonDioxideLevel), getUnsubscriber(v, c, CarbonDioxideLevel));
    }

    private static CarbonDioxidePeakLevelCharacteristic createCarbonDioxidePeakLevelCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCarbonDioxidePeakLevel());
        if (v == null) return null;
        return new CarbonDioxidePeakLevelCharacteristic(getDoubleSupplier(v, 0),
                getSubscriber(v, c, CarbonDioxidePeakLevel), getUnsubscriber(v, c, CarbonDioxidePeakLevel));
    }

    private static CarbonMonoxideLevelCharacteristic createCarbonMonoxideLevelCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCarbonMonoxideLevel());
        if (v == null) return null;
        return new CarbonMonoxideLevelCharacteristic(getDoubleSupplier(v, 0),
                getSubscriber(v, c, CarbonMonoxideLevel), getUnsubscriber(v, c, CarbonMonoxideLevel));
    }

    private static CarbonMonoxidePeakLevelCharacteristic createCarbonMonoxidePeakLevelCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCarbonMonoxidePeakLevel());
        if (v == null) return null;
        return new CarbonMonoxidePeakLevelCharacteristic(getDoubleSupplier(v, 0),
                getSubscriber(v, c, CarbonMonoxidePeakLevel), getUnsubscriber(v, c, CarbonMonoxidePeakLevel));
    }

    private static FilterLifeLevelCharacteristic createFilterLifeLevelCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getFilterLifeLevel());
        if (v == null) return null;
        return new FilterLifeLevelCharacteristic(getDoubleSupplier(v, 100), getSubscriber(v, c, FilterLifeLevel), getUnsubscriber(v, c, FilterLifeLevel));
    }

    private static ResetFilterIndicationCharacteristic createFilterResetIndicationCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getFilterResetIndication());
        if (v == null) return null;
        return new ResetFilterIndicationCharacteristic(val -> v.set(OnOffType.ON));
    }

    private static RemainingDurationCharacteristic createRemainingDurationCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getRemainingDuration());
        if (v == null) return null;
        return new RemainingDurationCharacteristic(getIntSupplier(v, 0), getSubscriber(v, c, RemainingDuration), getUnsubscriber(v, c, RemainingDuration));
    }

    // Smart Speaker / Speaker / Television Speaker
    private static VolumeCharacteristic createVolumeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getVolume());
        if (v == null) return null;
        return new VolumeCharacteristic(getIntSupplier(v, 50), setIntConsumer(v), getSubscriber(v, c, Volume), getUnsubscriber(v, c, Volume));
    }

    private static CurrentMediaStateCharacteristic createCurrentMediaStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentMediaState());
        if (v == null) return null;
        Map<CurrentMediaStateEnum, Object> m = createMapping(v, CurrentMediaStateEnum.class);
        return new CurrentMediaStateCharacteristic(() -> getCurrentEnumValue(v, m, CurrentMediaStateEnum.UNKNOWN),
                getSubscriber(v, c, CurrentMediaState), getUnsubscriber(v, c, CurrentMediaState));
    }

    private static TargetMediaStateCharacteristic createTargetMediaStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetMediaState());
        if (v == null) return null;
        Map<TargetMediaStateEnum, Object> m = createMapping(v, TargetMediaStateEnum.class);
        return new TargetMediaStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetMediaStateEnum.STOP), newVal -> setHomioVariableFromEnum(v, newVal, m),
                getSubscriber(v, c, TargetMediaState), getUnsubscriber(v, c, TargetMediaState));
    }

    private static VolumeControlTypeCharacteristic createVolumeControlTypeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getVolumeControlType());
        if (v == null) return null;
        Map<VolumeControlTypeEnum, Object> m = createMapping(v, VolumeControlTypeEnum.class);
        return new VolumeControlTypeCharacteristic(() -> getCurrentEnumValue(v, m, VolumeControlTypeEnum.NONE),
                getSubscriber(v, c, VolumeControlType), getUnsubscriber(v, c, VolumeControlType));
    }

    private static VolumeSelectorCharacteristic createVolumeSelectorCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getVolumeSelector());
        if (v == null) return null;
        Map<VolumeSelectorEnum, Object> m = createMapping(v, VolumeSelectorEnum.class);
        return new VolumeSelectorCharacteristic(newVal -> setHomioVariableFromEnum(v, newVal, m));
    }

    // Stateless Programmable Switch / Doorbell
    private static ProgrammableSwitchEventCharacteristic createProgrammableSwitchEventCharacteristic(HomekitEndpointContext c) {
        /*ContextVar.Variable v = c.getVariable(c.endpoint().getProgrammableSwitchEvent());
        if (v == null) return null;
        List<ProgrammableSwitchEnum> validVals = Arrays.asList(
                ProgrammableSwitchEnum.SINGLE_PRESS,
                ProgrammableSwitchEnum.DOUBLE_PRESS,
                ProgrammableSwitchEnum.LONG_PRESS
        );
        ProgrammableSwitchEnum[] switchEnums = validVals.toArray(new ProgrammableSwitchEnum[0]);
        var characteristic = new ProgrammableSwitchEventCharacteristic(switchEnums, );
        String listenerKey = e.getEntityID() + "_" + PROGRAMMABLE_SWITCH_EVENT.name() + "_sub";
        v.addListener(listenerKey, newState -> {
            if (newState != null) {
                try {
                    int eventCode = newState.intValue(-1);
                    ProgrammableSwitchEnum hkEvent = null;
                    if (eventCode == 0)
                        hkEvent = ProgrammableSwitchEnum.SINGLE_PRESS;
                    else if (eventCode == 1)
                        hkEvent = ProgrammableSwitchEnum.DOUBLE_PRESS;
                    else if (eventCode == 2)
                        hkEvent = ProgrammableSwitchEnum.LONG_PRESS;
                    if (hkEvent != null) characteristic.sendEvent(hkEvent);
                } catch (Exception ex) {
                    log.error("Error processing ProgrammableSwitchEvent for {}: {}", e.getName(), ex.getMessage());
                }
            }
        });
        // HAP-Java might handle unsubscription internally, or you might need to:
        // characteristic.onUnsubscribe(() -> v.removeListener(listenerKey)); // If such a hook exists
        return characteristic;*/
        return null;
    }

    // Television & InputSource
    private static ActiveIdentifierCharacteristic createActiveIdentifierCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getActiveIdentifier());
        if (v == null) return null;
        return new ActiveIdentifierCharacteristic(getIntSupplier(v, 1), setIntConsumer(v),
                getSubscriber(v, c, ActiveIdentifier), getUnsubscriber(v, c, ActiveIdentifier));
    }

    private static ConfiguredNameCharacteristic createConfiguredNameCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getConfiguredName());
        if (v == null) return null;
        return new ConfiguredNameCharacteristic(getStringSupplier(v, "Input"), setStringConsumer(v),
                getSubscriber(v, c, ConfiguredName), getUnsubscriber(v, c, ConfiguredName));
    }

    private static InputDeviceTypeCharacteristic createInputDeviceTypeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getInputDeviceType());
        if (v == null) return null;
        Map<InputDeviceTypeEnum, Object> m = createMapping(v, InputDeviceTypeEnum.class);
        return new InputDeviceTypeCharacteristic(() -> getCurrentEnumValue(v, m, InputDeviceTypeEnum.OTHER),
                getSubscriber(v, c, InputDeviceType), getUnsubscriber(v, c, InputDeviceType));
    }

    private static InputSourceTypeCharacteristic createInputSourceTypeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getInputSourceType());
        if (v == null) return null;
        Map<InputSourceTypeEnum, Object> m = createMapping(v, InputSourceTypeEnum.class);
        return new InputSourceTypeCharacteristic(() -> getCurrentEnumValue(v, m, InputSourceTypeEnum.OTHER),
                getSubscriber(v, c, InputSourceType), getUnsubscriber(v, c, InputSourceType));
    }

    /*private static IdentifierCharacteristic createIdentifierInputSourceCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getIdentifierInputSource());
        if (v == null) return null;
        return new IdentifierCharacteristic(getIntSupplier(v, 1));
    }*/ // This is usually read-only unique ID for an InputSource

    /*private static CurrentVisibilityStateCharacteristic createCurrentVisibilityStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getCurrentVisibilityState());
        if (v == null) return null;
        Map<CurrentVisibilityStateEnum, Object> m = createMapping(e, v, CurrentVisibilityStateEnum.class);
        return new CurrentVisibilityStateCharacteristic(() -> getCurrentEnumValue(e, v, m, CurrentVisibilityStateEnum.SHOWN), getSubscriber(v, c, CURRENT_VISIBILITY_STATE), getUnsubscriber(v, c, CURRENT_VISIBILITY_STATE));
    }*/

    private static TargetVisibilityStateCharacteristic createTargetVisibilityStateCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getTargetVisibilityState());
        if (v == null) return null;
        Map<TargetVisibilityStateEnum, Object> m = createMapping(v, TargetVisibilityStateEnum.class);
        return new TargetVisibilityStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetVisibilityStateEnum.SHOWN),
                newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, c, TargetVisibilityState), getUnsubscriber(v, c, TargetVisibilityState));
    }

    private static SleepDiscoveryModeCharacteristic createSleepDiscoveryModeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getSleepDiscoveryMode());
        if (v == null) return null;
        Map<SleepDiscoveryModeEnum, Object> m = createMapping(v, SleepDiscoveryModeEnum.class);
        return new SleepDiscoveryModeCharacteristic(() -> getCurrentEnumValue(v, m, SleepDiscoveryModeEnum.ALWAYS_DISCOVERABLE),
                getSubscriber(v, c, SleepDiscoveryMode), getUnsubscriber(v, c, SleepDiscoveryMode));
    }

    private static RemoteKeyCharacteristic createRemoteKeyCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getRemoteKey());
        if (v == null) return null;
        Map<RemoteKeyEnum, Object> m = createMapping(v, RemoteKeyEnum.class);
        return new RemoteKeyCharacteristic(newVal -> setHomioVariableFromEnum(v, newVal, m));
    }

    private static PowerModeCharacteristic createPowerModeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getPowerModeSelection());
        if (v == null) return null;
        Map<PowerModeEnum, Object> m = createMapping(v, PowerModeEnum.class);
        return new PowerModeCharacteristic(newVal -> setHomioVariableFromEnum(v, newVal, m));
    }

    private static PictureModeCharacteristic createPictureModeCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getPictureMode());
        if (v == null) return null;
        Map<PictureModeEnum, Object> m = createMapping(v, PictureModeEnum.class);
        return new PictureModeCharacteristic(() -> getCurrentEnumValue(v, m, PictureModeEnum.OTHER),
                newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, c, PictureMode), getUnsubscriber(v, c, PictureMode));
    }

    private static ClosedCaptionsCharacteristic createClosedCaptionsCharacteristic(HomekitEndpointContext c) {
        ContextVar.Variable v = c.getVariable(c.endpoint().getClosedCaptions());
        if (v == null) return null;
        Map<ClosedCaptionsEnum, Object> m = createMapping(v, ClosedCaptionsEnum.class);
        return new ClosedCaptionsCharacteristic(() -> getCurrentEnumValue(v, m, ClosedCaptionsEnum.DISABLED),
                newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, c, ClosedCaptions), getUnsubscriber(v, c, ClosedCaptions));
    }

}