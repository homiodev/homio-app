package org.homio.addon.homekit;

import io.github.hapjava.characteristics.Characteristic;
import io.github.hapjava.characteristics.CharacteristicEnum;
import io.github.hapjava.characteristics.ExceptionalConsumer;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.accessoryinformation.*;
import io.github.hapjava.characteristics.impl.airquality.OzoneDensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.PM10DensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.PM25DensityCharacteristic;
import io.github.hapjava.characteristics.impl.airquality.VOCDensityCharacteristic;
import io.github.hapjava.characteristics.impl.audio.MuteCharacteristic;
import io.github.hapjava.characteristics.impl.audio.VolumeCharacteristic;
import io.github.hapjava.characteristics.impl.battery.StatusLowBatteryCharacteristic;
import io.github.hapjava.characteristics.impl.battery.StatusLowBatteryEnum;
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
import io.github.hapjava.characteristics.impl.humiditysensor.CurrentRelativeHumidityCharacteristic;
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
import io.github.hapjava.characteristics.impl.slat.CurrentTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.slat.TargetTiltAngleCharacteristic;
import io.github.hapjava.characteristics.impl.television.*;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeControlTypeCharacteristic;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeControlTypeEnum;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeSelectorCharacteristic;
import io.github.hapjava.characteristics.impl.televisionspeaker.VolumeSelectorEnum;
import io.github.hapjava.characteristics.impl.thermostat.*;
import io.github.hapjava.characteristics.impl.valve.RemainingDurationCharacteristic;
import io.github.hapjava.characteristics.impl.valve.SetDurationCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.*;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.homio.addon.homekit.enums.HomekitCharacteristicType.*;

/**
 * Creates an optional characteristics .
 *
 * @author Eugen Freiter - Initial contribution
 */
@Log4j2
public class HomekitCharacteristicFactory {
    // These values represent ranges that do not match the defaults that are part of
    // the HAP specification/the defaults in HAP-Java, but nonetheless are commonly
    // encountered in consumer-grade devices. So we define our own default min/max so
    // that users don't have to override the default unnecessarily.

    // HAP default is 50-400 mired/2500-20,000 K. These numbers represent
    // the warmest and coolest bulbs I could reasonably find at general
    // purpose retailers.
    public static final int COLOR_TEMPERATURE_MIN_MIREDS = 107; // 9300 K
    public static final int COLOR_TEMPERATURE_MAX_MIREDS = 556; // 1800 K
    // HAP default is 0 °C, but it's very common for outdoor temperatures and/or
    // refrigation devices to go below freezing.
    // Lowest recorded temperature on Earth is -89.2 °C. This is just a nice round number.
    public static final int CURRENT_TEMPERATURE_MIN_CELSIUS = -100;
    // HAP default is 0.0001 lx, but this is commonly rounded to 0 by many devices
    public static final int CURRENT_AMBIENT_LIGHT_LEVEL_MIN_LUX = 0;
    // HAP default is 100k
    // https://en.wikipedia.org/wiki/Daylight#Intensity_in_different_conditions
    public static final int CURRENT_AMBIENT_LIGHT_LEVEL_MAX_LUX = 120000;

    // List of optional characteristics and corresponding method o create them.
    private static final Map<HomekitCharacteristicType, Function<HomekitEndpointEntity, Characteristic>> OPTIONAL = new HashMap<>() {
        {
            //   put(ACTIVE, HomekitCharacteristicFactory::createActiveCharacteristic);
            // put(ACTIVE_IDENTIFIER, HomekitCharacteristicFactory::createActiveIdentifierCharacteristic);
            put(ACTIVE_STATUS, HomekitCharacteristicFactory::createStatusActiveCharacteristic);
            put(BATTERY_LOW_STATUS, HomekitCharacteristicFactory::createStatusLowBatteryCharacteristic);
            put(BRIGHTNESS, HomekitCharacteristicFactory::createBrightnessCharacteristic);
            put(CARBON_DIOXIDE_LEVEL, HomekitCharacteristicFactory::createCarbonDioxideLevelCharacteristic);
            put(CARBON_DIOXIDE_PEAK_LEVEL, HomekitCharacteristicFactory::createCarbonDioxidePeakLevelCharacteristic);
            put(CARBON_MONOXIDE_LEVEL, HomekitCharacteristicFactory::createCarbonMonoxideLevelCharacteristic);
            put(CARBON_MONOXIDE_PEAK_LEVEL, HomekitCharacteristicFactory::createCarbonMonoxidePeakLevelCharacteristic);
            put(CLOSED_CAPTIONS, HomekitCharacteristicFactory::createClosedCaptionsCharacteristic);
            put(COLOR_TEMPERATURE, HomekitCharacteristicFactory::createColorTemperatureCharacteristic);
            put(CONFIGURED, HomekitCharacteristicFactory::createIsConfiguredCharacteristic);
            //put(CONFIGURED_NAME, HomekitCharacteristicFactory::createConfiguredNameCharacteristic);
            put(COOLING_THRESHOLD_TEMPERATURE, HomekitCharacteristicFactory::createCoolingThresholdCharacteristic);
            put(CURRENT_DOOR_STATE, HomekitCharacteristicFactory::createCurrentDoorStateCharacteristic);
            put(CURRENT_HEATING_COOLING_STATE,
                    HomekitCharacteristicFactory::createCurrentHeatingCoolingStateCharacteristic);
            put(CURRENT_FAN_STATE, HomekitCharacteristicFactory::createCurrentFanStateCharacteristic);
            put(CURRENT_HORIZONTAL_TILT_ANGLE,
                    HomekitCharacteristicFactory::createCurrentHorizontalTiltAngleCharacteristic);
            put(CURRENT_MEDIA_STATE, HomekitCharacteristicFactory::createCurrentMediaStateCharacteristic);
            put(CURRENT_TILT_ANGLE, HomekitCharacteristicFactory::createCurrentTiltAngleCharacteristic);
            put(CURRENT_VERTICAL_TILT_ANGLE,
                    HomekitCharacteristicFactory::createCurrentVerticalTiltAngleCharacteristic);
            put(CURRENT_VISIBILITY, HomekitCharacteristicFactory::createCurrentVisibilityStateCharacteristic);
            put(CURRENT_TEMPERATURE, HomekitCharacteristicFactory::createCurrentTemperatureCharacteristic);
            put(DURATION, HomekitCharacteristicFactory::createDurationCharacteristic);
            put(FAULT_STATUS, HomekitCharacteristicFactory::createStatusFaultCharacteristic);
            put(FIRMWARE_REVISION, HomekitCharacteristicFactory::createFirmwareRevisionCharacteristic);
            put(FILTER_LIFE_LEVEL, HomekitCharacteristicFactory::createFilterLifeLevelCharacteristic);
            put(FILTER_RESET_INDICATION, HomekitCharacteristicFactory::createFilterResetCharacteristic);
            put(HARDWARE_REVISION, HomekitCharacteristicFactory::createHardwareRevisionCharacteristic);
            put(HEATING_THRESHOLD_TEMPERATURE, HomekitCharacteristicFactory::createHeatingThresholdCharacteristic);
            put(WINDOW_HOLD_POSITION, HomekitCharacteristicFactory::createWindowHoldPositionCharacteristic);
            put(HUE, HomekitCharacteristicFactory::createHueCharacteristic);
            put(IDENTIFIER, HomekitCharacteristicFactory::createIdentifierCharacteristic);
            // put(IDENTIFY, HomekitCharacteristicFactory::createIdentifyCharacteristic);
            put(INPUT_DEVICE_TYPE, HomekitCharacteristicFactory::createInputDeviceTypeCharacteristic);
            put(INPUT_SOURCE_TYPE, HomekitCharacteristicFactory::createInputSourceTypeCharacteristic);
            put(LOCK_CONTROL, HomekitCharacteristicFactory::createLockPhysicalControlsCharacteristic);
            put(LOCK_CURRENT_STATE, HomekitCharacteristicFactory::createLockCurrentStateCharacteristic);
            put(LOCK_TARGET_STATE, HomekitCharacteristicFactory::createLockTargetStateCharacteristic);
            put(MANUFACTURER, HomekitCharacteristicFactory::createManufacturerCharacteristic);
            put(MODEL, HomekitCharacteristicFactory::createModelCharacteristic);
            put(MUTE, HomekitCharacteristicFactory::createMuteCharacteristic);
            put(NAME, HomekitCharacteristicFactory::createNameCharacteristic);
            // put(NITROGEN_DIOXIDE_DENSITY, HomekitCharacteristicFactory::createNitrogenDioxideDensityCharacteristic);
            put(OBSTRUCTION_STATUS, HomekitCharacteristicFactory::createObstructionDetectedCharacteristic);
            put(OZONE_DENSITY, HomekitCharacteristicFactory::createOzoneDensityCharacteristic);
            put(PICTURE_MODE, HomekitCharacteristicFactory::createPictureModeCharacteristic);
            put(PM10_DENSITY, HomekitCharacteristicFactory::createPM10DensityCharacteristic);
            put(PM25_DENSITY, HomekitCharacteristicFactory::createPM25DensityCharacteristic);
            put(POWER_MODE, HomekitCharacteristicFactory::createPowerModeCharacteristic);
            put(PROGRAMMABLE_SWITCH_EVENT, HomekitCharacteristicFactory::createProgrammableSwitchEventCharacteristic);
            put(REMAINING_DURATION, HomekitCharacteristicFactory::createRemainingDurationCharacteristic);
            put(REMOTE_KEY, HomekitCharacteristicFactory::createRemoteKeyCharacteristic);
            put(RELATIVE_HUMIDITY, HomekitCharacteristicFactory::createRelativeHumidityCharacteristic);
            put(ROTATION_DIRECTION, HomekitCharacteristicFactory::createRotationDirectionCharacteristic);
            put(ROTATION_SPEED, HomekitCharacteristicFactory::createRotationSpeedCharacteristic);
            put(SATURATION, HomekitCharacteristicFactory::createSaturationCharacteristic);
            put(SERIAL_NUMBER, HomekitCharacteristicFactory::createSerialNumberCharacteristic);
            put(SLEEP_DISCOVERY_MODE, HomekitCharacteristicFactory::createSleepDiscoveryModeCharacteristic);
            // put(SULPHUR_DIOXIDE_DENSITY, HomekitCharacteristicFactory::createSulphurDioxideDensityCharacteristic);
            put(SWING_MODE, HomekitCharacteristicFactory::createSwingModeCharacteristic);
            put(TAMPERED_STATUS, HomekitCharacteristicFactory::createStatusTamperedCharacteristic);
            put(TARGET_DOOR_STATE, HomekitCharacteristicFactory::createTargetDoorStateCharacteristic);
            put(TARGET_FAN_STATE, HomekitCharacteristicFactory::createTargetFanStateCharacteristic);
            put(TARGET_HEATING_COOLING_STATE,
                    HomekitCharacteristicFactory::createTargetHeatingCoolingStateCharacteristic);
            put(TARGET_HORIZONTAL_TILT_ANGLE,
                    HomekitCharacteristicFactory::createTargetHorizontalTiltAngleCharacteristic);
            put(TARGET_MEDIA_STATE, HomekitCharacteristicFactory::createTargetMediaStateCharacteristic);
            put(TARGET_RELATIVE_HUMIDITY, HomekitCharacteristicFactory::createTargetRelativeHumidityCharacteristic);
            put(TARGET_TEMPERATURE, HomekitCharacteristicFactory::createTargetTemperatureCharacteristic);
            put(TARGET_TILT_ANGLE, HomekitCharacteristicFactory::createTargetTiltAngleCharacteristic);
            put(TARGET_VERTICAL_TILT_ANGLE, HomekitCharacteristicFactory::createTargetVerticalTiltAngleCharacteristic);
            put(TARGET_VISIBILITY_STATE, HomekitCharacteristicFactory::createTargetVisibilityStateCharacteristic);
            put(TEMPERATURE_UNIT, HomekitCharacteristicFactory::createTemperatureDisplayUnitCharacteristic);
            put(VOC_DENSITY, HomekitCharacteristicFactory::createVOCDensityCharacteristic);
            put(VOLUME, HomekitCharacteristicFactory::createVolumeCharacteristic);
            put(VOLUME_CONTROL_TYPE, HomekitCharacteristicFactory::createVolumeControlTypeCharacteristic);
            put(VOLUME_SELECTOR, HomekitCharacteristicFactory::createVolumeSelectorCharacteristic);
        }
    };

    /**
     * Create an EnumMap for a particular CharacteristicEnum.
     * <p>
     * By default, the map will simply be from the Enum value to the string version of its value.
     * If the item is a Number item, though, the values will the be underlying integer code
     * for the item, as a String.
     * Then the item's metadata will be inspected, applying any custom mappings.
     * Finally, if customEnumList is supplied, it will be filled out with those mappings
     * that are actually referenced in the metadata.
     *
     * @param klazz          The HAP-Java Enum for the characteristic.
     * @param customEnumList Optional output list of which enums are explicitly mentioned.
     * @param inverted       Default-invert the 0/1 values of the HAP enum when linked to a Switch or Contact item.
     *                       This is set by the addon when creating mappings for specific characteristics where the 0 and 1
     *                       values for the enum do not map naturally to 0/OFF/CLOSED and 1/ON/OPEN of openHAB items.
     *                       Note that this is separate from the inverted item-level metadata configuration, which can be
     *                       thought of independently as applying on top of this setting. It essentially "multiplies" out,
     *                       but can also be thought of as simply swapping whichever value OFF/CLOSED and ON/OPEN are
     *                       associated with, which has already been set.
     */
    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            HomekitEndpointEntity item,
            ContextVar.Variable variable,
            Class<T> klazz,
            @Nullable List<T> customEnumList,
            boolean inverted) {
        EnumMap<T, Object> map = new EnumMap<>(klazz);

        // var dataTypes = item.getBaseItem().getAcceptedDataTypes();
        boolean switchType = true; //dataTypes.contains(OnOffType.class);
        boolean contactType = false; //dataTypes.contains(OpenClosedType.class);
        boolean percentType = false; //dataTypes.contains(PercentType.class);
        boolean numberType = false; //dataTypes.contains(DecimalType.class) || percentType || switchType || contactType;

        //if (item.isInverted()) {
        //   inverted = !inverted;
        // }
        String onValue = switchType ? OnOffType.ON.toString() : "OPEN";
        String offValue = switchType ? OnOffType.OFF.toString() : "CLOSED";
        @Nullable
        T offEnumValue = null, onEnumValue = null;

        //var configuration = item.getConfiguration();
        boolean configurationDefinesEnumValues = false;
        /*if (configuration != null && !configuration.isEmpty()) {
            for (var k : klazz.getEnumConstants()) {
                if (configuration.containsKey(k.toString())) {
                    configurationDefinesEnumValues = true;
                    break;
                }
            }
        }*/

        for (var k : klazz.getEnumConstants()) {
            if (numberType) {
                int code = k.getCode();
                if ((switchType || contactType) && code == 0 && !configurationDefinesEnumValues) {
                    map.put(k, inverted ? onValue : offValue);
                    offEnumValue = k;
                } else if ((switchType || contactType) && code == 1 && !configurationDefinesEnumValues) {
                    map.put(k, inverted ? offValue : onValue);
                    onEnumValue = k;
                } else if (percentType && code == 0) {
                    map.put(k, "OFF");
                } else if (percentType && code == 1) {
                    map.put(k, "ON");
                } else {
                    map.put(k, Integer.toString(code));
                }
            } else {
                map.put(k, k.toString());
            }
        }
        /*if (configuration != null && !configuration.isEmpty()) {
            map.forEach((k, current_value) -> {
                Object newValue = configuration.get(k.toString());
                if (newValue instanceof String || newValue instanceof Number || newValue instanceof List) {
                    if (newValue instanceof Number) {
                        newValue = newValue.toString();
                    } else if (newValue instanceof List listValue) {
                        newValue = listValue.stream().map(v -> {
                            // they probably put "NULL" in the YAML in MainUI;
                            // and they meant it as a string to match the UnDefType.NULL
                            if (v == null) {
                                return "NULL";
                            } else {
                                return v.toString();
                            }
                        }).collect(Collectors.toList());
                    }
                    map.put(k, Objects.requireNonNull(newValue));
                    if (customEnumList != null) {
                        customEnumList.add(k);
                    }
                }
            });
        }*/
        if (customEnumList != null && customEnumList.isEmpty()) {
            if (switchType || contactType) {
                // Switches and Contacts automatically filter the valid values to the first two
                customEnumList.add(Objects.requireNonNull(offEnumValue));
                customEnumList.add(Objects.requireNonNull(onEnumValue));
            } else {
                customEnumList.addAll(map.keySet());
            }
        }
        /*log.debug("Created {} mapping for item {} ({}): {}", klazz.getSimpleName(), item.getName(),
                item.getBaseItem().getClass().getSimpleName(), map);*/
        return map;
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            HomekitEndpointEntity item,
            ContextVar.Variable variable,
            Class<T> klazz) {
        return createMapping(item, variable, klazz, null, false);
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            HomekitEndpointEntity item,
            ContextVar.Variable variable,
            Class<T> klazz, @Nullable List<T> customEnumList) {
        return createMapping(item, variable, klazz, customEnumList, false);
    }

    public static <T extends Enum<T> & CharacteristicEnum> Map<T, Object> createMapping(
            HomekitEndpointEntity item,
            ContextVar.Variable variable,
            Class<T> klazz, boolean inverted) {
        return createMapping(item, variable, klazz, null, inverted);
    }

    /**
     * Takes item state as value and retrieves the key for that value from mapping.
     * E.g. used to map StringItem value to HomeKit Enum
     *
     * @param item         item
     * @param mapping      mapping
     * @param defaultValue default value if nothing found in mapping
     * @param <T>          type of the result derived from
     * @return key for the value
     */
    public static <T> T getKeyFromMapping(HomekitEndpointEntity item, State state, Map<T, Object> mapping, T defaultValue) {
        /*log.trace("getKeyFromMapping: characteristic {}, state {}, mapping {}", item.getAccessoryType().getTag(),
                state, mapping);*/

        String value;
        /*if (state instanceof UnDefType) {
            return defaultValue;
        } else */
        if (state instanceof StringType || state instanceof OnOffType) {
            value = state.toString();
        } /*else if (state.getClass().equals(PercentType.class)) {
            // We specifically want PercentType, but _not_ HSBType, so don't use instanceof
            value = state.as(OnOffType.class).toString();
        } */ else if (state.getClass().equals(DecimalType.class)) {
            // We specifically want DecimalType, but _not_ PercentType or HSBType, so don't use instanceof
            value = Integer.toString(((DecimalType) state).intValue());
        } else {
            /*log.warn(
                    "Wrong value type {} ({}) for {} characteristic of the item {}. Expected StringItem, NumberItem, or SwitchItem.",
                    state.toString(), state.getClass().getSimpleName(), item.getAccessoryType().getTag(),
                    item.getName());*/
            return defaultValue;
        }

        return mapping.entrySet().stream().filter(entry -> {
            Object mappingValue = entry.getValue();
            if (mappingValue instanceof String stringValue) {
                return value.equalsIgnoreCase(stringValue);
            } else if (mappingValue instanceof List listValue) {
                return listValue.stream().filter(listEntry -> value.equalsIgnoreCase(listEntry.toString())).findAny()
                        .isPresent();
            } else {
                log.warn("Found unexpected enum value type {}; this is a bug.", mappingValue.getClass());
                return false;
            }
        }).findAny().map(Map.Entry::getKey).orElseGet(() -> {
            /*log.warn(
                    "Wrong value {} for {} characteristic of the item {}. Expected one of following {}. Returning {}.",
                    state.toString(), item.getAccessoryType().getTag(), item.getName(), mapping.values(), defaultValue);*/
            return defaultValue;
        });
    }

    // supporting methods

    public static boolean useFahrenheit() {
        return true;
        /*return Boolean.TRUE.equals(FrameworkUtil.getBundle(HomekitImpl.class).getBundleContext()
                .getServiceReference(Homekit.class.getName()).getProperty("useFahrenheitTemperature"));*/
    }

    public static TemperatureDisplayUnitCharacteristic createSystemTemperatureDisplayUnitCharacteristic() {
        return new TemperatureDisplayUnitCharacteristic(() -> CompletableFuture
                .completedFuture(HomekitCharacteristicFactory.useFahrenheit() ? TemperatureDisplayUnitEnum.FAHRENHEIT
                        : TemperatureDisplayUnitEnum.CELSIUS),
                (value) -> {
                }, (cb) -> {
        }, () -> {
        });
    }

    /*public static Unit<Temperature> getSystemTemperatureUnit() {
        return useFahrenheit() ? ImperialUnits.FAHRENHEIT : SIUnits.CELSIUS;
    }*/

    private static <T extends CharacteristicEnum> CompletableFuture<T> getEnumFromItem(HomekitEndpointEntity item,
                                                                                       ContextVar.Variable variable,
                                                                                       Map<T, Object> mapping, T defaultValue) {
        return CompletableFuture
                .completedFuture(getKeyFromMapping(item, variable.getValue(), mapping, defaultValue));
    }

    public static <T extends Enum<T>> void setValueFromEnum(HomekitEndpointEntity endpoint, T value, Map<T, Object> map) {
        Object mapValue = map.get(value);
        // if the mapping has multiple values for this enum, just use the first one for the command sent to the item
        if (mapValue instanceof List listValue) {
            if (listValue.isEmpty()) {
                mapValue = null;
            } else {
                mapValue = listValue.get(0);
            }
        }
        if (mapValue == null) {
            // log.warn("Unable to find mapping value for {} for item {}", value, endpoint.getName());
            return;
        }
        // endpoint.getVariable().set(mapValue.toString());
    }

    private static int getIntFromItem(HomekitEndpointEntity endpoint, int defaultValue) {
        int value = defaultValue;
       /* State state = endpoint.getVariable().getValue();
        if (state instanceof DecimalType stateAsDecimalType) {
            value = stateAsDecimalType.intValue();
        } else {
            log.warn(
                    "Item state {} is not supported for {}. Only PercentType and DecimalType (0/100) are supported.",
                    state, endpoint.getName());
        }*/
        return value;
    }

    /*private static <T extends Quantity<T>> double convertAndRound(double value, Unit<T> from, Unit<T> to) {
        double rawValue = from.equals(to) ? value : from.getConverterTo(to).convert(value);
        return new BigDecimal(rawValue).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }*/

    public static @Nullable Double stateAsTemperature(@Nullable State state) {
        if (state == null) {
            return null;
        }

        /*if (state instanceof QuantityType<?> qt) {
            if (qt.getDimension().equals(SIUnits.CELSIUS.getDimension())) {
                return qt.toUnit(SIUnits.CELSIUS).doubleValue();
            }
        }*/

        return convertToCelsius(state.as(DecimalType.class).doubleValue());
    }

    public static double convertToCelsius(double degrees) {
        return new BigDecimal(degrees).setScale(1, RoundingMode.HALF_UP).doubleValue();
        //return convertAndRound(degrees, getSystemTemperatureUnit(), SIUnits.CELSIUS);
    }

    public static double convertFromCelsius(double degrees) {
        return new BigDecimal(degrees).setScale(1, RoundingMode.HALF_UP).doubleValue();
        //return convertAndRound(degrees, SIUnits.CELSIUS, getSystemTemperatureUnit());
    }

    private static Supplier<CompletableFuture<Integer>> getAngleSupplier(ContextVar.Variable variable, int defaultValue) {
        return () -> CompletableFuture.completedFuture(variable.getValue().intValue(defaultValue));
    }

    private static Supplier<CompletableFuture<Integer>> getIntSupplier(HomekitEndpointEntity endpoint, int defaultValue) {
        return () -> CompletableFuture.completedFuture(getIntFromItem(endpoint, defaultValue));
    }

    private static ExceptionalConsumer<Integer> setIntConsumer(HomekitEndpointEntity endpoint) {
        return (value) -> {
            System.out.println("twet");
            // endpoint.send(new DecimalType(value));
        };
    }

    private static ExceptionalConsumer<Integer> setAngleConsumer(HomekitEndpointEntity endpoint) {
        return (value) -> {
            // endpoint.send(new DecimalType(value));
            /*if (endpoint.getBaseItem() instanceof NumberItem) {
                endpoint.send(new DecimalType(value));
            } else if (endpoint.getBaseItem() instanceof DimmerItem) {
                value = (int) (value * 50.0 / 90.0 + 50.0);
                endpoint.send(new PercentType(value));
            } else {
                log.warn("Item type {} is not supported for {}. Only DimmerItem and NumberItem are supported.",
                        endpoint.getBaseItem().getType(), endpoint.getName());
            }*/
        };
    }

    private static Supplier<CompletableFuture<Double>> getDoubleSupplier(HomekitEndpointEntity endpoint,
                                                                         double defaultValue) {
        return () -> {
            double value = defaultValue;
            try {
                // value = endpoint.getVariable().getValue().floatValue();
            } catch (Exception ex) {
            }
            /*final State state = endpoint.getItem().getState();
            double value = defaultValue;
            if (state instanceof PercentType stateAsPercentType) {
                value = stateAsPercentType.doubleValue();
            } else if (state instanceof DecimalType stateAsDecimalType) {
                value = stateAsDecimalType.doubleValue();
            } else if (state instanceof QuantityType stateAsQuantityType) {
                value = stateAsQuantityType.doubleValue();
            }*/
            return CompletableFuture.completedFuture(value);
        };
    }

    private static ExceptionalConsumer<Double> setDoubleConsumer(HomekitEndpointEntity endpoint) {
        return (value) -> {
            //   endpoint.send(new DecimalType(value));
            /*if (endpoint.getBaseItem() instanceof NumberItem) {
                endpoint.send(new DecimalType(value.doubleValue()));
            } else if (endpoint.getBaseItem() instanceof DimmerItem) {
                endpoint.send(new PercentType(value.intValue()));
            } else {
                log.warn("Item type {} is not supported for {}. Only Number and Dimmer type are supported.",
                        endpoint.getBaseItem().getType(), endpoint.getName());
            }*/
        };
    }

    private static Supplier<CompletableFuture<Double>> getTemperatureSupplier(HomekitEndpointEntity endpoint,
                                                                              double defaultValue) {
        return () -> {
            final @Nullable Double value = null; //  stateAsTemperature(endpoint.getVariable().getValue());
            return CompletableFuture.completedFuture(value != null ? value : defaultValue);
        };
    }

    private static ExceptionalConsumer<Double> setTemperatureConsumer(HomekitEndpointEntity endpoint) {
        return (value) -> {
            // endpoint.send(new DecimalType(value));
            /*Item baseItem = endpoint.getBaseItem();
            if (baseItem instanceof NumberItem baseAsNumberItem) {
                if (baseAsNumberItem.getUnit() != null) {
                    endpoint.send(new QuantityType(value, SIUnits.CELSIUS));
                } else {
                    endpoint.send(new DecimalType(convertFromCelsius(value)));
                }
            } else {
                log.warn("Item type {} is not supported for {}. Only Number type is supported.",
                        endpoint.getBaseItem().getType(), endpoint.getName());
            }*/
        };
    }

    protected static Consumer<HomekitCharacteristicChangeCallback> getSubscriber(ContextVar.Variable variable,
                                                                                 HomekitEndpointEntity endpoint) {
        return (callback) -> {
            variable.setListener(endpoint.getName() + "_sub", state -> callback.changed());
        };
    }

    protected static Runnable getUnsubscriber(ContextVar.Variable variable, HomekitEndpointEntity endpoint) {
        return () -> {
            variable.setListener(endpoint.getName() + "_sub", null);
        };
    }

    // METHODS TO CREATE SINGLE CHARACTERISTIC FROM OPENHAB ITEM
    /*private static ActiveCharacteristic createActiveCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, ActiveEnum.class, false);
        return new ActiveCharacteristic(() -> getEnumFromItem(endpoint, map, ActiveEnum.INACTIVE),
                (value) -> {
                    setValueFromEnum(endpoint, value, map);
                }, new Consumer<HomekitCharacteristicChangeCallback>() {
            @Override
            public void accept(HomekitCharacteristicChangeCallback homekitCharacteristicChangeCallback) {
                getSubscriber(variable, endpoint, ACTIVE);
            }
        },
                getUnsubscriber(variable, endpoint, ACTIVE));
    }*/

    /*private static ActiveIdentifierCharacteristic createActiveIdentifierCharacteristic(HomekitEndpointEntity endpoint) {
        return new ActiveIdentifierCharacteristic(getIntSupplier(endpoint, 1), setIntConsumer(endpoint),
                getSubscriber(variable, endpoint, ACTIVE_IDENTIFIER),
                getUnsubscriber(variable, endpoint, ACTIVE_IDENTIFIER));
    }*/

    private static BrightnessCharacteristic createBrightnessCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getBrightness());
        return new BrightnessCharacteristic(() -> {
            int value = variable.getValue().intValue();// endpoint.getVariable().getValue().intValue();
            /*if (state instanceof HSBType stateAsHSBType) {
                value = stateAsHSBType.getBrightness().intValue();
            } else if (state instanceof PercentType stateAsPercentType) {
                value = stateAsPercentType.intValue();
            }*/
            return CompletableFuture.completedFuture(value);
        }, (brightness) -> {
            variable.set(brightness);
            //endpoint.send(new DecimalType(brightness));
            /*if (endpoint.getBaseItem() instanceof DimmerItem) {
                endpoint.sendCommandProxy(HomekitCommandType.BRIGHTNESS_COMMAND, new PercentType(brightness));
            } else {
                log.warn("Item type {} is not supported for {}. Only ColorItem and DimmerItem are supported.",
                        endpoint.getBaseItem().getType(), endpoint.getName());
            }*/
        }, getSubscriber(variable, endpoint), getUnsubscriber(variable, endpoint));
    }

    private static CarbonDioxideLevelCharacteristic createCarbonDioxideLevelCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCarbonDioxideLevel());
        return new CarbonDioxideLevelCharacteristic(
                getDoubleSupplier(endpoint,
                        variable.getMinValue(CarbonDioxideLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static CarbonDioxidePeakLevelCharacteristic createCarbonDioxidePeakLevelCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCarbonDioxidePeakLevel());
        return new CarbonDioxidePeakLevelCharacteristic(
                getDoubleSupplier(endpoint,
                        variable.getMinValue(CarbonDioxidePeakLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static CarbonMonoxideLevelCharacteristic createCarbonMonoxideLevelCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCarbonMonoxideLevel());
        return new CarbonMonoxideLevelCharacteristic(
                getDoubleSupplier(endpoint,
                        variable.getMinValue(CarbonMonoxideLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static CarbonMonoxidePeakLevelCharacteristic createCarbonMonoxidePeakLevelCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCarbonMonoxidePeakLevel());
        return new CarbonMonoxidePeakLevelCharacteristic(
                getDoubleSupplier(endpoint,
                        variable.getMinValue(CarbonMonoxidePeakLevelCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static ClosedCaptionsCharacteristic createClosedCaptionsCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getClosedCaptions());
        var map = createMapping(endpoint, variable, ClosedCaptionsEnum.class);
        return new ClosedCaptionsCharacteristic(() -> {
            return getEnumFromItem(endpoint, variable, map, ClosedCaptionsEnum.DISABLED);
        },
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static ColorTemperatureCharacteristic createColorTemperatureCharacteristic(HomekitEndpointEntity endpoint) {
        final boolean inverted = endpoint.isColorTemperatureInverted();
        var variable = endpoint.getVariable(endpoint.getColorTemperature());

        int minValue = (int) variable.getMinValue(COLOR_TEMPERATURE_MIN_MIREDS);
        int maxValue = (int) variable.getMaxValue(COLOR_TEMPERATURE_MAX_MIREDS);

        // It's common to swap these if you're providing in Kelvin instead of mired
        if (minValue > maxValue) {
            int temp = minValue;
            minValue = maxValue;
            maxValue = temp;
        }

        int finalMinValue = minValue;
        int range = maxValue - minValue;
        boolean isDimmer = variable.isPercentType();

        return new ColorTemperatureCharacteristic(minValue, maxValue, () -> {
            int value = finalMinValue;
            final State rs = variable.getValue();
            var state = (DecimalType) rs;
            if (isDimmer) {
                double percent = state.doubleValue();
                // invert so that 0% == coolest
                if (inverted) {
                    percent = 100.0 - percent;
                }

                // Dimmer
                // scale to the originally configured range
                value = (int) (percent * range / 100) + finalMinValue;
            } else {
                value = state.intValue();
            }
            /*if (state instanceof QuantityType<?> qt) {
                // Number:Temperature
                qt = qt.toInvertibleUnit(Units.MIRED);
                if (qt == null) {
                    log.warn("Item {}'s state '{}' is not convertible to mireds.", endpoint.getName(), state);
                } else {
                    value = qt.intValue();
                }
            } else if (state instanceof PercentType stateAsPercentType) {
                double percent = stateAsPercentType.doubleValue();
                // invert so that 0% == coolest
                if (inverted) {
                    percent = 100.0 - percent;
                }

                // Dimmer
                // scale to the originally configured range
                value = (int) (percent * range / 100) + finalMinValue;
            } else if (state instanceof DecimalType stateAsDecimalType) {
                value = stateAsDecimalType.intValue();
            }*/
            return CompletableFuture.completedFuture(value);
        }, (value) -> {
            if (isDimmer) {
                // scale to a percent
                double percent = (((double) value) - finalMinValue) * 100 / range;
                if (inverted) {
                    percent = 100.0 - percent;
                }
                variable.set(new DecimalType(BigDecimal.valueOf(percent)));
            } else {
                variable.set(new DecimalType(value));
            }
            /*if (endpoint.getBaseItem() instanceof DimmerItem) {
                // scale to a percent
                double percent = (((double) value) - finalMinValue) * 100 / range;
                if (inverted) {
                    percent = 100.0 - percent;
                }
                endpoint.send(new PercentType(BigDecimal.valueOf(percent)));
            } else if (endpoint.getBaseItem() instanceof NumberItem) {
                endpoint.send(new QuantityType(value, Units.MIRED));
            }*/
        }, getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    /*private static ConfiguredNameCharacteristic createConfiguredNameCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(CONFIGURED_NAME);
        return new ConfiguredNameCharacteristic(() -> {
            final State state = variable.getValue();
            return CompletableFuture
                    .completedFuture(*//*state instanceof UnDefType ? endpoint.getName() : *//*state.toString());
        }, (value) -> variable.set(new StringType(value)),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }*/

    private static CoolingThresholdTemperatureCharacteristic createCoolingThresholdCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getThresholdTemperature());
        double minValue = variable.getMinValue(CoolingThresholdTemperatureCharacteristic.DEFAULT_MIN_VALUE);
        double maxValue = variable.getMaxValue(CoolingThresholdTemperatureCharacteristic.DEFAULT_MAX_VALUE);
        double step = variable.getStep(CoolingThresholdTemperatureCharacteristic.DEFAULT_STEP);

        return new CoolingThresholdTemperatureCharacteristic(minValue, maxValue, step,
                getTemperatureSupplier(endpoint, minValue), setTemperatureConsumer(endpoint),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static CurrentDoorStateCharacteristic createCurrentDoorStateCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCurrentDoorState());
        /*if (taggedItem.getBaseItem() instanceof RollershutterItem) {
            return new CurrentDoorStateCharacteristic(() -> {
                if (taggedItem.getItem().getState() instanceof PercentType percentType
                    && percentType.equals(PercentType.HUNDRED)) {
                    return CompletableFuture.completedFuture(CurrentDoorStateEnum.CLOSED);
                }
                return CompletableFuture.completedFuture(CurrentDoorStateEnum.OPEN);
            }, getSubscriber(variable, endpoint),
                    getUnsubscriber(variable, endpoint));
        } else {*/
            List<CurrentDoorStateEnum> validValues = new ArrayList<>();
            var map = createMapping(endpoint, variable, CurrentDoorStateEnum.class, validValues, true);
            return new CurrentDoorStateCharacteristic(
                    () -> getEnumFromItem(endpoint,  variable, map, CurrentDoorStateEnum.CLOSED),
                    getSubscriber(variable, endpoint),
                    getUnsubscriber(variable, endpoint));
        // }
    }

    private static CurrentHeatingCoolingStateCharacteristic createCurrentHeatingCoolingStateCharacteristic(HomekitEndpointEntity endpoint) {
        List<CurrentHeatingCoolingStateEnum> validValues = new ArrayList<>();
        var variable = endpoint.getVariable(endpoint.getCurrentHeatingCoolingState());
        var map = createMapping(endpoint, variable, CurrentHeatingCoolingStateEnum.class, validValues);
        return new CurrentHeatingCoolingStateCharacteristic(validValues.toArray(new CurrentHeatingCoolingStateEnum[0]),
                () -> getEnumFromItem(endpoint, variable, map, CurrentHeatingCoolingStateEnum.OFF),
                getSubscriber(variable, endpoint),
                getUnsubscriber(variable, endpoint));
    }

    private static CurrentFanStateCharacteristic createCurrentFanStateCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getfan);
        var map = createMapping(endpoint, CurrentFanStateEnum.class);
        return new CurrentFanStateCharacteristic(() -> getEnumFromItem(endpoint, map, CurrentFanStateEnum.INACTIVE),
                getSubscriber(variable, endpoint, CURRENT_FAN_STATE),
                getUnsubscriber(variable, endpoint, CURRENT_FAN_STATE));
    }

    private static CurrentHorizontalTiltAngleCharacteristic createCurrentHorizontalTiltAngleCharacteristic(
            HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCurrentHorizontalTiltAngle());
        return new CurrentHorizontalTiltAngleCharacteristic(
                () -> CompletableFuture.completedFuture(variable.getValue().intValue(0)),
                getSubscriber(variable, endpoint, CURRENT_HORIZONTAL_TILT_ANGLE),
                getUnsubscriber(variable, endpoint, CURRENT_HORIZONTAL_TILT_ANGLE));
    }

    private static CurrentMediaStateCharacteristic createCurrentMediaStateCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, CurrentMediaStateEnum.class);
        return new CurrentMediaStateCharacteristic(
                () -> getEnumFromItem(endpoint, map, CurrentMediaStateEnum.UNKNOWN),
                getSubscriber(variable, endpoint, CURRENT_MEDIA_STATE),
                getUnsubscriber(variable, endpoint, CURRENT_MEDIA_STATE));
    }

    private static CurrentTemperatureCharacteristic createCurrentTemperatureCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCurrentTemperature());
        double minValue = variable.getMinValue(CURRENT_TEMPERATURE_MIN_CELSIUS);
        double maxValue = variable.getMinValue(CurrentTemperatureCharacteristic.DEFAULT_MAX_VALUE);
        double step = variable.getMinValue(CurrentTemperatureCharacteristic.DEFAULT_STEP);

        return new CurrentTemperatureCharacteristic(minValue, maxValue, step,
                getTemperatureSupplier(endpoint, minValue), getSubscriber(variable, endpoint, TARGET_TEMPERATURE),
                getUnsubscriber(variable, endpoint, TARGET_TEMPERATURE));
    }

    private static CurrentTiltAngleCharacteristic createCurrentTiltAngleCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCurrentTiltTiltAngle());
        return new CurrentTiltAngleCharacteristic(
                () -> CompletableFuture.completedFuture(variable.getValue().intValue(0)),
                getSubscriber(variable, endpoint, CURRENT_TILT_ANGLE),
                getUnsubscriber(variable, endpoint, CURRENT_TILT_ANGLE));
    }

    private static CurrentVerticalTiltAngleCharacteristic createCurrentVerticalTiltAngleCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getCurrentVerticalTiltAngle());
        return new CurrentVerticalTiltAngleCharacteristic(
                () -> CompletableFuture.completedFuture(variable.getValue().intValue(0)),
                getSubscriber(variable, endpoint, CURRENT_VERTICAL_TILT_ANGLE),
                getUnsubscriber(variable, endpoint, CURRENT_VERTICAL_TILT_ANGLE));
    }

    private static CurrentVisibilityStateCharacteristic createCurrentVisibilityStateCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, CurrentVisibilityStateEnum.class, true);
        return new CurrentVisibilityStateCharacteristic(
                () -> getEnumFromItem(endpoint, map, CurrentVisibilityStateEnum.HIDDEN),
                getSubscriber(variable, endpoint, CURRENT_VISIBILITY),
                getUnsubscriber(variable, endpoint, CURRENT_VISIBILITY));
    }

    private static SetDurationCharacteristic createDurationCharacteristic(HomekitEndpointEntity endpoint) {
        // var variable = endpoint.getVariable(DURATION);
        return new SetDurationCharacteristic(() -> {
            int value = getIntFromItem(endpoint, 0);
            // final @Nullable Map<String, Object> itemConfiguration = endpoint.getConfiguration();
            /*if ((value == 0) && (itemConfiguration != null)) { // check for default duration
                final Object duration = itemConfiguration.get(HomekitValveImpl.CONFIG_DEFAULT_DURATION);
                if (duration instanceof BigDecimal durationAsBigDecimal) {
                    value = durationAsBigDecimal.intValue();
                    if (endpoint.getItem() instanceof NumberItem taggedNumberItem) {
                        taggedNumberItem.setState(new DecimalType(value));
                    }
                }
            }*/
            return CompletableFuture.completedFuture(value);
        }, setIntConsumer(endpoint), getSubscriber(variable, endpoint, DURATION),
                getUnsubscriber(variable, endpoint, DURATION));
    }

    private static FilterLifeLevelCharacteristic createFilterLifeLevelCharacteristic(HomekitEndpointEntity endpoint) {
        return new FilterLifeLevelCharacteristic(getDoubleSupplier(endpoint, 0),
                getSubscriber(variable, endpoint, FILTER_LIFE_LEVEL),
                getUnsubscriber(variable, endpoint, FILTER_LIFE_LEVEL));
    }

    private static ResetFilterIndicationCharacteristic createFilterResetCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getFilterResetIndication());
        return new ResetFilterIndicationCharacteristic(
                (value) -> variable.set(OnOffType.ON));
    }

    private static FirmwareRevisionCharacteristic createFirmwareRevisionCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getFirmwareRevision());
        return new FirmwareRevisionCharacteristic(() -> {
            final State state = variable.getValue();
            return CompletableFuture.completedFuture(state == null ? "" : state.toString());
        });
    }

    private static HardwareRevisionCharacteristic createHardwareRevisionCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getHardwareRevision());
        return new HardwareRevisionCharacteristic(() -> {
            final State state = variable.getValue();
            return CompletableFuture.completedFuture(state == null ? "" : state.toString());
        });
    }

    private static HeatingThresholdTemperatureCharacteristic createHeatingThresholdCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getHeatingThresholdTemperature());
        double minValue = variable.getMinValue(HeatingThresholdTemperatureCharacteristic.DEFAULT_MIN_VALUE);
        double maxValue = variable.getMinValue(HeatingThresholdTemperatureCharacteristic.DEFAULT_MAX_VALUE);
        double step = variable.getMinValue(HeatingThresholdTemperatureCharacteristic.DEFAULT_STEP);

        return new HeatingThresholdTemperatureCharacteristic(minValue, maxValue, step,
                getTemperatureSupplier(endpoint, minValue), setTemperatureConsumer(endpoint),
                getSubscriber(variable, endpoint, HEATING_THRESHOLD_TEMPERATURE),
                getUnsubscriber(variable, endpoint, HEATING_THRESHOLD_TEMPERATURE));
    }

    private static HoldPositionCharacteristic createWindowHoldPositionCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getWindowHoldPosition());
        /*if (!(item instanceof SwitchItem || item instanceof RollershutterItem)) {
            log.warn(
                    "Item {} cannot be used for the HoldPosition characteristic; only SwitchItem and RollershutterItem are supported. Hold requests will be ignored.",
                    item.getName());
        }*/

        return new HoldPositionCharacteristic(value -> {
            if (!value) {
                return;
            }
            variable.set(OnOffType.ON);
            /*if (item instanceof SwitchItem switchItem) {
                switchItem.send(OnOffType.ON);
            } else if (item instanceof RollershutterItem rollershutterItem) {
                rollershutterItem.send(StopMoveType.STOP);
            }*/
        });
    }

    private static HueCharacteristic createHueCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getHue());
        return new HueCharacteristic(() -> {
            double value = variable.getValue().floatValue();

            /*if (state instanceof HSBType stateAsHSBType) {
                value = stateAsHSBType.getHue().doubleValue();
            }*/
            return CompletableFuture.completedFuture(value);
        }, (hue) -> {
            /*if (endpoint.getBaseItem() instanceof ColorItem) {
                endpoint.sendCommandProxy(HomekitCommandType.HUE_COMMAND, new DecimalType(hue));
            } else {*/
                /*log.warn("Item type {} is not supported for {}. Only Color type is supported.",
                        endpoint.getBaseItem().getType(), endpoint.getName());*/
            // }
        }, getSubscriber(variable, endpoint, HUE), getUnsubscriber(variable, endpoint, HUE));
    }

    private static IdentifierCharacteristic createIdentifierCharacteristic(HomekitEndpointEntity endpoint) {
        return new IdentifierCharacteristic(getIntSupplier(endpoint, 1));
    }

    /*private static IdentifyCharacteristic createIdentifyCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(IDENTIFY);
        return new IdentifyCharacteristic((value) -> variable.set(OnOffType.ON));
    }*/

    private static InputDeviceTypeCharacteristic createInputDeviceTypeCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, InputDeviceTypeEnum.class);
        return new InputDeviceTypeCharacteristic(() -> getEnumFromItem(endpoint, map, InputDeviceTypeEnum.OTHER),
                getSubscriber(variable, endpoint, INPUT_DEVICE_TYPE),
                getUnsubscriber(variable, endpoint, INPUT_DEVICE_TYPE));
    }

    private static InputSourceTypeCharacteristic createInputSourceTypeCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, InputSourceTypeEnum.class);
        return new InputSourceTypeCharacteristic(() -> getEnumFromItem(endpoint, map, InputSourceTypeEnum.OTHER),
                getSubscriber(variable, endpoint, INPUT_SOURCE_TYPE),
                getUnsubscriber(variable, endpoint, INPUT_SOURCE_TYPE));
    }

    private static IsConfiguredCharacteristic createIsConfiguredCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, IsConfiguredEnum.class);
        return new IsConfiguredCharacteristic(() -> getEnumFromItem(endpoint, map, IsConfiguredEnum.NOT_CONFIGURED),
                (value) -> setValueFromEnum(endpoint, value, map), getSubscriber(variable, endpoint, CONFIGURED),
                getUnsubscriber(variable, endpoint, CONFIGURED));
    }

    private static LockPhysicalControlsCharacteristic createLockPhysicalControlsCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, LockPhysicalControlsEnum.class);
        return new LockPhysicalControlsCharacteristic(
                () -> getEnumFromItem(endpoint, map, LockPhysicalControlsEnum.CONTROL_LOCK_DISABLED),
                (value) -> setValueFromEnum(endpoint, value, map), getSubscriber(variable, endpoint, LOCK_CONTROL),
                getUnsubscriber(variable, endpoint, LOCK_CONTROL));
    }

    private static LockCurrentStateCharacteristic createLockCurrentStateCharacteristic(org.homio.addon.homekit.HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, LockCurrentStateEnum.class);
        return new LockCurrentStateCharacteristic(() -> getEnumFromItem(endpoint, map, LockCurrentStateEnum.UNKNOWN),
                getSubscriber(variable, endpoint, LOCK_CURRENT_STATE),
                getUnsubscriber(variable, endpoint, LOCK_CURRENT_STATE));
    }

    private static LockTargetStateCharacteristic createLockTargetStateCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, LockTargetStateEnum.class);
        return new LockTargetStateCharacteristic(() -> getEnumFromItem(endpoint, map, LockTargetStateEnum.UNSECURED),
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint, LOCK_TARGET_STATE),
                getUnsubscriber(variable, endpoint, LOCK_TARGET_STATE));
    }

    private static ManufacturerCharacteristic createManufacturerCharacteristic(HomekitEndpointEntity endpoint) {
        return new ManufacturerCharacteristic(() -> {
            final State state = endpoint.getVariable(MANUFACTURER).getValue();
            return CompletableFuture.completedFuture(state == null ? "" : state.toString());
        });
    }

    private static ModelCharacteristic createModelCharacteristic(HomekitEndpointEntity endpoint) {
        return new ModelCharacteristic(() -> {
            final State state = endpoint.getVariable(MODEL).getValue();
            return CompletableFuture.completedFuture(state == null ? "" : state.toString());
        });
    }

    private static MuteCharacteristic createMuteCharacteristic(HomekitEndpointEntity endpoint) {
        /*BooleanItemReader muteReader = new BooleanItemReader(endpoint.getItem(),
                OnOffType.of(!endpoint.isInverted()),
                endpoint.isInverted() ? OnOffType.OFF : OnOffType.ON);*/
        var variable = endpoint.getVariable(MUTE);
        return new MuteCharacteristic(() -> CompletableFuture.completedFuture(variable.getValue().boolValue()),
                (value) -> variable.set(OnOffType.of(value)), getSubscriber(variable, endpoint, MUTE),
                getUnsubscriber(variable, endpoint, MUTE));
    }

    private static NameCharacteristic createNameCharacteristic(HomekitEndpointEntity endpoint) {
        return new NameCharacteristic(() -> {
            final State state = endpoint.getVariable(NAME).getValue();
            return CompletableFuture.completedFuture(state == null ? "" : state.toString());
        });
    }

    /*private static NitrogenDioxideDensityCharacteristic createNitrogenDioxideDensityCharacteristic(HomekitEndpointEntity endpoint) {
        return new NitrogenDioxideDensityCharacteristic(
                getDoubleSupplier(endpoint,
                        endpoint.getConfigurationAsDouble(HomekitEndpoint.MIN_VALUE,
                                NitrogenDioxideDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint, NITROGEN_DIOXIDE_DENSITY),
                getUnsubscriber(variable, endpoint, NITROGEN_DIOXIDE_DENSITY));
    }*/

    private static ObstructionDetectedCharacteristic createObstructionDetectedCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(OBSTRUCTION_STATUS);
        return new ObstructionDetectedCharacteristic(
                () -> CompletableFuture.completedFuture(variable.getValue().boolValue()),
                getSubscriber(variable, endpoint, OBSTRUCTION_STATUS),
                getUnsubscriber(variable, endpoint, OBSTRUCTION_STATUS));
    }

    private static OzoneDensityCharacteristic createOzoneDensityCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(OZONE_DENSITY);
        return new OzoneDensityCharacteristic(
                getDoubleSupplier(endpoint, variable.getMinValue(OzoneDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint, OZONE_DENSITY), getUnsubscriber(variable, endpoint, OZONE_DENSITY));
    }

    private static PM10DensityCharacteristic createPM10DensityCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(PM10_DENSITY);
        return new PM10DensityCharacteristic(
                getDoubleSupplier(endpoint, variable.getMinValue(PM10DensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint, PM10_DENSITY), getUnsubscriber(variable, endpoint, PM10_DENSITY));
    }

    private static PM25DensityCharacteristic createPM25DensityCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(PM25_DENSITY);
        return new PM25DensityCharacteristic(
                getDoubleSupplier(endpoint, variable.getMinValue(PM25DensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint, PM25_DENSITY), getUnsubscriber(variable, endpoint, PM25_DENSITY));
    }

    private static CurrentRelativeHumidityCharacteristic createRelativeHumidityCharacteristic(
            HomekitEndpointEntity endpoint) {
        return new CurrentRelativeHumidityCharacteristic(getDoubleSupplier(endpoint, 0.0),
                getSubscriber(variable, endpoint, RELATIVE_HUMIDITY),
                getUnsubscriber(variable, endpoint, RELATIVE_HUMIDITY));
    }

    private static PictureModeCharacteristic createPictureModeCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, PictureModeEnum.class);
        return new PictureModeCharacteristic(() -> getEnumFromItem(endpoint, map, PictureModeEnum.OTHER),
                (value) -> setValueFromEnum(endpoint, value, map), getSubscriber(variable, endpoint, PICTURE_MODE),
                getUnsubscriber(variable, endpoint, PICTURE_MODE));
    }

    private static PowerModeCharacteristic createPowerModeCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, PowerModeEnum.class, true);
        return new PowerModeCharacteristic((value) -> setValueFromEnum(endpoint, value, map));
    }

    // this characteristic is unique in a few ways, so we can't use the "normal" helpers:
    // * you don't return a "current" value, just the value of the most recent event
    // * NULL/invalid values are very much expected, and should silently _not_ trigger an event
    // * every update to the item should trigger an event, not just changes

    private static ProgrammableSwitchEventCharacteristic createProgrammableSwitchEventCharacteristic(
            HomekitEndpointEntity endpoint) {
        // have to build the map custom, since SINGLE_PRESS starts at 0
        Map<ProgrammableSwitchEnum, Object> map = new EnumMap(ProgrammableSwitchEnum.class);
        List<ProgrammableSwitchEnum> validValues = new ArrayList<>();

        /*if (endpoint.getBaseItem().getAcceptedDataTypes().contains(OnOffType.class)) {
            map.put(ProgrammableSwitchEnum.SINGLE_PRESS, OnOffType.ON.toString());
            validValues.add(ProgrammableSwitchEnum.SINGLE_PRESS);
        } else if (endpoint.getBaseItem().getAcceptedDataTypes().contains(OpenClosedType.class)) {
            map.put(ProgrammableSwitchEnum.SINGLE_PRESS, OpenClosedType.OPEN.toString());
            validValues.add(ProgrammableSwitchEnum.SINGLE_PRESS);
        } else {
            map = createMapping(endpoint, ProgrammableSwitchEnum.class, validValues, false);
        }*/

        var helper = new ProgrammableSwitchEventCharacteristicHelper(endpoint, map);

        return new ProgrammableSwitchEventCharacteristic(validValues.toArray(new ProgrammableSwitchEnum[0]),
                helper::getValue, helper::subscribe, getUnsubscriber(variable, endpoint, PROGRAMMABLE_SWITCH_EVENT));
    }

    private static RemainingDurationCharacteristic createRemainingDurationCharacteristic(HomekitEndpointEntity endpoint) {
        return new RemainingDurationCharacteristic(getIntSupplier(endpoint, 0),
                getSubscriber(variable, endpoint, REMAINING_DURATION),
                getUnsubscriber(variable, endpoint, REMAINING_DURATION));
    }

    private static RemoteKeyCharacteristic createRemoteKeyCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, RemoteKeyEnum.class);
        return new RemoteKeyCharacteristic((value) -> setValueFromEnum(endpoint, value, map));
    }

    private static RotationDirectionCharacteristic createRotationDirectionCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, RotationDirectionEnum.class);
        return new RotationDirectionCharacteristic(
                () -> getEnumFromItem(endpoint, map, RotationDirectionEnum.CLOCKWISE),
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint, ROTATION_DIRECTION),
                getUnsubscriber(variable, endpoint, ROTATION_DIRECTION));
    }

    private static RotationSpeedCharacteristic createRotationSpeedCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(ROTATION_SPEED);
        return new RotationSpeedCharacteristic(
                variable.getMinValue(RotationSpeedCharacteristic.DEFAULT_MIN_VALUE),
                variable.getMaxValue(RotationSpeedCharacteristic.DEFAULT_MAX_VALUE),
                variable.getStep(RotationSpeedCharacteristic.DEFAULT_STEP),
                getDoubleSupplier(endpoint, 0), setDoubleConsumer(endpoint), getSubscriber(variable, endpoint, ROTATION_SPEED),
                getUnsubscriber(variable, endpoint, ROTATION_SPEED));
    }

    private static SaturationCharacteristic createSaturationCharacteristic(HomekitEndpointEntity endpoint) {
        return new SaturationCharacteristic(() -> {
            State state = endpoint.getVariable(SATURATION).getValue();
            double value = state.floatValue();
            /*if (state instanceof HSBType stateAsHSBType) {
                value = stateAsHSBType.getSaturation().doubleValue();
            } else if (state instanceof PercentType stateAsPercentType) {
                value = stateAsPercentType.doubleValue();
            }*/
            return CompletableFuture.completedFuture(value);
        }, (saturation) -> {
            /*if (endpoint.getBaseItem() instanceof ColorItem) {
                endpoint.sendCommandProxy(HomekitCommandType.SATURATION_COMMAND,
                        new PercentType(saturation.intValue()));
            } else {*/
                /*log.warn("Item type {} is not supported for {}. Only Color type is supported.",
                        endpoint.getBaseItem().getType(), endpoint.getName());*/
            //}
        }, getSubscriber(variable, endpoint, SATURATION), getUnsubscriber(variable, endpoint, SATURATION));
    }

    private static SerialNumberCharacteristic createSerialNumberCharacteristic(HomekitEndpointEntity endpoint) {
        return new SerialNumberCharacteristic(() -> {
            final State state = endpoint.getVariable(SERIAL_NUMBER).getValue();
            return CompletableFuture.completedFuture(state == null ? "" : state.toString());
        });
    }

    private static SleepDiscoveryModeCharacteristic createSleepDiscoveryModeCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, SleepDiscoveryModeEnum.class);
        return new SleepDiscoveryModeCharacteristic(
                () -> getEnumFromItem(endpoint, map, SleepDiscoveryModeEnum.ALWAYS_DISCOVERABLE),
                getSubscriber(variable, endpoint, SLEEP_DISCOVERY_MODE),
                getUnsubscriber(variable, endpoint, SLEEP_DISCOVERY_MODE));
    }

    private static StatusActiveCharacteristic createStatusActiveCharacteristic(HomekitEndpointEntity endpoint) {
        return new StatusActiveCharacteristic(
                () -> CompletableFuture.completedFuture(endpoint.getVariable(ACTIVE_STATUS).getValue().boolValue()),
                getSubscriber(variable, endpoint, ACTIVE_STATUS), getUnsubscriber(variable, endpoint, ACTIVE_STATUS));
    }

    private static StatusFaultCharacteristic createStatusFaultCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, StatusFaultEnum.class);
        return new StatusFaultCharacteristic(() -> getEnumFromItem(endpoint, map, StatusFaultEnum.NO_FAULT),
                getSubscriber(variable, endpoint, FAULT_STATUS), getUnsubscriber(variable, endpoint, FAULT_STATUS));
    }

    private static StatusLowBatteryCharacteristic createStatusLowBatteryCharacteristic(HomekitEndpointEntity endpoint) {
        /*BigDecimal lowThreshold = endpoint.getConfiguration(HomekitEndpoint.BATTERY_LOW_THRESHOLD,
                BigDecimal.valueOf(20));*/
        var variable = endpoint.getVariable(endpoint.getBatteryLowStatus())
        /*BooleanItemReader lowBatteryReader = new BooleanItemReader(endpoint.getItem(),
                OnOffType.of(!endpoint.isInverted()),
                endpoint.isInverted() ? OnOffType.OFF : OnOffType.ON, lowThreshold, true);*/
        return new StatusLowBatteryCharacteristic(
                () -> CompletableFuture.completedFuture(
                        variable.getValue().boolValue() ? StatusLowBatteryEnum.LOW : StatusLowBatteryEnum.NORMAL),
                getSubscriber(variable, endpoint, BATTERY_LOW_STATUS),
                getUnsubscriber(variable, endpoint, BATTERY_LOW_STATUS));
    }

    private static StatusTamperedCharacteristic createStatusTamperedCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, StatusTamperedEnum.class);
        return new StatusTamperedCharacteristic(() -> getEnumFromItem(endpoint, map, StatusTamperedEnum.NOT_TAMPERED),
                getSubscriber(variable, endpoint, TAMPERED_STATUS),
                getUnsubscriber(variable, endpoint, TAMPERED_STATUS));
    }

    private static SwingModeCharacteristic createSwingModeCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, SwingModeEnum.class);
        return new SwingModeCharacteristic(() -> getEnumFromItem(endpoint, map, SwingModeEnum.SWING_DISABLED),
                (value) -> setValueFromEnum(endpoint, value, map), getSubscriber(variable, endpoint, SWING_MODE),
                getUnsubscriber(variable, endpoint, SWING_MODE));
    }

    /*private static SulphurDioxideDensityCharacteristic createSulphurDioxideDensityCharacteristic(HomekitEndpointEntity endpoint) {
        return new SulphurDioxideDensityCharacteristic(
                getDoubleSupplier(endpoint,
                        endpoint.getConfigurationAsDouble(HomekitEndpoint.MIN_VALUE,
                                SulphurDioxideDensityCharacteristic.DEFAULT_MIN_VALUE)),
                getSubscriber(variable, endpoint, SULPHUR_DIOXIDE_DENSITY),
                getUnsubscriber(variable, endpoint, SULPHUR_DIOXIDE_DENSITY));
    }*/

    private static TargetDoorStateCharacteristic createTargetDoorStateCharacteristic(HomekitEndpointEntity endpoint) {
        /*if (endpoint.getBaseItem() instanceof RollershutterItem) {
            return new TargetDoorStateCharacteristic(() -> {
                if (endpoint.getItem().getState() instanceof PercentType percentType
                        && percentType.equals(PercentType.HUNDRED)) {
                    return CompletableFuture.completedFuture(TargetDoorStateEnum.CLOSED);
                }
                return CompletableFuture.completedFuture(TargetDoorStateEnum.OPEN);
            }, (targetState) -> endpoint
                    .send(targetState.equals(TargetDoorStateEnum.OPEN) ? UpDownType.UP : UpDownType.DOWN),
                    getSubscriber(variable, endpoint, TARGET_DOOR_STATE),
                    getUnsubscriber(variable, endpoint, TARGET_DOOR_STATE));
        } else {*/
        List<TargetDoorStateEnum> validValues = new ArrayList<>();
        var map = createMapping(endpoint, TargetDoorStateEnum.class, validValues, true);
        return new TargetDoorStateCharacteristic(() -> getEnumFromItem(endpoint, map, TargetDoorStateEnum.CLOSED),
                (targetState) -> setValueFromEnum(endpoint, targetState, map),
                getSubscriber(variable, endpoint, TARGET_DOOR_STATE),
                getUnsubscriber(variable, endpoint, TARGET_DOOR_STATE));
        //}
    }

    private static TargetFanStateCharacteristic createTargetFanStateCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, TargetFanStateEnum.class);
        return new TargetFanStateCharacteristic(() -> getEnumFromItem(endpoint, map, TargetFanStateEnum.AUTO),
                (targetState) -> setValueFromEnum(endpoint, targetState, map),
                getSubscriber(variable, endpoint, TARGET_FAN_STATE),
                getUnsubscriber(variable, endpoint, TARGET_FAN_STATE));
    }

    private static TargetHeatingCoolingStateCharacteristic createTargetHeatingCoolingStateCharacteristic(HomekitEndpointEntity endpoint) {
        List<TargetHeatingCoolingStateEnum> validValues = new ArrayList<>();
        var map = createMapping(endpoint, TargetHeatingCoolingStateEnum.class, validValues);
        return new TargetHeatingCoolingStateCharacteristic(validValues.toArray(new TargetHeatingCoolingStateEnum[0]),
                () -> getEnumFromItem(endpoint, map, TargetHeatingCoolingStateEnum.OFF),
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint, TARGET_HEATING_COOLING_STATE),
                getUnsubscriber(variable, endpoint, TARGET_HEATING_COOLING_STATE));
    }

    private static TargetHorizontalTiltAngleCharacteristic createTargetHorizontalTiltAngleCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getTargetHorizontalTiltAngle());
        return new TargetHorizontalTiltAngleCharacteristic(getAngleSupplier(variable, 0),
                setAngleConsumer(endpoint), getSubscriber(variable, endpoint, TARGET_HORIZONTAL_TILT_ANGLE),
                getUnsubscriber(variable, endpoint, TARGET_HORIZONTAL_TILT_ANGLE));
    }

    private static TargetMediaStateCharacteristic createTargetMediaStateCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, TargetMediaStateEnum.class);
        return new TargetMediaStateCharacteristic(() -> getEnumFromItem(endpoint, map, TargetMediaStateEnum.STOP),
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint, TARGET_MEDIA_STATE),
                getUnsubscriber(variable, endpoint, TARGET_MEDIA_STATE));
    }

    private static TargetRelativeHumidityCharacteristic createTargetRelativeHumidityCharacteristic(HomekitEndpointEntity endpoint) {
        return new TargetRelativeHumidityCharacteristic(getDoubleSupplier(endpoint, 0), setDoubleConsumer(endpoint),
                getSubscriber(variable, endpoint, TARGET_RELATIVE_HUMIDITY),
                getUnsubscriber(variable, endpoint, TARGET_RELATIVE_HUMIDITY));
    }

    private static TargetTemperatureCharacteristic createTargetTemperatureCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(TARGET_TEMPERATURE);
        double minValue = variable.getMinValue(TargetTemperatureCharacteristic.DEFAULT_MIN_VALUE);
        double maxValue = variable.getMinValue(TargetTemperatureCharacteristic.DEFAULT_MAX_VALUE);
        double step = variable.getMinValue(TargetTemperatureCharacteristic.DEFAULT_STEP);

        /*double minValue = endpoint
                .getConfigurationAsQuantity(HomekitEndpoint.MIN_VALUE,
                        Objects.requireNonNull(
                                new QuantityType(TargetTemperatureCharacteristic.DEFAULT_MIN_VALUE, SIUnits.CELSIUS)
                                        .toUnit(getSystemTemperatureUnit())),
                        false)
                .toUnit(SIUnits.CELSIUS).doubleValue();
        double maxValue = endpoint
                .getConfigurationAsQuantity(HomekitEndpoint.MAX_VALUE,
                        Objects.requireNonNull(
                                new QuantityType(TargetTemperatureCharacteristic.DEFAULT_MAX_VALUE, SIUnits.CELSIUS)
                                        .toUnit(getSystemTemperatureUnit())),
                        false)
                .toUnit(SIUnits.CELSIUS).doubleValue();
        double step = endpoint
                .getConfigurationAsQuantity(HomekitEndpoint.STEP,
                        Objects.requireNonNull(
                                new QuantityType(TargetTemperatureCharacteristic.DEFAULT_STEP, SIUnits.CELSIUS)
                                        .toUnit(getSystemTemperatureUnit())),
                        true)
                .toUnitRelative(SIUnits.CELSIUS).doubleValue();*/
        return new TargetTemperatureCharacteristic(minValue, maxValue, step,
                getTemperatureSupplier(endpoint, minValue), setTemperatureConsumer(endpoint),
                getSubscriber(variable, endpoint, TARGET_TEMPERATURE),
                getUnsubscriber(variable, endpoint, TARGET_TEMPERATURE));
    }

    private static TargetTiltAngleCharacteristic createTargetTiltAngleCharacteristic(HomekitEndpointEntity endpoint) {
        return new TargetTiltAngleCharacteristic(getAngleSupplier(endpoint, 0), setAngleConsumer(endpoint),
                getSubscriber(variable, endpoint, TARGET_TILT_ANGLE),
                getUnsubscriber(variable, endpoint, TARGET_TILT_ANGLE));
    }

    private static TargetVerticalTiltAngleCharacteristic createTargetVerticalTiltAngleCharacteristic(HomekitEndpointEntity endpoint) {
        return new TargetVerticalTiltAngleCharacteristic(getAngleSupplier(endpoint, 0), setAngleConsumer(endpoint),
                getSubscriber(variable, endpoint, TARGET_HORIZONTAL_TILT_ANGLE),
                getUnsubscriber(variable, endpoint, TARGET_HORIZONTAL_TILT_ANGLE));
    }

    private static TargetVisibilityStateCharacteristic createTargetVisibilityStateCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, TargetVisibilityStateEnum.class, true);
        return new TargetVisibilityStateCharacteristic(
                () -> getEnumFromItem(endpoint, map, TargetVisibilityStateEnum.HIDDEN),
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint, TARGET_VISIBILITY_STATE),
                getUnsubscriber(variable, endpoint, TARGET_VISIBILITY_STATE));
    }

    private static TemperatureDisplayUnitCharacteristic createTemperatureDisplayUnitCharacteristic(HomekitEndpointEntity endpoint) {
        var map = createMapping(endpoint, TemperatureDisplayUnitEnum.class, true);
        return new TemperatureDisplayUnitCharacteristic(
                () -> getEnumFromItem(endpoint, map,
                        useFahrenheit() ? TemperatureDisplayUnitEnum.FAHRENHEIT : TemperatureDisplayUnitEnum.CELSIUS),
                (value) -> setValueFromEnum(endpoint, value, map),
                getSubscriber(variable, endpoint, TEMPERATURE_UNIT),
                getUnsubscriber(variable, endpoint, TEMPERATURE_UNIT));
    }

    private static VOCDensityCharacteristic createVOCDensityCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(VOC_DENSITY);
        double minValue = variable.getMinValue(VOCDensityCharacteristic.DEFAULT_MIN_VALUE);
        return new VOCDensityCharacteristic(
                minValue,
                variable.getMaxValue(VOCDensityCharacteristic.DEFAULT_MAX_VALUE),
                variable.getStep(VOCDensityCharacteristic.DEFAULT_STEP),
                getDoubleSupplier(endpoint, minValue),
                getSubscriber(variable, endpoint, VOC_DENSITY), getUnsubscriber(variable, endpoint, VOC_DENSITY));
    }

    private static VolumeCharacteristic createVolumeCharacteristic(HomekitEndpointEntity endpoint) {
        return new VolumeCharacteristic(getIntSupplier(endpoint, 0),
                (volume) -> {
                    // endpoint.send(new DecimalType(volume));
                },
                getSubscriber(variable, endpoint, DURATION), getUnsubscriber(variable, endpoint, DURATION));
    }

    private static VolumeSelectorCharacteristic createVolumeSelectorCharacteristic(HomekitEndpointEntity endpoint) {
        /*if (endpoint.getItem() instanceof DimmerItem) {
            return new VolumeSelectorCharacteristic((value) -> endpoint
                    .send(value.equals(VolumeSelectorEnum.INCREMENT) ? IncreaseDecreaseType.INCREASE
                            : IncreaseDecreaseType.DECREASE));
        } else {*/
        var map = createMapping(endpoint, VolumeSelectorEnum.class);
        return new VolumeSelectorCharacteristic((value) -> setValueFromEnum(endpoint, value, map));
        // }
    }

    private static VolumeControlTypeCharacteristic createVolumeControlTypeCharacteristic(HomekitEndpointEntity endpoint) {
        var variable = endpoint.getVariable(endpoint.getVolumeControlType());
        var map = createMapping(endpoint, VolumeControlTypeEnum.class);
        return new VolumeControlTypeCharacteristic(() -> getEnumFromItem(endpoint, map, VolumeControlTypeEnum.NONE),
                getSubscriber(variable, endpoint, VOLUME_CONTROL_TYPE),
                getUnsubscriber(variable, endpoint, VOLUME_CONTROL_TYPE));
    }

    private static class ProgrammableSwitchEventCharacteristicHelper {
        private final HomekitEndpointEntity endpoint;
        private final Map<ProgrammableSwitchEnum, Object> map;
        private @Nullable ProgrammableSwitchEnum lastValue = null;

        ProgrammableSwitchEventCharacteristicHelper(HomekitEndpointEntity endpoint, Map<ProgrammableSwitchEnum, Object> map) {
            this.endpoint = endpoint;
            this.map = map;
        }

        public CompletableFuture<ProgrammableSwitchEnum> getValue() {
            return CompletableFuture.completedFuture(lastValue);
        }

        public void subscribe(HomekitCharacteristicChangeCallback cb) {
            /*updater.subscribeToUpdates(endpoint, PROGRAMMABLE_SWITCH_EVENT.getTag(),
                    state -> {
                        // perform inversion here, so logic below only needs to deal with the
                        // canonical style
                        if (state instanceof OnOffType && endpoint.isInverted()) {
                            if (state.equals(OnOffType.ON)) {
                                state = OnOffType.OFF;
                            } else {
                                state = OnOffType.ON;
                            }
                        } else if (state instanceof OnOffType && endpoint.isInverted()) {
                            if (state.equals(OnOffType.ON)) {
                                state = OnOffType.OFF;
                            } else {
                                state = OnOffType.ON;
                            }
                        }
                        // if "not pressed", don't send an event
                        if (state == null || (state instanceof OnOffType && state.equals(OnOffType.OFF))
                                || (state instanceof OnOffType && state.equals(OnOffType.OFF))) {
                            lastValue = null;
                            return;
                        }
                        lastValue = getKeyFromMapping(endpoint, state, map, ProgrammableSwitchEnum.SINGLE_PRESS);
                        cb.changed();
                    });
        }*/
        }
    }
}
