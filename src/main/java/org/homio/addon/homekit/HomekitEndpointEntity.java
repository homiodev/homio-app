package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.ContextVar.Variable;
import org.homio.api.entity.BaseEntity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.UIFieldVariableSelection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.homio.addon.homekit.enums.HomekitAccessoryType.DUMMY;
import static org.homio.api.ContextVar.VariableType.Bool;

@Getter
@Setter
public class HomekitEndpointEntity extends BaseEntity {

    private transient HomekitService service;
    private transient int id = -1;
    /**
     * User-defined name for this HomeKit accessory endpoint.
     * This name is often used as the primary display name in HomeKit apps.
     * Used by: All accessory types.
     * Characteristic: Often implicitly used for the Accessory Information service's Name characteristic if not overridden by a specific 'configuredName' or similar.
     */
    @UIField(order = 1)
    private String name = "";
    /**
     * The type of HomeKit accessory this entity represents.
     * Determines which characteristics are available and how the device behaves in HomeKit.
     * Used by: All accessory types (defines the entity's fundamental type).
     */
    @UIField(order = 2)
    private HomekitAccessoryType accessoryType = HomekitAccessoryType.SWITCH;
    /**
     * Represents the active state of an accessory.
     * Characteristic: Active (0 = Inactive, 1 = Active).
     * Used by (Required): AIR_PURIFIER, FAN, FAUCET, HEATER_COOLER, HUMIDIFIER_DEHUMIDIFIER, IRRIGATION_SYSTEM, TELEVISION, VALVE.
     */
    @UIField(order = 5)
    @UIFieldShowOnCondition("return ['AIR_PURIFIER', 'FAN', 'FAUCET', 'HEATER_COOLER', 'HUMIDIFIER_DEHUMIDIFIER', 'IRRIGATION_SYSTEM', 'TELEVISION', 'VALVE'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup(value = "REQ_CHAR", order = 100, borderColor = "#8C3265")
    private String activeState;

    // --- Consolidated / Widely Used Fields ---
    /**
     * Represents the detected state for various binary sensors.
     * Characteristic: Varies by sensor (e.g., ContactSensorState, MotionDetected, SmokeDetected, LeakDetected, OccupancyDetected, CarbonMonoxideDetected, CarbonDioxideDetected).
     * Values typically 0 (Not Detected) or 1 (Detected).
     * Used by (Required): CARBON_DIOXIDE_SENSOR, CARBON_MONOXIDE_SENSOR, CONTACT_SENSOR, LEAK_SENSOR, MOTION_SENSOR, OCCUPANCY_SENSOR, SMOKE_SENSOR.
     */
    @UIField(order = 6)
    @UIFieldShowOnCondition("return ['CARBON_DIOXIDE_SENSOR', 'CARBON_MONOXIDE_SENSOR', 'CONTACT_SENSOR', 'LEAK_SENSOR', 'MOTION_SENSOR', 'OCCUPANCY_SENSOR', 'SMOKE_SENSOR'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String detectedState;
    /**
     * Represents the target position for accessories that can be set to a specific position (e.g., doors, windows, blinds).
     * Characteristic: TargetPosition (0-100%).
     * Used by (Required): DOOR, GARAGE_DOOR_OPENER, WINDOW, WINDOW_COVERING.
     */
    @UIField(order = 7)
    @UIFieldShowOnCondition("return ['DOOR', 'GARAGE_DOOR_OPENER', 'WINDOW', 'WINDOW_COVERING'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetPosition;
    /**
     * Represents the current position of accessories (e.g., doors, windows, blinds).
     * Characteristic: CurrentPosition (0-100%).
     * Used by (Required): DOOR, GARAGE_DOOR_OPENER, WINDOW, WINDOW_COVERING.
     */
    @UIField(order = 8)
    @UIFieldShowOnCondition("return ['DOOR', 'GARAGE_DOOR_OPENER', 'WINDOW', 'WINDOW_COVERING'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentPosition;
    /**
     * Represents the state of movement for positional accessories.
     * Characteristic: PositionState (0 = Decreasing, 1 = Increasing, 2 = Stopped).
     * Used by (Required): DOOR, WINDOW, WINDOW_COVERING. (GarageDoorOpener uses CurrentDoorState/TargetDoorState which imply movement).
     */
    @UIField(order = 9)
    @UIFieldShowOnCondition("return ['DOOR', 'WINDOW', 'WINDOW_COVERING'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String positionState;
    /**
     * Represents the On/Off state for simple switchable devices.
     * Characteristic: On (true/false or 1/0).
     * Used by (Required): OUTLET, SWITCH, LIGHTBULB, BASIC_FAN.
     */
    @UIField(order = 10)
    @UIFieldShowOnCondition("return ['OUTLET', 'SWITCH', 'LIGHTBULB', 'BASIC_FAN'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String onState;
    /**
     * Represents the mute state for audio output devices.
     * Characteristic: Mute (true/false or 1/0).
     * Used by (Required): MICROPHONE, SPEAKER, SMART_SPEAKER, TELEVISION_SPEAKER.
     */
    @UIField(order = 11)
    @UIFieldShowOnCondition("return ['TELEVISION_SPEAKER', 'SPEAKER', 'MICROPHONE', 'SMART_SPEAKER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String mute;
    /**
     * Represents a programmable switch event.
     * Characteristic: ProgrammableSwitchEvent (0 = Single Press, 1 = Double Press, 2 = Long Press). This is for stateless switches.
     * Used by (Required): DOORBELL, STATELESS_PROGRAMMABLE_SWITCH, VIDEO_DOORBELL.
     */
    @UIField(order = 12)
    @UIFieldShowOnCondition("return ['DOORBELL', 'STATELESS_PROGRAMMABLE_SWITCH', 'VIDEO_DOORBELL'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String programmableSwitchEvent;
    /**
     * Represents the current temperature reading.
     * Characteristic: CurrentTemperature (Celsius).
     * Used by (Required): HEATER_COOLER, TEMPERATURE_SENSOR, THERMOSTAT.
     */
    @UIField(order = 13)
    @UIFieldShowOnCondition("return ['HEATER_COOLER', 'TEMPERATURE_SENSOR', 'THERMOSTAT'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentTemperature;
    /**
     * Represents the target heating/cooling state for climate control devices.
     * Characteristic: TargetHeatingCoolingState (0 = Off, 1 = Heat, 2 = Cool, 3 = Auto).
     * Used by (Required): HEATER_COOLER, THERMOSTAT.
     */
    @UIField(order = 14)
    @UIFieldShowOnCondition("return ['HEATER_COOLER', 'THERMOSTAT'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetHeatingCoolingState;
    /**
     * Represents the current heating/cooling mode of a climate control device.
     * Characteristic: CurrentHeatingCoolingState (0 = Off, 1 = Heat, 2 = Cool).
     * Used by (Required): HEATER_COOLER, THERMOSTAT.
     */
    @UIField(order = 15)
    @UIFieldShowOnCondition("return ['HEATER_COOLER', 'THERMOSTAT'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    // Often mapped to 0/1/2, but source var might be boolean for simpler cases
    @UIFieldGroup("REQ_CHAR")
    private String currentHeatingCoolingState;
    /**
     * Indicates whether the accessory is currently in use.
     * Characteristic: InUse (0 = Not In Use, 1 = In Use).
     * Used by (Required): VALVE, OUTLET, IRRIGATION_SYSTEM, FAUCET.
     */
    @UIField(order = 16)
    @UIFieldShowOnCondition("return ['VALVE', 'OUTLET', 'IRRIGATION_SYSTEM', 'FAUCET'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String inuseStatus;
    /**
     * Indicates the battery status of the accessory.
     * Characteristic: StatusLowBattery (0 = Normal, 1 = Low).
     * Used by (Optional): Many battery-powered sensors and devices.
     * (AIR_QUALITY_SENSOR, BATTERY, CARBON_DIOXIDE_SENSOR, CARBON_MONOXIDE_SENSOR, CONTACT_SENSOR, HUMIDITY_SENSOR, LEAK_SENSOR, LIGHT_SENSOR, MOTION_SENSOR, OCCUPANCY_SENSOR, SMOKE_SENSOR, TEMPERATURE_SENSOR).
     * For BATTERY accessory type, this is often linked to its primary battery level.
     */
    @UIField(order = 17)
    @UIFieldShowOnCondition("return ['AIR_QUALITY_SENSOR', 'BATTERY', 'CARBON_DIOXIDE_SENSOR', 'CARBON_MONOXIDE_SENSOR', 'CONTACT_SENSOR', 'HUMIDITY_SENSOR', 'LEAK_SENSOR', 'LIGHT_SENSOR', 'MOTION_SENSOR', 'OCCUPANCY_SENSOR', 'SMOKE_SENSOR', 'TEMPERATURE_SENSOR'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup(value = "OPT_CHAR", order = 150, borderColor = "#649639")
    private String statusLowBattery;

    // --- Common Optional Status Fields ---
    /**
     * Indicates if the accessory has been tampered with.
     * Characteristic: StatusTampered (0 = Not Tampered, 1 = Tampered).
     * Used by (Optional): Security-sensitive devices and sensors.
     * (AIR_QUALITY_SENSOR, CARBON_DIOXIDE_SENSOR, CARBON_MONOXIDE_SENSOR, CONTACT_SENSOR, HUMIDITY_SENSOR, LEAK_SENSOR, LIGHT_SENSOR, MOTION_SENSOR, OCCUPANCY_SENSOR, SMOKE_SENSOR, TEMPERATURE_SENSOR, WINDOW, DOOR, GARAGE_DOOR_OPENER, SECURITY_SYSTEM).
     */
    @UIField(order = 18)
    @UIFieldShowOnCondition("return ['AIR_QUALITY_SENSOR', 'CARBON_DIOXIDE_SENSOR', 'CARBON_MONOXIDE_SENSOR', 'CONTACT_SENSOR', 'HUMIDITY_SENSOR', 'LEAK_SENSOR', 'LIGHT_SENSOR', 'MOTION_SENSOR', 'OCCUPANCY_SENSOR', 'SMOKE_SENSOR', 'TEMPERATURE_SENSOR', 'WINDOW', 'DOOR', 'GARAGE_DOOR_OPENER', 'SECURITY_SYSTEM'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String statusTampered;
    /**
     * Indicates a general fault in the accessory.
     * Characteristic: StatusFault (0 = No Fault, 1 = Fault).
     * Used by (Optional): Many sensors and complex devices.
     * (AIR_QUALITY_SENSOR, CARBON_DIOXIDE_SENSOR, CARBON_MONOXIDE_SENSOR, CONTACT_SENSOR, HUMIDITY_SENSOR, LEAK_SENSOR, LIGHT_SENSOR, MOTION_SENSOR, OCCUPANCY_SENSOR, SMOKE_SENSOR, TEMPERATURE_SENSOR).
     */
    @UIField(order = 19)
    @UIFieldShowOnCondition("return ['AIR_QUALITY_SENSOR', 'CARBON_DIOXIDE_SENSOR', 'CARBON_MONOXIDE_SENSOR', 'CONTACT_SENSOR', 'HUMIDITY_SENSOR', 'LEAK_SENSOR', 'LIGHT_SENSOR', 'MOTION_SENSOR', 'OCCUPANCY_SENSOR', 'SMOKE_SENSOR', 'TEMPERATURE_SENSOR'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String statusFault;
    /**
     * Current state of the air purifier.
     * Characteristic: CurrentAirPurifierState (0 = Inactive, 1 = Idle, 2 = Purifying Air).
     * Used by (Required): AIR_PURIFIER.
     */
    @UIField(order = 20)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_PURIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentAirPurifierState;


    // --- Accessory: AIR_PURIFIER ---
    /**
     * Target state of the air purifier.
     * Characteristic: TargetAirPurifierState (0 = Manual, 1 = Auto).
     * Used by (Required): AIR_PURIFIER.
     */
    @UIField(order = 21)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_PURIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetAirPurifierState;
    /**
     * Swing mode for air purifiers that support oscillation.
     * Characteristic: SwingMode (0 = Disabled, 1 = Enabled).
     * Used by (Optional): AIR_PURIFIER.
     */
    @UIField(order = 22)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_PURIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String airPurifierSwingMode;
    /**
     * Rotation speed for fans or air purifiers.
     * Characteristic: RotationSpeed (percentage 0-100).
     * Used by (Optional): AIR_PURIFIER, FAN.
     */
    @UIField(order = 23)
    @UIFieldShowOnCondition("['AIR_PURIFIER', 'FAN'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String rotationSpeed;
    /**
     * Lock physical controls on the device.
     * Characteristic: LockPhysicalControls (0 = Unlocked, 1 = Locked).
     * Used by (Optional): AIR_PURIFIER, HEATER_COOLER.
     */
    @UIField(order = 24)
    @UIFieldShowOnCondition("['AIR_PURIFIER', 'HEATER_COOLER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String lockPhysicalControls;
    /**
     * Overall air quality level.
     * Characteristic: AirQuality (0 = Unknown, 1 = Excellent, 2 = Good, 3 = Fair, 4 = Inferior, 5 = Poor).
     * Used by (Required): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 25)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String airQuality;


    // --- Accessory: AIR_QUALITY_SENSOR ---
    /**
     * Ozone density reading.
     * Characteristic: OzoneDensity (ppb, 0-1000).
     * Used by (Optional): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 26)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String ozoneDensity;
    /**
     * Nitrogen Dioxide density reading.
     * Characteristic: NitrogenDioxideDensity (ppb, 0-1000).
     * Used by (Optional): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 27)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String nitrogenDioxideDensity;
    /**
     * Sulphur Dioxide density reading.
     * Characteristic: SulphurDioxideDensity (ppb, 0-1000).
     * Used by (Optional): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 28)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String sulphurDioxideDensity;
    /**
     * PM2.5 (Particulate Matter 2.5 micrometers) density reading.
     * Characteristic: PM2_5Density (µg/m³, 0-1000).
     * Used by (Optional): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 29)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String pm25Density;
    /**
     * PM10 (Particulate Matter 10 micrometers) density reading.
     * Characteristic: PM10Density (µg/m³, 0-1000).
     * Used by (Optional): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 30)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String pm10Density;
    /**
     * VOC (Volatile Organic Compounds) density reading.
     * Characteristic: VOCDensity (µg/m³, 0-1000).
     * Used by (Optional): AIR_QUALITY_SENSOR.
     */
    @UIField(order = 31)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String vocDensity;
    /**
     * Current battery level.
     * Characteristic: BatteryLevel (percentage 0-100).
     * Used by (Required): BATTERY. Also see 'statusLowBattery' for generic low battery indication.
     */
    @UIField(order = 32)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'BATTERY'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String batteryLevel;


    // --- Accessory: BATTERY ---
    /**
     * Current charging state of the battery.
     * Characteristic: ChargingState (0 = Not Charging, 1 = Charging, 2 = Not Chargeable).
     * Used by (Required): BATTERY.
     */
    @UIField(order = 33)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'BATTERY'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String batteryChargingState;
    /**
     * Current Carbon Dioxide level.
     * Characteristic: CarbonDioxideLevel (ppm, 0-100000).
     * Used by (Optional): CARBON_DIOXIDE_SENSOR. ('detectedState' is the required characteristic).
     */
    @UIField(order = 34)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_DIOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonDioxideLevel;


    // --- Accessory: CARBON_DIOXIDE_SENSOR ---
    /**
     * Peak Carbon Dioxide level detected.
     * Characteristic: CarbonDioxidePeakLevel (ppm, 0-100000).
     * Used by (Optional): CARBON_DIOXIDE_SENSOR.
     */
    @UIField(order = 35)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_DIOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonDioxidePeakLevel;
    /**
     * Current Carbon Monoxide level.
     * Characteristic: CarbonMonoxideLevel (ppm, 0-100).
     * Used by (Optional): CARBON_MONOXIDE_SENSOR. ('detectedState' is the required characteristic).
     */
    @UIField(order = 36)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_MONOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonMonoxideLevel;


    // --- Accessory: CARBON_MONOXIDE_SENSOR ---
    /**
     * Peak Carbon Monoxide level detected.
     * Characteristic: CarbonMonoxidePeakLevel (ppm, 0-100).
     * Used by (Optional): CARBON_MONOXIDE_SENSOR.
     */
    @UIField(order = 37)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_MONOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonMonoxidePeakLevel;
    /**
     * Indicates if an obstruction is detected during movement.
     * Characteristic: ObstructionDetected (true/false or 1/0).
     * Used by (Optional): DOOR, WINDOW, WINDOW_COVERING, GARAGE_DOOR_OPENER.
     */
    @UIField(order = 38)
    @UIFieldShowOnCondition("return ['DOOR', 'WINDOW', 'WINDOW_COVERING', 'GARAGE_DOOR_OPENER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String obstructionDetected;


    // --- Accessory: DOOR / WINDOW / WINDOW_COVERING / GARAGE_DOOR_OPENER ---
    /**
     * Current operational state of the fan, more detailed than just Active.
     * Characteristic: CurrentFanState (0 = Inactive, 1 = Idle, 2 = Blowing Air).
     * Used by (Optional): FAN. 'activeState' is required.
     */
    @UIField(order = 39)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FAN'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentFanState;


    // --- Accessory: FAN (Full) ---
    @UIField(order = 39)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FAN'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String rotationDirection;
    /**
     * Target fan state (Manual/Auto).
     * Characteristic: TargetFanState (0 = Manual, 1 = Auto).
     * Used by (Optional): FAN.
     */
    @UIField(order = 40)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FAN'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetFanState;
    /**
     * Swing mode for fans that support oscillation.
     * Characteristic: SwingMode (0 = Swing Disabled, 1 = Swing Enabled). (Same as AirPurifier's SwingMode).
     * Used by (Optional): FAN.
     */
    @UIField(order = 41)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FAN'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String fanSwingMode;
    /**
     * Indicates if the filter needs to be changed.
     * Characteristic: FilterChangeIndication (0 = OK, 1 = Change).
     * Used by (Required): FILTER_MAINTENANCE. (Optional for AIR_PURIFIER).
     */
    @UIField(order = 42)
    @UIFieldShowOnCondition("['FILTER_MAINTENANCE', 'AIR_PURIFIER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String filterChangeIndication;


    // --- Accessory: FILTER_MAINTENANCE ---
    /**
     * Remaining life of the filter.
     * Characteristic: FilterLifeLevel (percentage 0-100).
     * Used by (Optional): FILTER_MAINTENANCE, AIR_PURIFIER.
     */
    @UIField(order = 43)
    @UIFieldShowOnCondition("['FILTER_MAINTENANCE', 'AIR_PURIFIER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String filterLifeLevel;
    /**
     * Command to reset the filter change indication / life level. Write-only.
     * Characteristic: ResetFilterIndication (Write a value, typically 1, to reset).
     * Used by (Optional): FILTER_MAINTENANCE, AIR_PURIFIER.
     */
    @UIField(order = 44)
    @UIFieldShowOnCondition("['FILTER_MAINTENANCE', 'AIR_PURIFIER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String filterResetIndication;
    /**
     * Current state of the garage door.
     * Characteristic: CurrentDoorState (0 = Open, 1 = Closed, 2 = Opening, 3 = Closing, 4 = Stopped).
     * Used by (Required): GARAGE_DOOR_OPENER.
     */
    @UIField(order = 45)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GARAGE_DOOR_OPENER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentDoorState;


    // --- Accessory: GARAGE_DOOR_OPENER ---
    /**
     * Target state of the garage door.
     * Characteristic: TargetDoorState (0 = Open, 1 = Closed).
     * Used by (Required): GARAGE_DOOR_OPENER.
     */
    @UIField(order = 46)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GARAGE_DOOR_OPENER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetDoorState;
    /**
     * Current state of the garage door's lock mechanism (if present).
     * Characteristic: LockCurrentState (0 = Unsecured, 1 = Secured, 2 = Jammed, 3 = Unknown).
     * Used by (Optional): GARAGE_DOOR_OPENER.
     */
    @UIField(order = 47)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GARAGE_DOOR_OPENER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String lockCurrentStateGarage;
    /**
     * Target state of the garage door's lock mechanism (if present).
     * Characteristic: LockTargetState (0 = Unsecured, 1 = Secured).
     * Used by (Optional): GARAGE_DOOR_OPENER.
     */
    @UIField(order = 48)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GARAGE_DOOR_OPENER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String lockTargetStateGarage;
    /**
     * Current operational state of the heater/cooler. More detailed than 'activeState'.
     * Characteristic: CurrentHeaterCoolerState (0 = Inactive, 1 = Idle, 2 = Heating, 3 = Cooling).
     * Used by (Required): HEATER_COOLER.
     */
    @UIField(order = 49)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HEATER_COOLER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentHeaterCoolerState;


    // --- Accessory: HEATER_COOLER ---
    /**
     * Cooling threshold temperature for HeaterCooler in Auto mode.
     * Characteristic: CoolingThresholdTemperature (Celsius, typically 10-35).
     * Used by (Optional): HEATER_COOLER.
     */
    @UIField(order = 51)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HEATER_COOLER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String coolingThresholdTemperature;
    /**
     * Heating threshold temperature for HeaterCooler in Auto mode.
     * Characteristic: HeatingThresholdTemperature (Celsius, typically 0-25).
     * Used by (Optional): HEATER_COOLER.
     */
    @UIField(order = 52)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HEATER_COOLER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String heatingThresholdTemperature;
    /**
     * Current relative humidity, can be part of a HeaterCooler or HumidifierDehumidifier.
     * Characteristic: CurrentRelativeHumidity (percentage 0-100).
     * Used by (Optional): HEATER_COOLER, HUMIDIFIER_DEHUMIDIFIER.
     */
    @UIField(order = 53)
    @UIFieldShowOnCondition("['HEATER_COOLER', 'HUMIDIFIER_DEHUMIDIFIER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentRelativeHumidity;
    /**
     * Target relative humidity for devices with humidification/dehumidification capabilities.
     * Characteristic: TargetRelativeHumidity (percentage 0-100).
     * Used by (Optional): HEATER_COOLER (if it has humidifier function), HUMIDIFIER_DEHUMIDIFIER.
     */
    @UIField(order = 54)
    @UIFieldShowOnCondition("['HEATER_COOLER', 'HUMIDIFIER_DEHUMIDIFIER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetRelativeHumidity;
    /**
     * Current state of the humidifier/dehumidifier.
     * Characteristic: CurrentHumidifierDehumidifierState (0 = Inactive, 1 = Idle, 2 = Humidifying, 3 = Dehumidifying).
     * Used by (Required): HUMIDIFIER_DEHUMIDIFIER.
     */
    @UIField(order = 55)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HUMIDIFIER_DEHUMIDIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentHumidifierDehumidifierState;


    // --- Accessory: HUMIDIFIER_DEHUMIDIFIER ---
    /**
     * Target state of the humidifier/dehumidifier.
     * Characteristic: TargetHumidifierDehumidifierState (0 = Auto, 1 = Humidify, 2 = Dehumidify).
     * Used by (Required): HUMIDIFIER_DEHUMIDIFIER.
     */
    @UIField(order = 56)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HUMIDIFIER_DEHUMIDIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetHumidifierDehumidifierState;
    /**
     * Relative humidity threshold for dehumidifier activation (when in Auto mode).
     * Characteristic: RelativeHumidityDehumidifierThreshold (percentage 0-100).
     * Used by (Optional): HUMIDIFIER_DEHUMIDIFIER.
     */
    @UIField(order = 57)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HUMIDIFIER_DEHUMIDIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String relativeHumidityDehumidifierThreshold;
    /**
     * Relative humidity threshold for humidifier activation (when in Auto mode).
     * Characteristic: RelativeHumidityHumidifierThreshold (percentage 0-100).
     * Used by (Optional): HUMIDIFIER_DEHUMIDIFIER.
     */
    @UIField(order = 58)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HUMIDIFIER_DEHUMIDIFIER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String relativeHumidityHumidifierThreshold;
    /**
     * Water level in devices that use water.
     * Characteristic: WaterLevel (percentage 0-100).
     * Used by (Optional): HUMIDIFIER_DEHUMIDIFIER, FAUCET, VALVE (e.g., for a tank).
     */
    @UIField(order = 59)
    @UIFieldShowOnCondition("['VALVE', 'FAUCET', 'HUMIDIFIER_DEHUMIDIFIER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String waterLevel;
    /**
     * Current relative humidity. This is the primary characteristic for a dedicated Humidity Sensor.
     * Characteristic: CurrentRelativeHumidity (percentage 0-100).
     * Used by (Required): HUMIDITY_SENSOR.
     */
    @UIField(order = 60)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HUMIDITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String relativeHumidity;


    // --- Accessory: HUMIDITY_SENSOR ---
    /**
     * Program mode for irrigation systems.
     * Characteristic: ProgramMode (0 = No Program Scheduled, 1 = Program Scheduled, 2 = Program Scheduled, Manual Mode).
     * Used by (Required): IRRIGATION_SYSTEM.
     */
    @UIField(order = 61)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'IRRIGATION_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String programMode;


    // --- Accessory: IRRIGATION_SYSTEM ---
    /**
     * Remaining duration for an active irrigation cycle or valve operation.
     * Characteristic: RemainingDuration (seconds). Read-only.
     * Used by (Optional): IRRIGATION_SYSTEM, VALVE.
     */
    @UIField(order = 62)
    @UIFieldShowOnCondition("['IRRIGATION_SYSTEM', 'VALVE'].includes(context.get('accessoryType'))")
    // OH has this for Valve too
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String remainingDuration;
    /**
     * Set duration for a valve's operation.
     * Characteristic: SetDuration (seconds). Writeable.
     * Used by (Optional): IRRIGATION_SYSTEM (for individual zones/valves), VALVE.
     */
    @UIField(order = 63)
    @UIFieldShowOnCondition("['IRRIGATION_SYSTEM', 'VALVE'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String setDuration;
    /**
     * Current ambient light level.
     * Characteristic: CurrentAmbientLightLevel (lux, 0.0001 to 100000).
     * Used by (Required): LIGHT_SENSOR.
     */
    @UIField(order = 64)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHT_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String lightLevel;


    // --- Accessory: LIGHT_SENSOR ---
    /**
     * Brightness of the light.
     * Characteristic: Brightness (percentage 0-100).
     * Used by (Optional): LIGHTBULB. 'onState' is required.
     */
    @UIField(order = 65)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String brightness;


    // --- Accessory: LIGHTBULB ---
    /**
     * Hue of the light color.
     * Characteristic: Hue (degrees 0-360).
     * Used by (Optional): LIGHTBULB (for color lights).
     */
    @UIField(order = 66)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String hue;
    /**
     * Saturation of the light color.
     * Characteristic: Saturation (percentage 0-100).
     * Used by (Optional): LIGHTBULB (for color lights).
     */
    @UIField(order = 67)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String saturation;
    /**
     * Color temperature of the light.
     * Characteristic: ColorTemperature (Mireds, typically 50-400).
     * Used by (Optional): LIGHTBULB (for tunable white lights).
     */
    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String colorTemperature;
    /**
     * Inverts the color temperature scale if the source device uses an inverted scale.
     * This is a local setting, not a HomeKit characteristic.
     * Used by: LIGHTBULB (local logic).
     */
    @UIField(order = 69)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldGroup("OPT_CHAR")
    private boolean colorTemperatureInverted;
    /**
     * Current state of the lock.
     * Characteristic: LockCurrentState (0 = Unsecured, 1 = Secured, 2 = Jammed, 3 = Unknown).
     * Used by (Required): LOCK.
     */
    @UIField(order = 70)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LOCK'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String lockCurrentState;


    // --- Accessory: LOCK ---
    /**
     * Target state of the lock.
     * Characteristic: LockTargetState (0 = Unsecured, 1 = Secured).
     * Used by (Required): LOCK.
     */
    @UIField(order = 71)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LOCK'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String lockTargetState;
    /**
     * Current state of the security system.
     * Characteristic: SecuritySystemCurrentState (0 = Stay Arm, 1 = Away Arm, 2 = Night Arm, 3 = Disarmed, 4 = Alarm Triggered).
     * Used by (Required): SECURITY_SYSTEM.
     */
    @UIField(order = 72)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SECURITY_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String securitySystemCurrentState;


    // --- Accessory: SECURITY_SYSTEM ---
    /**
     * Target state of the security system.
     * Characteristic: SecuritySystemTargetState (0 = Stay Arm, 1 = Away Arm, 2 = Night Arm, 3 = Disarm).
     * Used by (Required): SECURITY_SYSTEM.
     */
    @UIField(order = 73)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SECURITY_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String securitySystemTargetState;
    /**
     * Indicates if the security system has been tampered with. Can use general 'statusTampered' or this specific one.
     * Characteristic: StatusTampered (0 = Not Tampered, 1 = Tampered).
     * Used by (Optional): SECURITY_SYSTEM.
     */
    @UIField(order = 74)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SECURITY_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String statusTamperedSecurity;
    /**
     * Type of alarm for the security system. Read-only.
     * Characteristic: SecuritySystemAlarmType (0 = No Alarm, 1 = Unknown).
     * Used by (Optional): SECURITY_SYSTEM.
     */
    @UIField(order = 75)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SECURITY_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String securitySystemAlarmType;
    /**
     * Current state of the slats (e.g., open/closed). This is for a dedicated Slat service, often part of WindowCovering.
     * Characteristic: CurrentSlatState (0 = Fixed, 1 = Jammed, 2 = Swinging).
     * Used by (Required): SLAT.
     */
    @UIField(order = 76)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SLAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentSlatState;


    // --- Accessory: SLAT ---
    /**
     * Type of slats (horizontal or vertical).
     * Characteristic: SlatType (0 = Horizontal, 1 = Vertical).
     * Used by (Optional): SLAT.
     */
    @UIField(order = 77)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SLAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String slatType;
    /**
     * Current tilt angle of slats (part of Slat service or WindowCovering).
     * Characteristic: CurrentTiltAngle (degrees, -90 to 90).
     * Used by (Optional): SLAT, WINDOW_COVERING (if it has tiltable slats).
     */
    @UIField(order = 78)
    @UIFieldShowOnCondition("['SLAT', 'WINDOW_COVERING'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentTiltAngle;
    /**
     * Target tilt angle of slats (part of Slat service or WindowCovering).
     * Characteristic: TargetTiltAngle (degrees, -90 to 90).
     * Used by (Optional): SLAT, WINDOW_COVERING (if it has tiltable slats).
     */
    @UIField(order = 79)
    @UIFieldShowOnCondition("['SLAT', 'WINDOW_COVERING'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetTiltAngle;
    /**
     * Volume level for audio output.
     * Characteristic: Volume (percentage 0-100).
     * Used by (Optional): SMART_SPEAKER, SPEAKER, TELEVISION_SPEAKER. 'mute' is required.
     */
    @UIField(order = 80)
    @UIFieldShowOnCondition("return ['SMART_SPEAKER', 'SPEAKER', 'TELEVISION_SPEAKER'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String volume;


    // --- Accessory: SMART_SPEAKER / SPEAKER / TELEVISION_SPEAKER ---
    /**
     * Current media state for SmartSpeaker.
     * Characteristic: CurrentMediaState (0=Play, 1=Pause, 2=Stop, 3=Loading, 4=Interrupted - HAP specific values).
     * Used by (Required): SMART_SPEAKER.
     */
    @UIField(order = 81)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SMART_SPEAKER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentMediaState;
    /**
     * Target media state for SmartSpeaker.
     * Characteristic: TargetMediaState (0=Play, 1=Pause, 2=Stop - HAP specific values).
     * Used by (Optional): SMART_SPEAKER.
     */
    @UIField(order = 82)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SMART_SPEAKER'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetMediaState;
    /**
     * Identifier of the currently active input source on the Television.
     * Characteristic: ActiveIdentifier (UInt32, matches Identifier of an InputSource service).
     * Used by (Required): TELEVISION.
     */
    @UIField(order = 83)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String activeIdentifier;


    // --- Accessory: TELEVISION ---
    /**
     * Name of the input source as configured by the user (e.g., "HDMI 1", "Netflix").
     * Characteristic: ConfiguredName (String). This is for the InputSource service linked to Television.
     * This field would typically be on an InputSource entity, but placed here if TV manages input names directly.
     * Used by (Optional): TELEVISION (via linked InputSource services).
     */
    @UIField(order = 84)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'") // Relates to InputSource naming
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String configuredName;
    @UIField(order = 84)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'") // Relates to InputSource naming
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String inputDeviceType;
    @UIField(order = 84)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'") // Relates to InputSource naming
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String inputSourceType;
    /**
     * Sleep/wake discovery mode for the Television.
     * Characteristic: SleepDiscoveryMode (0 = Not Discoverable, 1 = Always Discoverable).
     * Used by (Optional): TELEVISION.
     */
    @UIField(order = 85)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String sleepDiscoveryMode;
    /**
     * Simulates a remote key press for the Television. Write-only.
     * Characteristic: RemoteKey (various enum values like PlayPause, ArrowUp, Select etc.).
     * Used by (Optional): TELEVISION.
     */
    @UIField(order = 86)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String remoteKey;
    /**
     * Selects if a power mode change should trigger OSD display. Write-only.
     * Characteristic: PowerModeSelection (0=Show, 1=Hide).
     * Used by (Optional): TELEVISION.
     */
    @UIField(order = 87)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String powerModeSelection;
    /**
     * Closed captions state for Television.
     * Characteristic: ClosedCaptions (0 = Disabled, 1 = Enabled).
     * Used by (Optional): TELEVISION.
     */
    @UIField(order = 88)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection
    private String closedCaptions;
    /**
     * Type of volume control supported by Television or TelevisionSpeaker.
     * Characteristic: VolumeControlType (0 = None, 1 = Relative, 2 = Absolute, 3 = Relative with Current).
     * Used by (Optional): TELEVISION, TELEVISION_SPEAKER.
     */
    @UIField(order = 89)
    @UIFieldShowOnCondition("['TELEVISION', 'TELEVISION_SPEAKER'].includes(context.get('accessoryType'))")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection
    private String volumeControlType;
    /**
     * Sends volume up/down commands for relative volume control. Write-only.
     * Characteristic: VolumeSelector (0 = Increment, 1 = Decrement).
     * Used by (Optional): TELEVISION, TELEVISION_SPEAKER (if VolumeControlType is Relative).
     */
    @UIField(order = 90)
    @UIFieldShowOnCondition("['TELEVISION', 'TELEVISION_SPEAKER'].includes(context.get('accessoryType'))")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection
    private String volumeSelector;
    @UIField(order = 90)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String pictureMode;
    @UIField(order = 92)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'TELEVISION'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetVisibilityState;
    /**
     * Target temperature for the Thermostat.
     * Characteristic: TargetTemperature (Celsius, typically 10-38).
     * Used by (Required): THERMOSTAT.
     */
    @UIField(order = 91)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'THERMOSTAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetTemperature;


    // --- Accessory: THERMOSTAT ---
    /**
     * Cooling threshold temperature for Thermostat (if it supports separate cooling/heating thresholds).
     * Characteristic: CoolingThresholdTemperature (Celsius, typically 10-35).
     * Used by (Optional): THERMOSTAT.
     */
    @UIField(order = 92)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'THERMOSTAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String coolingThresholdTemperatureThermo;
    /**
     * Heating threshold temperature for Thermostat (if it supports separate cooling/heating thresholds).
     * Characteristic: HeatingThresholdTemperature (Celsius, typically 0-25).
     * Used by (Optional): THERMOSTAT.
     */
    @UIField(order = 93)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'THERMOSTAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String heatingThresholdTemperatureThermo;
    /**
     * Current relative humidity, if the Thermostat includes a humidity sensor.
     * Characteristic: CurrentRelativeHumidity (percentage 0-100).
     * Used by (Optional): THERMOSTAT.
     */
    @UIField(order = 94)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'THERMOSTAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentRelativeHumidityThermo;
    /**
     * Type of valve.
     * Characteristic: ValveType (0 = Generic, 1 = Irrigation, 2 = Shower Head, 3 = Water Faucet).
     * Used by (Required): VALVE.
     */
    @UIField(order = 95)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'VALVE'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String valveType;


    // --- Accessory: VALVE ---
    /**
     * Indicates if the valve is configured and ready for use. Read-only.
     * Characteristic: IsConfigured (0 = Not Configured, 1 = Configured).
     * Used by (Optional): VALVE.
     */
    @UIField(order = 96)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'VALVE'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String isConfigured;
    /**
     * Index for a labeled service, e.g., if there are multiple valves in an irrigation system.
     * Characteristic: ServiceLabelIndex (UInt8).
     * Used by (Optional): VALVE (especially when grouped, like in IrrigationSystem).
     */
    @UIField(order = 97)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'VALVE'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String serviceLabelIndex;
    /**
     * Command to hold the current position of the window covering. Write-only.
     * Characteristic: HoldPosition (Write any value to hold).
     * Used by (Optional): WINDOW_COVERING.
     */
    @UIField(order = 98)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String windowHoldPosition;


    // --- Accessory: WINDOW_COVERING ---
    /**
     * Current horizontal tilt angle of slats on a window covering.
     * Characteristic: CurrentHorizontalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WINDOW_COVERING.
     */
    @UIField(order = 99)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentHorizontalTiltAngle;
    /**
     * Target horizontal tilt angle of slats on a window covering.
     * Characteristic: TargetHorizontalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WINDOW_COVERING.
     */
    @UIField(order = 100)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetHorizontalTiltAngle;
    /**
     * Current vertical tilt angle of slats on a window covering.
     * Characteristic: CurrentVerticalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WINDOW_COVERING.
     */
    @UIField(order = 101)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentVerticalTiltAngle;
    /**
     * Target vertical tilt angle of slats on a window covering.
     * Characteristic: TargetVerticalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WINDOW_COVERING.
     */
    @UIField(order = 102)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String targetVerticalTiltAngle;
    /**
     * Manufacturer of the accessory.
     * Characteristic: Manufacturer (String). Part of Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 110)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup(value = "OPT_INFO", order = 200, borderColor = "#395D96")
    private String manufacturer;


    // --- Generic Optional Info (Accessory Information Service) ---
    /**
     * Model of the accessory.
     * Characteristic: Model (String). Part of Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 111)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_INFO")
    private String model;
    /**
     * Serial number of the accessory.
     * Characteristic: SerialNumber (String). Part of Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 112)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_INFO")
    private String serialNumber;
    /**
     * Firmware revision of the accessory.
     * Characteristic: FirmwareRevision (String). Part of Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 113)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_INFO")
    private String firmwareRevision;
    /**
     * Hardware revision of the accessory.
     * Characteristic: HardwareRevision (String). Part of Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 114)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_INFO")
    private String hardwareRevision;

    public static int calculateId(String name) {
        int id = 629 + name.hashCode();
        if (id < 0) {
            id += Integer.MAX_VALUE;
            if (id < 0) id = Integer.MAX_VALUE;
        }
        if (id < 2) {
            id = 2;
        }
        return id;
    }

    @Override
    public String getEntityID() {
        return String.valueOf(accessoryType.name().hashCode() + name.hashCode());
    }

    @JsonIgnore
    public int getId() {
        if (id == -1) {
            id = accessoryType == DUMMY ? 0 : calculateId(getTitle());
        }
        return id;
    }

    @JsonIgnore
    public HomekitEntity getDevice() {
        return service.getEntity();
    }

    @Override
    public @Nullable String getDefaultName() {
        return "Homekit accessory";
    }

    @Override
    protected long getChildEntityHashCode() {
        return 0;
    }

    @Override
    public @NotNull String getEntityPrefix() {
        return "homekit-ep";
    }

    public Variable getVariable(String variableId) {
        if (variableId == null || variableId.isEmpty()) {
            return null;
        }
        return service.getContext().var().getVariable(variableId);
    }
}