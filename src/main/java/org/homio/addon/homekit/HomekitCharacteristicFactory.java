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
            put(ACCESSORY_GROUP, new HomekitCharacteristicType[]{});
            put(AIR_QUALITY_SENSOR, new HomekitCharacteristicType[]{AIR_QUALITY});
            put(BASIC_FAN, new HomekitCharacteristicType[]{ON_STATE});
            put(BATTERY, new HomekitCharacteristicType[]{BATTERY_LEVEL, BATTERY_LOW_STATUS});
            put(CARBON_DIOXIDE_SENSOR, new HomekitCharacteristicType[]{CARBON_DIOXIDE_DETECTED_STATE});
            put(CARBON_MONOXIDE_SENSOR, new HomekitCharacteristicType[]{CARBON_MONOXIDE_DETECTED_STATE});
            put(CONTACT_SENSOR, new HomekitCharacteristicType[]{CONTACT_SENSOR_STATE});
            put(DOOR, new HomekitCharacteristicType[]{CURRENT_POSITION, TARGET_POSITION, POSITION_STATE});
            put(DOORBELL, new HomekitCharacteristicType[]{PROGRAMMABLE_SWITCH_EVENT});
            put(FAN, new HomekitCharacteristicType[]{ACTIVE_STATUS});
            put(FAUCET, new HomekitCharacteristicType[]{ACTIVE_STATUS});
            put(FILTER_MAINTENANCE, new HomekitCharacteristicType[]{FILTER_CHANGE_INDICATION});
            put(GARAGE_DOOR_OPENER, new HomekitCharacteristicType[]{CURRENT_DOOR_STATE, TARGET_DOOR_STATE});
            put(HEATER_COOLER, new HomekitCharacteristicType[]{ACTIVE_STATUS, CURRENT_HEATER_COOLER_STATE,
                    TARGET_HEATER_COOLER_STATE, CURRENT_TEMPERATURE});
            put(HUMIDITY_SENSOR, new HomekitCharacteristicType[]{RELATIVE_HUMIDITY});
            put(INPUT_SOURCE, new HomekitCharacteristicType[]{});
            put(IRRIGATION_SYSTEM, new HomekitCharacteristicType[]{ACTIVE, INUSE_STATUS, PROGRAM_MODE});
            put(LEAK_SENSOR, new HomekitCharacteristicType[]{LEAK_DETECTED_STATE});
            put(LIGHT_SENSOR, new HomekitCharacteristicType[]{LIGHT_LEVEL});
            put(LIGHTBULB, new HomekitCharacteristicType[]{ON_STATE});
            put(LOCK, new HomekitCharacteristicType[]{LOCK_CURRENT_STATE, LOCK_TARGET_STATE});
            put(MICROPHONE, new HomekitCharacteristicType[]{MUTE});
            put(MOTION_SENSOR, new HomekitCharacteristicType[]{MOTION_DETECTED_STATE});
            put(OCCUPANCY_SENSOR, new HomekitCharacteristicType[]{OCCUPANCY_DETECTED_STATE});
            put(OUTLET, new HomekitCharacteristicType[]{ON_STATE, INUSE_STATUS});
            put(SECURITY_SYSTEM,
                    new HomekitCharacteristicType[]{SECURITY_SYSTEM_CURRENT_STATE, SECURITY_SYSTEM_TARGET_STATE});
            put(SMART_SPEAKER, new HomekitCharacteristicType[]{CURRENT_MEDIA_STATE, TARGET_MEDIA_STATE});
            put(SMOKE_SENSOR, new HomekitCharacteristicType[]{SMOKE_DETECTED_STATE});
            put(SLAT, new HomekitCharacteristicType[]{CURRENT_SLAT_STATE});
            put(SPEAKER, new HomekitCharacteristicType[]{MUTE});
            put(STATELESS_PROGRAMMABLE_SWITCH, new HomekitCharacteristicType[]{PROGRAMMABLE_SWITCH_EVENT});
            put(SWITCH, new HomekitCharacteristicType[]{ON_STATE});
            put(TELEVISION, new HomekitCharacteristicType[]{ACTIVE});
            put(TELEVISION_SPEAKER, new HomekitCharacteristicType[]{MUTE});
            put(TEMPERATURE_SENSOR, new HomekitCharacteristicType[]{CURRENT_TEMPERATURE});
            put(THERMOSTAT, new HomekitCharacteristicType[]{TARGET_HEATING_COOLING_STATE, CURRENT_TEMPERATURE});
            put(VALVE, new HomekitCharacteristicType[]{ACTIVE_STATUS, INUSE_STATUS});
            put(WINDOW, new HomekitCharacteristicType[]{CURRENT_POSITION, TARGET_POSITION, POSITION_STATE});
            put(WINDOW_COVERING, new HomekitCharacteristicType[]{TARGET_POSITION, CURRENT_POSITION, POSITION_STATE});
        }
    };

    private static final Map<HomekitCharacteristicType, Function<HomekitEndpointEntity, Characteristic>> OPTIONAL_CHARACTERISTICS = new HashMap<>() {
        {
            // Accessory Information Service
            put(MANUFACTURER, HomekitCharacteristicFactory::createManufacturerCharacteristic);
            put(MODEL, HomekitCharacteristicFactory::createModelCharacteristic);
            put(SERIAL_NUMBER, HomekitCharacteristicFactory::createSerialNumberCharacteristic);
            put(FIRMWARE_REVISION, HomekitCharacteristicFactory::createFirmwareRevisionCharacteristic);
            put(HARDWARE_REVISION, HomekitCharacteristicFactory::createHardwareRevisionCharacteristic);
            put(IDENTIFY, HomekitCharacteristicFactory::createIdentifyCharacteristic);
            put(NAME, HomekitCharacteristicFactory::createNameCharacteristic);

            // Common Binary States
            put(ON_STATE, HomekitCharacteristicFactory::createOnStateCharacteristic);
            put(ACTIVE, HomekitCharacteristicFactory::createActiveCharacteristic);
            put(MUTE, HomekitCharacteristicFactory::createMuteCharacteristic);
            put(OBSTRUCTION_STATUS, HomekitCharacteristicFactory::createObstructionDetectedCharacteristic);

            // Common Sensor Detected States
            put(MOTION_DETECTED_STATE, HomekitCharacteristicFactory::createMotionDetectedCharacteristic);
            put(OCCUPANCY_DETECTED_STATE, HomekitCharacteristicFactory::createOccupancyDetectedCharacteristic);

            // Lightbulb Specific
            put(BRIGHTNESS, HomekitCharacteristicFactory::createBrightnessCharacteristic);
            put(HUE, HomekitCharacteristicFactory::createHueCharacteristic);
            put(SATURATION, HomekitCharacteristicFactory::createSaturationCharacteristic);
            put(COLOR_TEMPERATURE, HomekitCharacteristicFactory::createColorTemperatureCharacteristic);

            // Temperature & Climate
            put(CURRENT_TEMPERATURE, HomekitCharacteristicFactory::createCurrentTemperatureCharacteristic);
            put(TARGET_TEMPERATURE, HomekitCharacteristicFactory::createTargetTemperatureCharacteristic);
            put(CURRENT_HEATING_COOLING_STATE, HomekitCharacteristicFactory::createCurrentHeatingCoolingStateCharacteristic);
            put(TARGET_HEATING_COOLING_STATE, HomekitCharacteristicFactory::createTargetHeatingCoolingStateCharacteristic);
            put(COOLING_THRESHOLD_TEMPERATURE, HomekitCharacteristicFactory::createCoolingThresholdTemperatureCharacteristic);
            put(HEATING_THRESHOLD_TEMPERATURE, HomekitCharacteristicFactory::createHeatingThresholdTemperatureCharacteristic);
            put(TARGET_RELATIVE_HUMIDITY, HomekitCharacteristicFactory::createTargetRelativeHumidityCharacteristic);

            // Lock
            put(LOCK_CURRENT_STATE, HomekitCharacteristicFactory::createLockCurrentStateCharacteristic);
            put(LOCK_TARGET_STATE, HomekitCharacteristicFactory::createLockTargetStateCharacteristic);

            // Window Covering & Slats
            put(HOLD_POSITION, HomekitCharacteristicFactory::createHoldPositionCharacteristic);
            put(CURRENT_HORIZONTAL_TILT_ANGLE, HomekitCharacteristicFactory::createCurrentHorizontalTiltAngleCharacteristic);
            put(TARGET_HORIZONTAL_TILT_ANGLE, HomekitCharacteristicFactory::createTargetHorizontalTiltAngleCharacteristic);
            put(CURRENT_VERTICAL_TILT_ANGLE, HomekitCharacteristicFactory::createCurrentVerticalTiltAngleCharacteristic);
            put(TARGET_VERTICAL_TILT_ANGLE, HomekitCharacteristicFactory::createTargetVerticalTiltAngleCharacteristic);
            put(CURRENT_TILT_ANGLE, HomekitCharacteristicFactory::createCurrentTiltAngleCharacteristic);
            put(TARGET_TILT_ANGLE, HomekitCharacteristicFactory::createTargetTiltAngleCharacteristic);

            // Garage Door Opener
            put(CURRENT_DOOR_STATE, HomekitCharacteristicFactory::createCurrentDoorStateCharacteristic);
            put(TARGET_DOOR_STATE, HomekitCharacteristicFactory::createTargetDoorStateCharacteristic);

            // Fan
            put(CURRENT_FAN_STATE, HomekitCharacteristicFactory::createCurrentFanStateCharacteristic);
            put(TARGET_FAN_STATE, HomekitCharacteristicFactory::createTargetFanStateCharacteristic);
            put(ROTATION_DIRECTION, HomekitCharacteristicFactory::createRotationDirectionCharacteristic);
            put(ROTATION_SPEED, HomekitCharacteristicFactory::createRotationSpeedCharacteristic);
            put(SWING_MODE, HomekitCharacteristicFactory::createSwingModeCharacteristic);

            // Air Quality Sensor
            put(AIR_QUALITY, HomekitCharacteristicFactory::createAirQualityCharacteristic);
            put(OZONE_DENSITY, HomekitCharacteristicFactory::createOzoneDensityCharacteristic);
            put(NITROGEN_DIOXIDE_DENSITY, HomekitCharacteristicFactory::createNitrogenDioxideDensityCharacteristic);
            put(SULPHUR_DIOXIDE_DENSITY, HomekitCharacteristicFactory::createSulphurDioxideDensityCharacteristic);
            put(PM25_DENSITY, HomekitCharacteristicFactory::createPM25DensityCharacteristic);
            put(PM10_DENSITY, HomekitCharacteristicFactory::createPM10DensityCharacteristic);
            put(VOC_DENSITY, HomekitCharacteristicFactory::createVOCDensityCharacteristic);

            // Carbon Dioxide / Monoxide Sensors
            put(CARBON_DIOXIDE_LEVEL, HomekitCharacteristicFactory::createCarbonDioxideLevelCharacteristic);
            put(CARBON_DIOXIDE_PEAK_LEVEL, HomekitCharacteristicFactory::createCarbonDioxidePeakLevelCharacteristic);
            put(CARBON_MONOXIDE_LEVEL, HomekitCharacteristicFactory::createCarbonMonoxideLevelCharacteristic);
            put(CARBON_MONOXIDE_PEAK_LEVEL, HomekitCharacteristicFactory::createCarbonMonoxidePeakLevelCharacteristic);

            // Filter Maintenance
            put(FILTER_LIFE_LEVEL, HomekitCharacteristicFactory::createFilterLifeLevelCharacteristic);
            put(FILTER_RESET_INDICATION, HomekitCharacteristicFactory::createFilterResetIndicationCharacteristic);

            // Irrigation System / Valve
            put(REMAINING_DURATION, HomekitCharacteristicFactory::createRemainingDurationCharacteristic);

            // Smart Speaker / Speaker / Television Speaker
            put(VOLUME, HomekitCharacteristicFactory::createVolumeCharacteristic);
            put(CURRENT_MEDIA_STATE, HomekitCharacteristicFactory::createCurrentMediaStateCharacteristic);
            put(TARGET_MEDIA_STATE, HomekitCharacteristicFactory::createTargetMediaStateCharacteristic);
            put(VOLUME_CONTROL_TYPE, HomekitCharacteristicFactory::createVolumeControlTypeCharacteristic);
            put(VOLUME_SELECTOR, HomekitCharacteristicFactory::createVolumeSelectorCharacteristic);

            // Stateless Programmable Switch / Doorbell
            put(PROGRAMMABLE_SWITCH_EVENT, HomekitCharacteristicFactory::createProgrammableSwitchEventCharacteristic);

            // Television & Input Source
            put(ACTIVE_IDENTIFIER, HomekitCharacteristicFactory::createActiveIdentifierCharacteristic);
            put(CONFIGURED_NAME, HomekitCharacteristicFactory::createConfiguredNameCharacteristic);
            put(INPUT_DEVICE_TYPE, HomekitCharacteristicFactory::createInputDeviceTypeCharacteristic);
            put(INPUT_SOURCE_TYPE, HomekitCharacteristicFactory::createInputSourceTypeCharacteristic);
            put(TARGET_VISIBILITY_STATE, HomekitCharacteristicFactory::createTargetVisibilityStateCharacteristic);
            put(SLEEP_DISCOVERY_MODE, HomekitCharacteristicFactory::createSleepDiscoveryModeCharacteristic);
            put(REMOTE_KEY, HomekitCharacteristicFactory::createRemoteKeyCharacteristic);
            put(POWER_MODE, HomekitCharacteristicFactory::createPowerModeCharacteristic);
            put(PICTURE_MODE, HomekitCharacteristicFactory::createPictureModeCharacteristic);
            put(CLOSED_CAPTIONS, HomekitCharacteristicFactory::createClosedCaptionsCharacteristic);
        }
    };

    public static List<Characteristic> buildRequiredCharacteristics(HomekitEndpointEntity endpoint) {
        List<Characteristic> characteristics = new ArrayList<>();
        var types = MANDATORY_CHARACTERISTICS.get(endpoint.getAccessoryType());
        if (types != null) {
            for (HomekitCharacteristicType type : types) {
                characteristics.add(OPTIONAL_CHARACTERISTICS.get(type).apply(endpoint));
            }
        }
        return characteristics;
    }

    public static List<Characteristic> buildOptionalCharacteristics(HomekitEndpointEntity endpoint) {
        List<Characteristic> characteristics = new ArrayList<>();
        addIfNotNull(characteristics, createManufacturerCharacteristic(endpoint));
        addIfNotNull(characteristics, createModelCharacteristic(endpoint));
        addIfNotNull(characteristics, createSerialNumberCharacteristic(endpoint));
        addIfNotNull(characteristics, createFirmwareRevisionCharacteristic(endpoint));
        addIfNotNull(characteristics, createHardwareRevisionCharacteristic(endpoint));
        addIfNotNull(characteristics, createIdentifyCharacteristic(endpoint));
        addIfNotNull(characteristics, createNameCharacteristic(endpoint));

        for (Map.Entry<HomekitCharacteristicType, Function<HomekitEndpointEntity, Characteristic>> entry : OPTIONAL_CHARACTERISTICS.entrySet()) {
            HomekitCharacteristicType charType = entry.getKey();
            if (isAccessoryInfoType(charType)) {
                continue;
            }
            try {
                Characteristic characteristic = entry.getValue().apply(endpoint);
                addIfNotNull(characteristics, characteristic);
            } catch (Exception e) {
                log.error("Error creating characteristic {} for endpoint {} [{}]: {}",
                        charType, endpoint.getName(), endpoint.getEntityID(), e.getMessage(), e);
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
        return type == MANUFACTURER || type == MODEL || type == SERIAL_NUMBER ||
               type == FIRMWARE_REVISION || type == HARDWARE_REVISION || type == NAME || type == IDENTIFY;
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

    protected static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(
            @Nullable ContextVar.Variable v,
            HomekitEndpointEntity e,
            HomekitCharacteristicType t) {
        if (v == null) return cb -> {
        };
        String k = e.getEntityID() + "_" + t.name() + "_sub";
        return cb -> {
            if (cb == null) return;
            v.addListener(k, s -> cb.changed());
        };
    }

    protected static Runnable getUnsubscriber(@Nullable ContextVar.Variable v, HomekitEndpointEntity e, HomekitCharacteristicType t) {
        if (v == null) return () -> {
        };
        String k = e.getEntityID() + "_" + t.name() + "_sub";
        return () -> v.removeListener(k);
    }

    // Accessory Info
    private static ManufacturerCharacteristic createManufacturerCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getManufacturer());
        return (v == null) ? new ManufacturerCharacteristic(() -> completedFuture("Homio")) : new ManufacturerCharacteristic(getStringSupplier(v, "Homio"));
    }

    private static ModelCharacteristic createModelCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getModel());
        return (v == null) ? new ModelCharacteristic(() -> completedFuture("Virtual")) : new ModelCharacteristic(getStringSupplier(v, "Virtual"));
    }

    private static SerialNumberCharacteristic createSerialNumberCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getSerialNumber());
        return (v == null) ? new SerialNumberCharacteristic(() -> completedFuture(e.getEntityID())) : new SerialNumberCharacteristic(getStringSupplier(v, e.getEntityID()));
    }

    private static FirmwareRevisionCharacteristic createFirmwareRevisionCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getFirmwareRevision());
        return (v == null) ? new FirmwareRevisionCharacteristic(() -> completedFuture("1.0")) : new FirmwareRevisionCharacteristic(getStringSupplier(v, "1.0"));
    }

    private static HardwareRevisionCharacteristic createHardwareRevisionCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getHardwareRevision());
        return (v == null) ? new HardwareRevisionCharacteristic(() -> completedFuture("1.0")) : new HardwareRevisionCharacteristic(getStringSupplier(v, "1.0"));
    }

    private static IdentifyCharacteristic createIdentifyCharacteristic(HomekitEndpointEntity e) {
        return new IdentifyCharacteristic((onOff) -> log.info("Identify called for: {}. Value: {}",
                e.getName(), onOff));
    }

    private static NameCharacteristic createNameCharacteristic(HomekitEndpointEntity e) {
        return new NameCharacteristic(() -> completedFuture(e.getDevice().getTitle()));
    }

    // Common Binary
    private static OnCharacteristic createOnStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getOnState());
        if (v == null) return null;
        return new OnCharacteristic(getBooleanSupplier(v), setBooleanConsumer(v), getSubscriber(v, e, ON_STATE), getUnsubscriber(v, e, ON_STATE));
    }

    private static ActiveCharacteristic createActiveCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getActiveState());
        if (v == null) return null;
        Map<ActiveEnum, Object> m = createMapping(v, ActiveEnum.class);
        return new ActiveCharacteristic(
                () -> getCurrentEnumValue(v, m, ActiveEnum.INACTIVE),
                val -> setHomioVariableFromEnum(v, val, m),
                getSubscriber(v, e, ACTIVE),
                getUnsubscriber(v, e, ACTIVE));
    }

    private static MuteCharacteristic createMuteCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getMute());
        if (v == null) return null;
        return new MuteCharacteristic(getBooleanSupplier(v), setBooleanConsumer(v), getSubscriber(v, e, MUTE), getUnsubscriber(v, e, MUTE));
    }

    private static ObstructionDetectedCharacteristic createObstructionDetectedCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getObstructionDetected());
        if (v == null) return null;
        return new ObstructionDetectedCharacteristic(
                getBooleanSupplier(v),
                getSubscriber(v, e, OBSTRUCTION_STATUS),
                getUnsubscriber(v, e, OBSTRUCTION_STATUS));
    }

    // Sensors
    private static MotionDetectedCharacteristic createMotionDetectedCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getDetectedState());
        if (v == null || e.getAccessoryType() != HomekitAccessoryType.MOTION_SENSOR) return null;
        return new MotionDetectedCharacteristic(
                getBooleanSupplier(v),
                getSubscriber(v, e, MOTION_DETECTED_STATE),
                getUnsubscriber(v, e, MOTION_DETECTED_STATE));
    }

    private static OccupancyDetectedCharacteristic createOccupancyDetectedCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getDetectedState());
        if (v == null || e.getAccessoryType() != HomekitAccessoryType.OCCUPANCY_SENSOR) return null;
        Map<OccupancyDetectedEnum, Object> m = createMapping(v, OccupancyDetectedEnum.class);
        return new OccupancyDetectedCharacteristic(() -> getCurrentEnumValue(v, m, OccupancyDetectedEnum.NOT_DETECTED), getSubscriber(v, e, OCCUPANCY_DETECTED_STATE), getUnsubscriber(v, e, OCCUPANCY_DETECTED_STATE));
    }

    // Lightbulb
    private static BrightnessCharacteristic createBrightnessCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getBrightness());
        if (v == null) return null;
        return new BrightnessCharacteristic(getIntSupplier(v, 100), setIntConsumer(v), getSubscriber(v, e, BRIGHTNESS), getUnsubscriber(v, e, BRIGHTNESS));
    }

    private static HueCharacteristic createHueCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getHue());
        if (v == null) return null;
        Supplier<CompletableFuture<Double>> getter = () -> completedFuture(v.getValue().doubleValue(0));
        ExceptionalConsumer<Double> setter = hue -> v.set(new DecimalType(hue));
        return new HueCharacteristic(getter, setter, getSubscriber(v, e, HUE), getUnsubscriber(v, e, HUE));
    }

    private static SaturationCharacteristic createSaturationCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getSaturation());
        if (v == null) return null;
        Supplier<CompletableFuture<Double>> getter = () -> completedFuture(v.getValue().doubleValue(0));
        ExceptionalConsumer<Double> setter = sat -> v.set(new DecimalType(sat));
        return new SaturationCharacteristic(getter, setter, getSubscriber(v, e, SATURATION), getUnsubscriber(v, e, SATURATION));
    }

    private static ColorTemperatureCharacteristic createColorTemperatureCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getColorTemperature());
        if (v == null) return null;
        int minM = (int) v.getMinValue(COLOR_TEMPERATURE_MIN_MIREDS);
        int maxM = (int) v.getMaxValue(COLOR_TEMPERATURE_MAX_MIREDS);
        boolean inv = e.isColorTemperatureInverted();
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
        return new ColorTemperatureCharacteristic(minM, maxM, getter, setter, getSubscriber(v, e, COLOR_TEMPERATURE), getUnsubscriber(v, e, COLOR_TEMPERATURE));
    }

    // Temperature & Climate
    private static CurrentTemperatureCharacteristic createCurrentTemperatureCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentTemperature());
        if (v == null) return null;
        return new CurrentTemperatureCharacteristic(v.getMinValue(CURRENT_TEMPERATURE_MIN_CELSIUS), v.getMaxValue(100), v.getStep(0.1), getTemperatureSupplier(v, 20.0), getSubscriber(v, e, CURRENT_TEMPERATURE), getUnsubscriber(v, e, CURRENT_TEMPERATURE));
    }

    private static TargetTemperatureCharacteristic createTargetTemperatureCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetTemperature());
        if (v == null) return null;
        return new TargetTemperatureCharacteristic(v.getMinValue(10.0), v.getMaxValue(38.0), v.getStep(0.5), getTemperatureSupplier(v, 21.0), setTemperatureConsumer(v), getSubscriber(v, e, TARGET_TEMPERATURE), getUnsubscriber(v, e, TARGET_TEMPERATURE));
    }

    private static CurrentHeatingCoolingStateCharacteristic createCurrentHeatingCoolingStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentHeatingCoolingState());
        if (v == null) return null;
        List<CurrentHeatingCoolingStateEnum> vals = new ArrayList<>();
        Map<CurrentHeatingCoolingStateEnum, Object> m = createMapping(v, CurrentHeatingCoolingStateEnum.class, vals);
        return new CurrentHeatingCoolingStateCharacteristic(vals.isEmpty() ? null : vals.toArray(new CurrentHeatingCoolingStateEnum[0]), () -> getCurrentEnumValue(v, m, CurrentHeatingCoolingStateEnum.OFF), getSubscriber(v, e, CURRENT_HEATING_COOLING_STATE), getUnsubscriber(v, e, CURRENT_HEATING_COOLING_STATE));
    }

    private static TargetHeatingCoolingStateCharacteristic createTargetHeatingCoolingStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetHeatingCoolingState());
        if (v == null) return null;
        List<TargetHeatingCoolingStateEnum> vals = new ArrayList<>();
        Map<TargetHeatingCoolingStateEnum, Object> m = createMapping(v, TargetHeatingCoolingStateEnum.class, vals);
        return new TargetHeatingCoolingStateCharacteristic(vals.isEmpty() ? null : vals.toArray(new TargetHeatingCoolingStateEnum[0]), () -> getCurrentEnumValue(v, m, TargetHeatingCoolingStateEnum.OFF), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, TARGET_HEATING_COOLING_STATE), getUnsubscriber(v, e, TARGET_HEATING_COOLING_STATE));
    }

    private static CoolingThresholdTemperatureCharacteristic createCoolingThresholdTemperatureCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCoolingThresholdTemperature());
        if (v == null) return null;
        return new CoolingThresholdTemperatureCharacteristic(v.getMinValue(10.0), v.getMaxValue(35.0), v.getStep(0.5), getTemperatureSupplier(v, 25.0), setTemperatureConsumer(v), getSubscriber(v, e, COOLING_THRESHOLD_TEMPERATURE), getUnsubscriber(v, e, COOLING_THRESHOLD_TEMPERATURE));
    }

    private static HeatingThresholdTemperatureCharacteristic createHeatingThresholdTemperatureCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getHeatingThresholdTemperature());
        if (v == null) return null;
        return new HeatingThresholdTemperatureCharacteristic(v.getMinValue(0.0), v.getMaxValue(25.0), v.getStep(0.5), getTemperatureSupplier(v, 18.0), setTemperatureConsumer(v), getSubscriber(v, e, HEATING_THRESHOLD_TEMPERATURE), getUnsubscriber(v, e, HEATING_THRESHOLD_TEMPERATURE));
    }

    private static TargetRelativeHumidityCharacteristic createTargetRelativeHumidityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetRelativeHumidity());
        if (v == null) return null;
        return new TargetRelativeHumidityCharacteristic(getDoubleSupplier(v, 45.0F), setDoubleConsumer(v), getSubscriber(v, e, TARGET_RELATIVE_HUMIDITY), getUnsubscriber(v, e, TARGET_RELATIVE_HUMIDITY));
    }

    // Lock
    private static LockCurrentStateCharacteristic createLockCurrentStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getLockCurrentState());
        if (v == null) return null;
        Map<LockCurrentStateEnum, Object> m = createMapping(v, LockCurrentStateEnum.class);
        return new LockCurrentStateCharacteristic(() -> getCurrentEnumValue(v, m, LockCurrentStateEnum.UNKNOWN), getSubscriber(v, e, LOCK_CURRENT_STATE), getUnsubscriber(v, e, LOCK_CURRENT_STATE));
    }

    private static LockTargetStateCharacteristic createLockTargetStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getLockTargetState());
        if (v == null) return null;
        Map<LockTargetStateEnum, Object> m = createMapping(v, LockTargetStateEnum.class);
        return new LockTargetStateCharacteristic(() -> getCurrentEnumValue(v, m, LockTargetStateEnum.UNSECURED), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, LOCK_TARGET_STATE), getUnsubscriber(v, e, LOCK_TARGET_STATE));
    }

    // Window Covering & Slats
    private static HoldPositionCharacteristic createHoldPositionCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getWindowHoldPosition());
        if (v == null) return null;
        return new HoldPositionCharacteristic(value -> {
            if (!value) {
                return;
            }
            v.set(OnOffType.ON);
        });
    }

    private static CurrentHorizontalTiltAngleCharacteristic createCurrentHorizontalTiltAngleCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentHorizontalTiltAngle());
        if (v == null) return null;
        return new CurrentHorizontalTiltAngleCharacteristic(getIntSupplier(v, 0), getSubscriber(v, e, CURRENT_HORIZONTAL_TILT_ANGLE), getUnsubscriber(v, e, CURRENT_HORIZONTAL_TILT_ANGLE));
    }

    private static TargetHorizontalTiltAngleCharacteristic createTargetHorizontalTiltAngleCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetHorizontalTiltAngle());
        if (v == null) return null;
        return new TargetHorizontalTiltAngleCharacteristic(getIntSupplier(v, 0), setIntConsumer(v), getSubscriber(v, e, TARGET_HORIZONTAL_TILT_ANGLE), getUnsubscriber(v, e, TARGET_HORIZONTAL_TILT_ANGLE));
    }

    private static CurrentVerticalTiltAngleCharacteristic createCurrentVerticalTiltAngleCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentVerticalTiltAngle());
        if (v == null) return null;
        return new CurrentVerticalTiltAngleCharacteristic(getIntSupplier(v, 0), getSubscriber(v, e, CURRENT_VERTICAL_TILT_ANGLE), getUnsubscriber(v, e, CURRENT_VERTICAL_TILT_ANGLE));
    }

    private static TargetVerticalTiltAngleCharacteristic createTargetVerticalTiltAngleCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetVerticalTiltAngle());
        if (v == null) return null;
        return new TargetVerticalTiltAngleCharacteristic(getIntSupplier(v, 0), setIntConsumer(v), getSubscriber(v, e, TARGET_VERTICAL_TILT_ANGLE), getUnsubscriber(v, e, TARGET_VERTICAL_TILT_ANGLE));
    }

    private static CurrentTiltAngleCharacteristic createCurrentTiltAngleCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentTiltAngle());
        if (v == null) return null;
        return new CurrentTiltAngleCharacteristic(getIntSupplier(v, 0), getSubscriber(v, e, CURRENT_TILT_ANGLE), getUnsubscriber(v, e, CURRENT_TILT_ANGLE));
    }

    private static TargetTiltAngleCharacteristic createTargetTiltAngleCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetTiltAngle());
        if (v == null) return null;
        return new TargetTiltAngleCharacteristic(getIntSupplier(v, 0), setIntConsumer(v), getSubscriber(v, e, TARGET_TILT_ANGLE), getUnsubscriber(v, e, TARGET_TILT_ANGLE));
    }

    // Garage Door
    private static CurrentDoorStateCharacteristic createCurrentDoorStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentDoorState());
        if (v == null || e.getAccessoryType() != HomekitAccessoryType.GARAGE_DOOR_OPENER) return null;
        Map<CurrentDoorStateEnum, Object> m = createMapping(v, CurrentDoorStateEnum.class, true);
        return new CurrentDoorStateCharacteristic(() -> getCurrentEnumValue(v, m, CurrentDoorStateEnum.CLOSED), getSubscriber(v, e, CURRENT_DOOR_STATE), getUnsubscriber(v, e, CURRENT_DOOR_STATE));
    }

    private static TargetDoorStateCharacteristic createTargetDoorStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetDoorState());
        if (v == null || e.getAccessoryType() != HomekitAccessoryType.GARAGE_DOOR_OPENER) return null;
        Map<TargetDoorStateEnum, Object> m = createMapping(v, TargetDoorStateEnum.class, true);
        return new TargetDoorStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetDoorStateEnum.CLOSED), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, TARGET_DOOR_STATE), getUnsubscriber(v, e, TARGET_DOOR_STATE));
    }

    // Fan
    private static CurrentFanStateCharacteristic createCurrentFanStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentFanState());
        if (v == null) return null;
        Map<CurrentFanStateEnum, Object> m = createMapping(v, CurrentFanStateEnum.class);
        return new CurrentFanStateCharacteristic(() -> getCurrentEnumValue(v, m, CurrentFanStateEnum.INACTIVE), getSubscriber(v, e, CURRENT_FAN_STATE), getUnsubscriber(v, e, CURRENT_FAN_STATE));
    }

    private static TargetFanStateCharacteristic createTargetFanStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetFanState());
        if (v == null) return null;
        Map<TargetFanStateEnum, Object> m = createMapping(v, TargetFanStateEnum.class);
        return new TargetFanStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetFanStateEnum.MANUAL), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, TARGET_FAN_STATE), getUnsubscriber(v, e, TARGET_FAN_STATE));
    }

    private static RotationDirectionCharacteristic createRotationDirectionCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getRotationDirection());
        if (v == null) return null;
        Map<RotationDirectionEnum, Object> m = createMapping(v, RotationDirectionEnum.class);
        return new RotationDirectionCharacteristic(() -> getCurrentEnumValue(v, m, RotationDirectionEnum.CLOCKWISE), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, ROTATION_DIRECTION), getUnsubscriber(v, e, ROTATION_DIRECTION));
    }

    private static RotationSpeedCharacteristic createRotationSpeedCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getRotationSpeed());
        if (v == null) return null;
        return new RotationSpeedCharacteristic(getDoubleSupplier(v, 0), setDoubleConsumer(v), getSubscriber(v, e, ROTATION_SPEED), getUnsubscriber(v, e, ROTATION_SPEED));
    }

    private static SwingModeCharacteristic createSwingModeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getFanSwingMode());
        if (v == null) return null;
        Map<SwingModeEnum, Object> m = createMapping(v, SwingModeEnum.class);
        return new SwingModeCharacteristic(() -> getCurrentEnumValue(v, m, SwingModeEnum.SWING_DISABLED), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, SWING_MODE), getUnsubscriber(v, e, SWING_MODE));
    }

    // Air Quality Sensor
    private static AirQualityCharacteristic createAirQualityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getAirQuality());
        if (v == null) return null;
        Map<AirQualityEnum, Object> m = createMapping(v, AirQualityEnum.class);
        return new AirQualityCharacteristic(() -> getCurrentEnumValue(v, m, AirQualityEnum.UNKNOWN), getSubscriber(v, e, AIR_QUALITY), getUnsubscriber(v, e, AIR_QUALITY));
    }

    private static OzoneDensityCharacteristic createOzoneDensityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getOzoneDensity());
        if (v == null) return null;
        return new OzoneDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, OZONE_DENSITY), getUnsubscriber(v, e, OZONE_DENSITY));
    }

    private static NitrogenDioxideDensityCharacteristic createNitrogenDioxideDensityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getNitrogenDioxideDensity());
        if (v == null) return null;
        return new NitrogenDioxideDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, NITROGEN_DIOXIDE_DENSITY), getUnsubscriber(v, e, NITROGEN_DIOXIDE_DENSITY));
    }

    private static SulphurDioxideDensityCharacteristic createSulphurDioxideDensityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getSulphurDioxideDensity());
        if (v == null) return null;
        return new SulphurDioxideDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, SULPHUR_DIOXIDE_DENSITY), getUnsubscriber(v, e, SULPHUR_DIOXIDE_DENSITY));
    }

    private static PM25DensityCharacteristic createPM25DensityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getPm25Density());
        if (v == null) return null;
        return new PM25DensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, PM25_DENSITY), getUnsubscriber(v, e, PM25_DENSITY));
    }

    private static PM10DensityCharacteristic createPM10DensityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getPm10Density());
        if (v == null) return null;
        return new PM10DensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, PM10_DENSITY), getUnsubscriber(v, e, PM10_DENSITY));
    }

    private static VOCDensityCharacteristic createVOCDensityCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getVocDensity());
        if (v == null) return null;
        return new VOCDensityCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, VOC_DENSITY), getUnsubscriber(v, e, VOC_DENSITY));
    }

    // CO2/CO Sensors
    private static CarbonDioxideLevelCharacteristic createCarbonDioxideLevelCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCarbonDioxideLevel());
        if (v == null) return null;
        return new CarbonDioxideLevelCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, CARBON_DIOXIDE_LEVEL), getUnsubscriber(v, e, CARBON_DIOXIDE_LEVEL));
    }

    private static CarbonDioxidePeakLevelCharacteristic createCarbonDioxidePeakLevelCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCarbonDioxidePeakLevel());
        if (v == null) return null;
        return new CarbonDioxidePeakLevelCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, CARBON_DIOXIDE_PEAK_LEVEL), getUnsubscriber(v, e, CARBON_DIOXIDE_PEAK_LEVEL));
    }

    private static CarbonMonoxideLevelCharacteristic createCarbonMonoxideLevelCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCarbonMonoxideLevel());
        if (v == null) return null;
        return new CarbonMonoxideLevelCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, CARBON_MONOXIDE_LEVEL), getUnsubscriber(v, e, CARBON_MONOXIDE_LEVEL));
    }

    private static CarbonMonoxidePeakLevelCharacteristic createCarbonMonoxidePeakLevelCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCarbonMonoxidePeakLevel());
        if (v == null) return null;
        return new CarbonMonoxidePeakLevelCharacteristic(getDoubleSupplier(v, 0), getSubscriber(v, e, CARBON_MONOXIDE_PEAK_LEVEL), getUnsubscriber(v, e, CARBON_MONOXIDE_PEAK_LEVEL));
    }

    private static FilterLifeLevelCharacteristic createFilterLifeLevelCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getFilterLifeLevel());
        if (v == null) return null;
        return new FilterLifeLevelCharacteristic(getDoubleSupplier(v, 100), getSubscriber(v, e, FILTER_LIFE_LEVEL), getUnsubscriber(v, e, FILTER_LIFE_LEVEL));
    }

    private static ResetFilterIndicationCharacteristic createFilterResetIndicationCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getFilterResetIndication());
        if (v == null) return null;
        return new ResetFilterIndicationCharacteristic(val -> v.set(OnOffType.ON));
    }

    private static RemainingDurationCharacteristic createRemainingDurationCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getRemainingDuration());
        if (v == null) return null;
        return new RemainingDurationCharacteristic(getIntSupplier(v, 0), getSubscriber(v, e, REMAINING_DURATION), getUnsubscriber(v, e, REMAINING_DURATION));
    }

    // Smart Speaker / Speaker / Television Speaker
    private static VolumeCharacteristic createVolumeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getVolume());
        if (v == null) return null;
        return new VolumeCharacteristic(getIntSupplier(v, 50), setIntConsumer(v), getSubscriber(v, e, VOLUME), getUnsubscriber(v, e, VOLUME));
    }

    private static CurrentMediaStateCharacteristic createCurrentMediaStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentMediaState());
        if (v == null) return null;
        Map<CurrentMediaStateEnum, Object> m = createMapping(v, CurrentMediaStateEnum.class);
        return new CurrentMediaStateCharacteristic(() -> getCurrentEnumValue(v, m, CurrentMediaStateEnum.UNKNOWN), getSubscriber(v, e, CURRENT_MEDIA_STATE), getUnsubscriber(v, e, CURRENT_MEDIA_STATE));
    }

    private static TargetMediaStateCharacteristic createTargetMediaStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetMediaState());
        if (v == null) return null;
        Map<TargetMediaStateEnum, Object> m = createMapping(v, TargetMediaStateEnum.class);
        return new TargetMediaStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetMediaStateEnum.STOP), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, TARGET_MEDIA_STATE), getUnsubscriber(v, e, TARGET_MEDIA_STATE));
    }

    private static VolumeControlTypeCharacteristic createVolumeControlTypeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getVolumeControlType());
        if (v == null) return null;
        Map<VolumeControlTypeEnum, Object> m = createMapping(v, VolumeControlTypeEnum.class);
        return new VolumeControlTypeCharacteristic(() -> getCurrentEnumValue(v, m, VolumeControlTypeEnum.NONE), getSubscriber(v, e, VOLUME_CONTROL_TYPE), getUnsubscriber(v, e, VOLUME_CONTROL_TYPE));
    }

    private static VolumeSelectorCharacteristic createVolumeSelectorCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getVolumeSelector());
        if (v == null) return null;
        Map<VolumeSelectorEnum, Object> m = createMapping(v, VolumeSelectorEnum.class);
        return new VolumeSelectorCharacteristic(newVal -> setHomioVariableFromEnum(v, newVal, m));
    }

    // Stateless Programmable Switch / Doorbell
    private static ProgrammableSwitchEventCharacteristic createProgrammableSwitchEventCharacteristic(HomekitEndpointEntity e) {
        /*ContextVar.Variable v = e.getVariable(e.getProgrammableSwitchEvent());
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
    private static ActiveIdentifierCharacteristic createActiveIdentifierCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getActiveIdentifier());
        if (v == null) return null;
        return new ActiveIdentifierCharacteristic(getIntSupplier(v, 1), setIntConsumer(v), getSubscriber(v, e, ACTIVE_IDENTIFIER), getUnsubscriber(v, e, ACTIVE_IDENTIFIER));
    }

    private static ConfiguredNameCharacteristic createConfiguredNameCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getConfiguredName());
        if (v == null) return null;
        return new ConfiguredNameCharacteristic(getStringSupplier(v, "Input"), setStringConsumer(v), getSubscriber(v, e, CONFIGURED_NAME), getUnsubscriber(v, e, CONFIGURED_NAME));
    }

    private static InputDeviceTypeCharacteristic createInputDeviceTypeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getInputDeviceType());
        if (v == null) return null;
        Map<InputDeviceTypeEnum, Object> m = createMapping(v, InputDeviceTypeEnum.class);
        return new InputDeviceTypeCharacteristic(() -> getCurrentEnumValue(v, m, InputDeviceTypeEnum.OTHER), getSubscriber(v, e, INPUT_DEVICE_TYPE), getUnsubscriber(v, e, INPUT_DEVICE_TYPE));
    }

    private static InputSourceTypeCharacteristic createInputSourceTypeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getInputSourceType());
        if (v == null) return null;
        Map<InputSourceTypeEnum, Object> m = createMapping(v, InputSourceTypeEnum.class);
        return new InputSourceTypeCharacteristic(() -> getCurrentEnumValue(v, m, InputSourceTypeEnum.OTHER), getSubscriber(v, e, INPUT_SOURCE_TYPE), getUnsubscriber(v, e, INPUT_SOURCE_TYPE));
    }

    /*private static IdentifierCharacteristic createIdentifierInputSourceCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getIdentifierInputSource());
        if (v == null) return null;
        return new IdentifierCharacteristic(getIntSupplier(v, 1));
    }*/ // This is usually read-only unique ID for an InputSource

    /*private static CurrentVisibilityStateCharacteristic createCurrentVisibilityStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getCurrentVisibilityState());
        if (v == null) return null;
        Map<CurrentVisibilityStateEnum, Object> m = createMapping(e, v, CurrentVisibilityStateEnum.class);
        return new CurrentVisibilityStateCharacteristic(() -> getCurrentEnumValue(e, v, m, CurrentVisibilityStateEnum.SHOWN), getSubscriber(v, e, CURRENT_VISIBILITY_STATE), getUnsubscriber(v, e, CURRENT_VISIBILITY_STATE));
    }*/

    private static TargetVisibilityStateCharacteristic createTargetVisibilityStateCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getTargetVisibilityState());
        if (v == null) return null;
        Map<TargetVisibilityStateEnum, Object> m = createMapping(v, TargetVisibilityStateEnum.class);
        return new TargetVisibilityStateCharacteristic(() -> getCurrentEnumValue(v, m, TargetVisibilityStateEnum.SHOWN), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, TARGET_VISIBILITY_STATE), getUnsubscriber(v, e, TARGET_VISIBILITY_STATE));
    }

    private static SleepDiscoveryModeCharacteristic createSleepDiscoveryModeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getSleepDiscoveryMode());
        if (v == null) return null;
        Map<SleepDiscoveryModeEnum, Object> m = createMapping(v, SleepDiscoveryModeEnum.class);
        return new SleepDiscoveryModeCharacteristic(() -> getCurrentEnumValue(v, m, SleepDiscoveryModeEnum.ALWAYS_DISCOVERABLE), getSubscriber(v, e, SLEEP_DISCOVERY_MODE), getUnsubscriber(v, e, SLEEP_DISCOVERY_MODE));
    }

    private static RemoteKeyCharacteristic createRemoteKeyCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getRemoteKey());
        if (v == null) return null;
        Map<RemoteKeyEnum, Object> m = createMapping(v, RemoteKeyEnum.class);
        return new RemoteKeyCharacteristic(newVal -> setHomioVariableFromEnum(v, newVal, m));
    }

    private static PowerModeCharacteristic createPowerModeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getPowerModeSelection());
        if (v == null) return null;
        Map<PowerModeEnum, Object> m = createMapping(v, PowerModeEnum.class);
        return new PowerModeCharacteristic(newVal -> setHomioVariableFromEnum(v, newVal, m));
    }

    private static PictureModeCharacteristic createPictureModeCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getPictureMode());
        if (v == null) return null;
        Map<PictureModeEnum, Object> m = createMapping(v, PictureModeEnum.class);
        return new PictureModeCharacteristic(() -> getCurrentEnumValue(v, m, PictureModeEnum.OTHER), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, PICTURE_MODE), getUnsubscriber(v, e, PICTURE_MODE));
    }

    private static ClosedCaptionsCharacteristic createClosedCaptionsCharacteristic(HomekitEndpointEntity e) {
        ContextVar.Variable v = e.getVariable(e.getClosedCaptions());
        if (v == null) return null;
        Map<ClosedCaptionsEnum, Object> m = createMapping(v, ClosedCaptionsEnum.class);
        return new ClosedCaptionsCharacteristic(() -> getCurrentEnumValue(v, m, ClosedCaptionsEnum.DISABLED), newVal -> setHomioVariableFromEnum(v, newVal, m), getSubscriber(v, e, CLOSED_CAPTIONS), getUnsubscriber(v, e, CLOSED_CAPTIONS));
    }

}