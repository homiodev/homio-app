package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.ContextVar.Variable;
import org.homio.api.entity.device.DeviceSeriesEntity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.UIFieldVariableSelection;
import org.jetbrains.annotations.NotNull;

import static org.homio.api.ContextVar.VariableType.*;


@SuppressWarnings("unused")
@Entity
@Getter
@Setter
public final class HomekitEndpointEntity extends DeviceSeriesEntity<HomekitEntity> {

    private transient int id = -1;

    @Override
    @UIField(order = 1, required = true)
    @UIFieldGroup(value = "GENERAL", order = 1, borderColor = "#9F48B5")
    public String getName() {
        return super.getName();
    }

    @UIField(order = 2, label = "homekitGroup")
    @UIFieldGroup("GENERAL")
    public String getGroup() {
        return getJsonData("group");
    }

    public void setGroup(String value) {
        setJsonData("group", value);
    }

    /**
     * The type of HomeKit accessory this entity represents.
     * Determines which characteristics are available and how the device behaves in HomeKit.
     * Used by: All accessory types (defines the entity's fundamental type).
     */
    @UIField(order = 3, required = true)
    @UIFieldGroup("GENERAL")
    public @NotNull HomekitAccessoryType getAccessoryType() {
        return getJsonDataEnum("at", HomekitAccessoryType.Switch);
    }

    public void setAccessoryType(HomekitAccessoryType value) {
        setJsonDataEnum("at", value);
    }

    /**
     * Represents the active state of an accessory.
     * Characteristic: Active (0 = Inactive, 1 = Active).
     * Used by (Required): AirPurifier, Fan, Faucet, HeaterCooler, HumidifierDehumidifier, IrrigationSystem, Television, Valve.
     */
    @UIField(order = 5, required = true)
    @UIFieldShowOnCondition("return ['AirPurifier', 'Fan', 'Faucet', 'HeaterCooler', 'HumidifierDehumidifier', 'IrrigationSystem', 'Television', 'Valve'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup(value = "REQ_CHAR", order = 100, borderColor = "#8C3265")
    public String getActiveState() {
        return getJsonData("as");
    }

    public void setActiveState(String value) {
        setJsonData("as", value);
    }

    // --- Consolidated / Widely Used Fields ---
    /**
     * Represents the detected state for various binary sensors.
     * Characteristic: Varies by sensor (e.g., ContactSensorState, MotionDetected, SmokeDetected, LeakDetected, OccupancyDetected, CarbonMonoxideDetected, CarbonDioxideDetected).
     * Values are typically 0 (Not Detected) or 1 (Detected).
     * Used by (Required): CarbonDioxideSensor, CarbonMonoxideSensor, ContactSensor, LeakSensor, MotionSensor, OccupancySensor, SmokeSensor.
     */
    @UIField(order = 6, required = true)
    @UIFieldShowOnCondition("return ['CarbonDioxideSensor', 'CarbonMonoxideSensor', 'ContactSensor', 'LeakSensor', 'MotionSensor', 'OccupancySensor', 'SmokeSensor'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getDetectedState() {
        return getJsonData("ds");
    }

    public void setDetectedState(String value) {
        setJsonData("ds", value);
    }

    /**
     * Represents the target position for accessories that can be set to a specific position (e.g., doors, windows, blinds).
     * Characteristic: TargetPosition (0-100%).
     * Used by (Required): Door, GarageDoorOpener, Window, WindowCovering.
     */
    @UIField(order = 7, required = true)
    @UIFieldShowOnCondition("return ['Door', 'GarageDoorOpener', 'Window', 'WindowCovering'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("REQ_CHAR")
    public String getTargetPosition() {
        return getJsonData("tp");
    }

    public void setTargetPosition(String value) {
        setJsonData("tp", value);
    }

    /**
     * Represents the current position of accessories (e.g., doors, windows, blinds).
     * Characteristic: CurrentPosition (0-100%).
     * Used by (Required): Door, GarageDoorOpener, Window, WindowCovering.
     */
    @UIField(order = 8, required = true)
    @UIFieldShowOnCondition("return ['Door', 'GarageDoorOpener', 'Window', 'WindowCovering'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentPosition() {
        return getJsonData("cp");
    }

    public void setCurrentPosition(String value) {
        setJsonData("cp", value);
    }

    /**
     * Represents the state of movement for positional accessories.
     * Characteristic: PositionState (0 = Decreasing, 1 = Increasing, 2 = Stopped).
     * Used by (Required): Door, Window, WindowCovering.
     * (GarageDoorOpener uses CurrentDoorState/TargetDoorState, which implies movement).
     */
    @UIField(order = 9, required = true)
    @UIFieldShowOnCondition("return ['Door', 'Window', 'WindowCovering'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getPositionState() {
        return getJsonData("ps");
    }

    public void setPositionState(String value) {
        setJsonData("ps", value);
    }

    /**
     * Represents the On/Off state for simple switchable devices.
     * Characteristic: On (true/false or 1/0).
     * Used by (Required): Outlet, Switch, LightBulb, BasicFan.
     */
    @UIField(order = 10, required = true)
    @UIFieldShowOnCondition("return ['Outlet', 'Switch', 'LightBulb', 'BasicFan'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getOnState() {
        return getJsonData("os");
    }

    public void setOnState(String value) {
        setJsonData("os", value);
    }

    /**
     * Represents the mute state for audio output devices.
     * Characteristic: Mute (true/false or 1/0).
     * Used by (Required): Microphone, Speaker, SmartSpeaker, TelevisionSpeaker.
     */
    @UIField(order = 11, required = true)
    @UIFieldShowOnCondition("return ['TelevisionSpeaker', 'Speaker', 'Microphone', 'SmartSpeaker'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getMute() {
        return getJsonData("mt");
    }

    public void setMute(String value) {
        setJsonData("mt", value);
    }

    /**
     * Represents a programmable switch event.
     * Characteristic: ProgrammableSwitchEvent (0 = Single Press, 1 = Double Press, 2 = Long Press). This is for stateless switches.
     * Used by (Required): Doorbell, PushButton.
     */
    @UIField(order = 12, required = true)
    @UIFieldShowOnCondition("return ['Doorbell', 'PushButton'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getProgrammableSwitchEvent() {
        return getJsonData("pse");
    }

    public void setProgrammableSwitchEvent(String value) {
        setJsonData("pse", value);
    }

    /**
     * Represents the current temperature reading.
     * Characteristic: CurrentTemperature (Celsius).
     * Used by (Required): HeaterCooler, TemperatureSensor, Thermostat.
     */
    @UIField(order = 13, required = true)
    @UIFieldShowOnCondition("return ['HeaterCooler', 'TemperatureSensor', 'Thermostat'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentTemperature() {
        return getJsonData("ct");
    }

    public void setCurrentTemperature(String value) {
        setJsonData("ct", value);
    }

    /**
     * Represents the target heating/cooling state for climate control devices.
     * Characteristic: TargetHeatingCoolingState (0 = Off, 1 = Heat, 2 = Cool, 3 = Auto).
     * Used by (Required): HeaterCooler, Thermostat.
     */
    @UIField(order = 14, required = true)
    @UIFieldShowOnCondition("return ['HeaterCooler', 'Thermostat'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getTargetHeatingCoolingState() {
        return getJsonData("thcs");
    }

    public void setTargetHeatingCoolingState(String value) {
        setJsonData("thcs", value);
    }

    /**
     * Represents the current heating/cooling mode of a climate control device.
     * Characteristic: CurrentHeatingCoolingState (0 = Off, 1 = Heat, 2 = Cool).
     * Used by (Required): HeaterCooler, Thermostat.
     */
    @UIField(order = 15, required = true)
    @UIFieldShowOnCondition("return ['HeaterCooler', 'Thermostat'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    // Often mapped to 0/1/2, but source var might be boolean for simpler cases
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentHeatingCoolingState() {
        return getJsonData("chcs");
    }

    public void setCurrentHeatingCoolingState(String value) {
        setJsonData("chcs", value);
    }

    /**
     * Indicates whether the accessory is currently in use.
     * Characteristic: InUse (0 = Not In Uses, 1 = In Use).
     * Used by (Required): Valve, Outlet, IrrigationSystem, Faucet.
     */
    @UIField(order = 16, required = true)
    @UIFieldShowOnCondition("return ['Valve', 'Outlet', 'IrrigationSystem', 'Faucet'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getInuseStatus() {
        return getJsonData("ius");
    }

    public void setInuseStatus(String value) {
        setJsonData("ius", value);
    }

    /**
     * Indicates the battery status of the accessory.
     * Characteristic: StatusLowBattery (0 = Normal, 1 = Low).
     * Used by (Optional): Many battery-powered sensors and devices.
     * (AirQualitySensor, Battery, CarbonDioxideSensor, CarbonMonoxideSensor, ContactSensor, HumiditySensor, LeakSensor, LightSensor, MotionSensor, OccupancySensor, SmokeSensor, TemperatureSensor).
     * For Battery accessory type, this is often linked to its primary battery level.
     */
    @UIField(order = 17)
    @UIFieldShowOnCondition("return ['AirQualitySensor', 'Battery', 'CarbonDioxideSensor', 'CarbonMonoxideSensor', 'ContactSensor', 'HumiditySensor', 'LeakSensor', 'LightSensor', 'MotionSensor', 'OccupancySensor', 'SmokeSensor', 'TemperatureSensor'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup(value = "OPT_CHAR", order = 150, borderColor = "#649639")
    public String getStatusLowBattery() {
        return getJsonData("slb");
    }

    public void setStatusLowBattery(String value) {
        setJsonData("slb", value);
    }

    // --- Common Optional Status Fields ---
    /**
     * Indicates if the accessory has been tampered with.
     * Characteristic: StatusTampered (0 = Not Tampered, 1 = Tampered).
     * Used by (Optional): Security-sensitive devices and sensors.
     * (AirQualitySensor, CarbonDioxideSensor, CarbonMonoxideSensor, ContactSensor, HumiditySensor, LeakSensor, LightSensor, MotionSensor, OccupancySensor, SmokeSensor, TemperatureSensor, Window, Door, GarageDoorOpener, SecuritySystem).
     */
    @UIField(order = 18)
    @UIFieldShowOnCondition("return ['AirQualitySensor', 'CarbonDioxideSensor', 'CarbonMonoxideSensor', 'ContactSensor', 'HumiditySensor', 'LeakSensor', 'LightSensor', 'MotionSensor', 'OccupancySensor', 'SmokeSensor', 'TemperatureSensor', 'Window', 'Door', 'GarageDoorOpener', 'SecuritySystem'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getStatusTampered() {
        return getJsonData("st");
    }

    public void setStatusTampered(String value) {
        setJsonData("st", value);
    }

    /**
     * Indicates a general fault in the accessory.
     * Characteristic: StatusFault (0 = No Fault, 1 = Fault).
     * Used by (Optional): Many sensors and complex devices.
     * (AirQualitySensor, CarbonDioxideSensor, CarbonMonoxideSensor, ContactSensor, HumiditySensor, LeakSensor, LightSensor, MotionSensor, OccupancySensor, SmokeSensor, TemperatureSensor).
     */
    @UIField(order = 19)
    @UIFieldShowOnCondition("return ['AirQualitySensor', 'CarbonDioxideSensor', 'CarbonMonoxideSensor', 'ContactSensor', 'HumiditySensor', 'LeakSensor', 'LightSensor', 'MotionSensor', 'OccupancySensor', 'SmokeSensor', 'TemperatureSensor'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getStatusFault() {
        return getJsonData("sf");
    }

    public void setStatusFault(String value) {
        setJsonData("sf", value);
    }

    /**
     * Current state of the air purifier.
     * Characteristic: CurrentAirPurifierState (0 = Inactive, 1 = Idle, 2 = Purifying Air).
     * Used by (Required): AirPurifier.
     */
    @UIField(order = 20, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirPurifier'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentAirPurifierState() {
        return getJsonData("caps");
    }

    public void setCurrentAirPurifierState(String value) {
        setJsonData("caps", value);
    }


    // --- Accessory: AirPurifier ---
    /**
     * Target state of the air purifier.
     * Characteristic: TargetAirPurifierState (0 = Manual, 1 = Auto).
     * Used by (Required): AirPurifier.
     */
    @UIField(order = 21, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirPurifier'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getTargetAirPurifierState() {
        return getJsonData("taps");
    }

    public void setTargetAirPurifierState(String value) {
        setJsonData("taps", value);
    }

    /**
     * Swing mode for air purifiers that support oscillation.
     * Characteristic: SwingMode (0 = Disabled, 1 = Enabled).
     * Used by (Optional): AirPurifier.
     */
    @UIField(order = 22)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirPurifier'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getAirPurifierSwingMode() {
        return getJsonData("apsm");
    }

    public void setAirPurifierSwingMode(String value) {
        setJsonData("apsm", value);
    }

    /**
     * Rotation speed for fans or air purifiers.
     * Characteristic: RotationSpeed (percentage 0-100).
     * Used by (Optional): AirPurifier, Fan.
     */
    @UIField(order = 23)
    @UIFieldShowOnCondition("['AirPurifier', 'Fan'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getRotationSpeed() {
        return getJsonData("rs");
    }

    public void setRotationSpeed(String value) {
        setJsonData("rs", value);
    }

    /**
     * Lock physical controls on the device.
     * Characteristic: LockPhysicalControls (0 = Unlocked, 1 = Locked).
     * Used by (Optional): AirPurifier, HeaterCooler.
     */
    @UIField(order = 24)
    @UIFieldShowOnCondition("['AirPurifier', 'HeaterCooler'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getLockPhysicalControls() {
        return getJsonData("lpc");
    }

    public void setLockPhysicalControls(String value) {
        setJsonData("lpc", value);
    }

    /**
     * Overall air quality level.
     * Characteristic: AirQuality (0 = Unknown, 1 = Excellent, 2 = Good, 3 = Fair, 4 = Inferior, 5 = Poor).
     * Used by (Required): AirQualitySensor.
     */
    @UIField(order = 25, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getAirQuality() {
        return getJsonData("aq");
    }

    public void setAirQuality(String value) {
        setJsonData("aq", value);
    }


    // --- Accessory: AirQualitySensor ---
    /**
     * Ozone density reading.
     * Characteristic: OzoneDensity (ppb, 0-1000).
     * Used by (Optional): AirQualitySensor.
     */
    @UIField(order = 26)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getOzoneDensity() {
        return getJsonData("od");
    }

    public void setOzoneDensity(String value) {
        setJsonData("od", value);
    }

    /**
     * Nitrogen Dioxide density reading.
     * Characteristic: NitrogenDioxideDensity (ppb, 0-1000).
     * Used by (Optional): AirQualitySensor.
     */
    @UIField(order = 27)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getNitrogenDioxideDensity() {
        return getJsonData("ndd");
    }

    public void setNitrogenDioxideDensity(String value) {
        setJsonData("ndd", value);
    }

    /**
     * Sulphur Dioxide density reading.
     * Characteristic: SulphurDioxideDensity (ppb, 0-1000).
     * Used by (Optional): AirQualitySensor.
     */
    @UIField(order = 28)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getSulphurDioxideDensity() {
        return getJsonData("sdd");
    }

    public void setSulphurDioxideDensity(String value) {
        setJsonData("sdd", value);
    }

    /**
     * PM2.5 (Particulate Matter 2.5 micrometers) density reading.
     * Characteristic: PM2_5Density (µg/m³, 0-1000).
     * Used by (Optional): AirQualitySensor.
     */
    @UIField(order = 29)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getPm25Density() {
        return getJsonData("pm25d");
    }

    public void setPm25Density(String value) {
        setJsonData("pm25d", value);
    }

    /**
     * PM10 (Particulate Matter 10 micrometers) density reading.
     * Characteristic: PM10Density (µg/m³, 0-1000).
     * Used by (Optional): AirQualitySensor.
     */
    @UIField(order = 30)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getPm10Density() {
        return getJsonData("pm10d");
    }

    public void setPm10Density(String value) {
        setJsonData("pm10d", value);
    }

    /**
     * VOC (Volatile Organic Compounds) density reading.
     * Characteristic: VOCDensity (µg/m³, 0-1000).
     * Used by (Optional): AirQualitySensor.
     */
    @UIField(order = 31)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AirQualitySensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getVocDensity() {
        return getJsonData("vd");
    }

    public void setVocDensity(String value) {
        setJsonData("vd", value);
    }

    /**
     * Current battery level.
     * Characteristic: BatteryLevel (percentage 0-100).
     * Used by (Required): Battery.
     * Also see 'statusLowBattery' for generic low-battery indication.
     */
    @UIField(order = 32, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Battery'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("REQ_CHAR")
    public String getBatteryLevel() {
        return getJsonData("bl");
    }

    public void setBatteryLevel(String value) {
        setJsonData("bl", value);
    }


    // --- Accessory: Battery ---
    /**
     * Current charging state of the battery.
     * Characteristic: ChargingState (0 = Not Charging, 1 = Charging, 2 = Not Chargeable).
     * Used by (Required): Battery.
     */
    @UIField(order = 33, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Battery'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getBatteryChargingState() {
        return getJsonData("bcs");
    }

    public void setBatteryChargingState(String value) {
        setJsonData("bcs", value);
    }

    /**
     * Current Carbon Dioxide level.
     * Characteristic: CarbonDioxideLevel (ppm, 0-100000).
     * Used by (Optional): CarbonDioxideSensor. ('detectedState' is the required characteristic).
     */
    @UIField(order = 34)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CarbonDioxideSensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCarbonDioxideLevel() {
        return getJsonData("cdl");
    }

    public void setCarbonDioxideLevel(String value) {
        setJsonData("cdl", value);
    }


    // --- Accessory: CarbonDioxideSensor ---
    /**
     * Peak Carbon Dioxide level detected.
     * Characteristic: CarbonDioxidePeakLevel (ppm, 0-100000).
     * Used by (Optional): CarbonDioxideSensor.
     */
    @UIField(order = 35)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CarbonDioxideSensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCarbonDioxidePeakLevel() {
        return getJsonData("cdpl");
    }

    public void setCarbonDioxidePeakLevel(String value) {
        setJsonData("cdpl", value);
    }

    /**
     * Current Carbon Monoxide level.
     * Characteristic: CarbonMonoxideLevel (ppm, 0-100).
     * Used by (Optional): CarbonMonoxideSensor. ('detectedState' is the required characteristic).
     */
    @UIField(order = 36)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CarbonMonoxideSensor'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getCarbonMonoxideLevel() {
        return getJsonData("cml");
    }

    public void setCarbonMonoxideLevel(String value) {
        setJsonData("cml", value);
    }


    // --- Accessory: CarbonMonoxideSensor ---
    /**
     * Peak Carbon Monoxide level detected.
     * Characteristic: CarbonMonoxidePeakLevel (ppm, 0-100).
     * Used by (Optional): CarbonMonoxideSensor.
     */
    @UIField(order = 37)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CarbonMonoxideSensor'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getCarbonMonoxidePeakLevel() {
        return getJsonData("cmpl");
    }

    public void setCarbonMonoxidePeakLevel(String value) {
        setJsonData("cmpl", value);
    }

    /**
     * Indicates if an obstruction is detected during movement.
     * Characteristic: ObstructionDetected (true/false or 1/0).
     * Used by (Optional): Door, Window, WindowCovering, GarageDoorOpener.
     */
    @UIField(order = 38)
    @UIFieldShowOnCondition("return ['Door', 'Window', 'WindowCovering', 'GarageDoorOpener'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getObstructionDetected() {
        return getJsonData("obd");
    }

    public void setObstructionDetected(String value) {
        setJsonData("obd", value);
    }


    // --- Accessory: Door / Window / WindowCovering / GarageDoorOpener ---
    /**
     * Current operational state of the fan, more detailed than just Active.
     * Characteristic: CurrentFanState (0 = Inactive, 1 = Idle, 2 = Blowing Air).
     * Used by (Optional): Fan. 'activeState' is required.
     */
    @UIField(order = 39)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Fan'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCurrentFanState() {
        return getJsonData("cfs");
    }

    public void setCurrentFanState(String value) {
        setJsonData("cfs", value);
    }


    // --- Accessory: Fan (Full) ---
    @UIField(order = 39) // Preserving duplicate order from the original
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Fan'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getRotationDirection() {
        return getJsonData("rd");
    }

    public void setRotationDirection(String value) {
        setJsonData("rd", value);
    }

    /**
     * Target fan state (Manual/Auto).
     * Characteristic: TargetFanState (0 = Manual, 1 = Auto).
     * Used by (Optional): Fan.
     */
    @UIField(order = 40)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Fan'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetFanState() {
        return getJsonData("tfs");
    }

    public void setTargetFanState(String value) {
        setJsonData("tfs", value);
    }

    /**
     * Swing mode for fans that support oscillation.
     * Characteristic: SwingMode (0 = Swing Disabled, 1 = Swing Enabled). (Same as AirPurifier's SwingMode).
     * Used by (Optional): Fan.
     */
    @UIField(order = 41)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Fan'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getFanSwingMode() {
        return getJsonData("fsm");
    }

    public void setFanSwingMode(String value) {
        setJsonData("fsm", value);
    }

    /**
     * Indicates if the filter needs to be changed.
     * Characteristic: FilterChangeIndication (0 = OK, 1 = Change).
     * Used by (Required): FilterMaintenance. (Optional for AirPurifier).
     */
    @UIField(order = 42, required = true)
    @UIFieldShowOnCondition("['FilterMaintenance', 'AirPurifier'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getFilterChangeIndication() {
        return getJsonData("fci");
    }

    public void setFilterChangeIndication(String value) {
        setJsonData("fci", value);
    }


    // --- Accessory: FilterMaintenance ---
    /**
     * Remaining life of the filter.
     * Characteristic: FilterLifeLevel (percentage 0-100).
     * Used by (Optional): FilterMaintenance, AirPurifier.
     */
    @UIField(order = 43)
    @UIFieldShowOnCondition("['FilterMaintenance', 'AirPurifier'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getFilterLifeLevel() {
        return getJsonData("fll");
    }

    public void setFilterLifeLevel(String value) {
        setJsonData("fll", value);
    }

    /**
     * Command to reset the filter change indication / life level. Write-only.
     * Characteristic: ResetFilterIndication (Write a value, typically 1, to reset).
     * Used by (Optional): FilterMaintenance, AirPurifier.
     */
    @UIField(order = 44)
    @UIFieldShowOnCondition("['FilterMaintenance', 'AirPurifier'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getFilterResetIndication() {
        return getJsonData("fri");
    }

    public void setFilterResetIndication(String value) {
        setJsonData("fri", value);
    }

    /**
     * Current state of the garage door.
     * Characteristic: CurrentDoorState (0 = Open, 1 = Closed, 2 = Opening, 3 = Closing, 4 = Stopped).
     * Used by (Required): GarageDoorOpener.
     */
    @UIField(order = 45, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GarageDoorOpener'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentDoorState() {
        return getJsonData("cds");
    }

    public void setCurrentDoorState(String value) {
        setJsonData("cds", value);
    }


    // --- Accessory: GarageDoorOpener ---
    /**
     * Target state of the garage door.
     * Characteristic: TargetDoorState (0 = Open, 1 = Closed).
     * Used by (Required): GarageDoorOpener.
     */
    @UIField(order = 46, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GarageDoorOpener'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getTargetDoorState() {
        return getJsonData("tds");
    }

    public void setTargetDoorState(String value) {
        setJsonData("tds", value);
    }

    /**
     * Current state of the garage door's lock mechanism (if present).
     * Characteristic: LockCurrentState (0 = Unsecured, 1 = Secured, 2 = Jammed, 3 = Unknown).
     * Used by (Optional): GarageDoorOpener.
     */
    @UIField(order = 47)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GarageDoorOpener'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getLockCurrentStateGarage() {
        return getJsonData("lcsg");
    }

    public void setLockCurrentStateGarage(String value) {
        setJsonData("lcsg", value);
    }

    /**
     * Target state of the garage door's lock mechanism (if present).
     * Characteristic: LockTargetState (0 = Unsecured, 1 = Secured).
     * Used by (Optional): GarageDoorOpener.
     */
    @UIField(order = 48)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GarageDoorOpener'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getLockTargetStateGarage() {
        return getJsonData("ltsg");
    }

    public void setLockTargetStateGarage(String value) {
        setJsonData("ltsg", value);
    }

    /**
     * Current operational state of the heater/cooler. More detailed than 'activeState'.
     * Characteristic: CurrentHeaterCoolerState (0 = Inactive, 1 = Idle, 2 = Heating, 3 = Cooling).
     * Used by (Required): HeaterCooler.
     */
    @UIField(order = 49, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HeaterCooler'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentHeaterCoolerState() {
        return getJsonData("chcshc");
    }

    public void setCurrentHeaterCoolerState(String value) {
        setJsonData("chcshc", value);
    }


    // --- Accessory: HeaterCooler ---
    /**
     * Cooling threshold temperature for HeaterCooler in Auto mode.
     * Characteristic: CoolingThresholdTemperature (Celsius, typically 10-35).
     * Used by (Optional): HeaterCooler.
     */
    @UIField(order = 51)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HeaterCooler'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCoolingThresholdTemperature() {
        return getJsonData("ctt");
    }

    public void setCoolingThresholdTemperature(String value) {
        setJsonData("ctt", value);
    }

    /**
     * Heating threshold temperature for HeaterCooler in Auto mode.
     * Characteristic: HeatingThresholdTemperature (Celsius, typically 0-25).
     * Used by (Optional): HeaterCooler.
     */
    @UIField(order = 52)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HeaterCooler'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getHeatingThresholdTemperature() {
        return getJsonData("htt");
    }

    public void setHeatingThresholdTemperature(String value) {
        setJsonData("htt", value);
    }

    /**
     * Current relative humidity can be part of a HeaterCooler or HumidifierDehumidifier.
     * Characteristic: CurrentRelativeHumidity (percentage 0-100).
     * Used by (Optional): HeaterCooler, HumidifierDehumidifier.
     */
    @UIField(order = 53)
    @UIFieldShowOnCondition("['HeaterCooler', 'HumidifierDehumidifier'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getCurrentRelativeHumidity() {
        return getJsonData("crh");
    }

    public void setCurrentRelativeHumidity(String value) {
        setJsonData("crh", value);
    }

    /**
     * Target relative humidity for devices with humidification/dehumidification capabilities.
     * Characteristic: TargetRelativeHumidity (percentage 0-100).
     * Used by (Optional): HeaterCooler (if it has a humidifier function), HumidifierDehumidifier.
     */
    @UIField(order = 54)
    @UIFieldShowOnCondition("['HeaterCooler', 'HumidifierDehumidifier'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetRelativeHumidity() {
        return getJsonData("trh");
    }

    public void setTargetRelativeHumidity(String value) {
        setJsonData("trh", value);
    }

    /**
     * Current state of the humidifier/dehumidifier.
     * Characteristic: CurrentHumidifierDehumidifierState (0 = Inactive, 1 = Idle, 2 = Humidifying, 3 = Dehumidifying).
     * Used by (Required): HumidifierDehumidifier.
     */
    @UIField(order = 55, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HumidifierDehumidifier'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentHumidifierDehumidifierState() {
        return getJsonData("chds");
    }

    public void setCurrentHumidifierDehumidifierState(String value) {
        setJsonData("chds", value);
    }


    // --- Accessory: HumidifierDehumidifier ---
    /**
     * Target state of the humidifier/dehumidifier.
     * Characteristic: TargetHumidifierDehumidifierState (0 = Auto, 1 = Humidify, 2 = Dehumidify).
     * Used by (Required): HumidifierDehumidifier.
     */
    @UIField(order = 56, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HumidifierDehumidifier'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getTargetHumidifierDehumidifierState() {
        return getJsonData("thds");
    }

    public void setTargetHumidifierDehumidifierState(String value) {
        setJsonData("thds", value);
    }

    /**
     * Relative humidity threshold for dehumidifier activation (when in Auto mode).
     * Characteristic: RelativeHumidityDehumidifierThreshold (percentage 0-100).
     * Used by (Optional): HumidifierDehumidifier.
     */
    @UIField(order = 57)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HumidifierDehumidifier'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getRelativeHumidityDehumidifierThreshold() {
        return getJsonData("rhdt");
    }

    public void setRelativeHumidityDehumidifierThreshold(String value) {
        setJsonData("rhdt", value);
    }

    /**
     * Relative humidity threshold for humidifier activation (when in Auto mode).
     * Characteristic: RelativeHumidityHumidifierThreshold (percentage 0-100).
     * Used by (Optional): HumidifierDehumidifier.
     */
    @UIField(order = 58)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HumidifierDehumidifier'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getRelativeHumidityHumidifierThreshold() {
        return getJsonData("rhht");
    }

    public void setRelativeHumidityHumidifierThreshold(String value) {
        setJsonData("rhht", value);
    }

    /**
     * Water level in devices that use water.
     * Characteristic: WaterLevel (percentage 0-100).
     * Used by (Optional): HumidifierDehumidifier, Faucet, Valve (e.g., for a tank).
     */
    @UIField(order = 59)
    @UIFieldShowOnCondition("['Valve', 'Faucet', 'HumidifierDehumidifier'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getWaterLevel() {
        return getJsonData("wl");
    }

    public void setWaterLevel(String value) {
        setJsonData("wl", value);
    }

    /**
     * Current relative humidity. This is the primary characteristic for a dedicated Humidity Sensor.
     * Characteristic: CurrentRelativeHumidity (percentage 0-100).
     * Used by (Required): HumiditySensor.
     */
    @UIField(order = 60, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HumiditySensor'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("REQ_CHAR")
    public String getRelativeHumidity() {
        return getJsonData("rh");
    }

    public void setRelativeHumidity(String value) {
        setJsonData("rh", value);
    }


    // --- Accessory: HumiditySensor ---
    /**
     * Program mode for irrigation systems.
     * Characteristic: ProgramMode (0 = No Program Scheduled, 1 = Program Scheduled, 2 = Program Scheduled, Manual Mode).
     * Used by (Required): IrrigationSystem.
     */
    @UIField(order = 61, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'IrrigationSystem'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getProgramMode() {
        return getJsonData("pm");
    }

    public void setProgramMode(String value) {
        setJsonData("pm", value);
    }


    // --- Accessory: IrrigationSystem ---
    /**
     * Remaining duration for an active irrigation cycle or valve operation.
     * Characteristic: RemainingDuration (seconds). Read-only.
     * Used by (Optional): IrrigationSystem, Valve.
     */
    @UIField(order = 62)
    @UIFieldShowOnCondition("['IrrigationSystem', 'Valve'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getRemainingDuration() {
        return getJsonData("rdur");
    }

    public void setRemainingDuration(String value) {
        setJsonData("rdur", value);
    }

    /**
     * Set the duration for a valve's operation.
     * Characteristic: SetDuration (seconds). Writeable.
     * Used by (Optional): IrrigationSystem (for individual zones/valves), Valve.
     */
    @UIField(order = 63)
    @UIFieldShowOnCondition("['IrrigationSystem', 'Valve'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getSetDuration() {
        return getJsonData("sdur");
    }

    public void setSetDuration(String value) {
        setJsonData("sdur", value);
    }

    /**
     * Current ambient light level.
     * Characteristic: CurrentAmbientLightLevel (lux, 0.0001 to 100000).
     * Used by (Required): LightSensor.
     */
    @UIField(order = 64, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LightSensor'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getLightLevel() {
        return getJsonData("ll");
    }

    public void setLightLevel(String value) {
        setJsonData("ll", value);
    }


    // --- Accessory: LightSensor ---
    /**
     * Brightness of the light.
     * Characteristic: Brightness (percentage 0-100).
     * Used by (Optional): LightBulb. 'onState' is required.
     */
    @UIField(order = 65)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LightBulb'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getBrightness() {
        return getJsonData("br");
    }

    public void setBrightness(String value) {
        setJsonData("br", value);
    }


    // --- Accessory: LightBulb ---
    /**
     * Hue of the light color.
     * Characteristic: Hue (degrees 0-360).
     * Used by (Optional): LightBulb (for color lights).
     */
    @UIField(order = 66)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LightBulb'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    public String getHue() {
        return getJsonData("hu");
    }

    public void setHue(String value) {
        setJsonData("hu", value);
    }

    /**
     * Saturation of the light color.
     * Characteristic: Saturation (percentage 0-100).
     * Used by (Optional): LightBulb (for color lights).
     */
    @UIField(order = 67)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LightBulb'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    public String getSaturation() {
        return getJsonData("sa");
    }

    public void setSaturation(String value) {
        setJsonData("sa", value);
    }

    /**
     * Color temperature of the light.
     * Characteristic: ColorTemperature (Mireds, typically 50-400).
     * Used by (Optional): LightBulb (for tunable white lights).
     */
    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LightBulb'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    public String getColorTemperature() {
        return getJsonData("ctemp");
    }

    public void setColorTemperature(String value) {
        setJsonData("ctemp", value);
    }

    /**
     * Inverts the color temperature scale if the source device uses an inverted scale.
     * This is a local setting, not a HomeKit characteristic.
     * Used by: LightBulb (local logic).
     */
    @UIField(order = 69)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LightBulb'")
    @UIFieldGroup("OPT_CHAR")
    public boolean isColorTemperatureInverted() {
        return getJsonData("cti", false);
    }

    public void setColorTemperatureInverted(boolean value) {
        setJsonData("cti", value);
    }

    /**
     * Current state of the lock.
     * Characteristic: LockCurrentState (0 = Unsecured, 1 = Secured, 2 = Jammed, 3 = Unknown).
     * Used by (Required): Lock.
     */
    @UIField(order = 70, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Lock'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getLockCurrentState() {
        return getJsonData("lcs");
    }

    public void setLockCurrentState(String value) {
        setJsonData("lcs", value);
    }


    // --- Accessory: Lock ---
    /**
     * Target state of the lock.
     * Characteristic: LockTargetState (0 = Unsecured, 1 = Secured).
     * Used by (Required): Lock.
     */
    @UIField(order = 71, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Lock'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("REQ_CHAR")
    public String getLockTargetState() {
        return getJsonData("lts");
    }

    public void setLockTargetState(String value) {
        setJsonData("lts", value);
    }

    /**
     * Current state of the security system.
     * Characteristic: SecuritySystemCurrentState (0 = Stay Arm, 1 = Away Arm, 2 = Night Arm, 3 = Disarmed, 4 = Alarm Triggered).
     * Used by (Required): SecuritySystem.
     */
    @UIField(order = 72, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SecuritySystem'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getSecuritySystemCurrentState() {
        return getJsonData("sscs");
    }

    public void setSecuritySystemCurrentState(String value) {
        setJsonData("sscs", value);
    }


    // --- Accessory: SecuritySystem ---
    /**
     * Target state of the security system.
     * Characteristic: SecuritySystemTargetState (0 = Stay Arm, 1 = Away Arm, 2 = Night Arm, 3 = Disarm).
     * Used by (Required): SecuritySystem.
     */
    @UIField(order = 73, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SecuritySystem'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getSecuritySystemTargetState() {
        return getJsonData("ssts");
    }

    public void setSecuritySystemTargetState(String value) {
        setJsonData("ssts", value);
    }

    /**
     * Indicates if the security system has been tampered with. Can use general 'statusTampered' or this specific one.
     * Characteristic: StatusTampered (0 = Not Tampered, 1 = Tampered).
     * Used by (Optional): SecuritySystem.
     */
    @UIField(order = 74)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SecuritySystem'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getStatusTamperedSecurity() {
        return getJsonData("sts_sec");
    }

    public void setStatusTamperedSecurity(String value) {
        setJsonData("sts_sec", value);
    }

    /**
     * Type of alarm for the security system. Read-only.
     * Characteristic: SecuritySystemAlarmType (0 = No Alarm, 1 = Unknown).
     * Used by (Optional): SecuritySystem.
     */
    @UIField(order = 75)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SecuritySystem'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getSecuritySystemAlarmType() {
        return getJsonData("ssat");
    }

    public void setSecuritySystemAlarmType(String value) {
        setJsonData("ssat", value);
    }

    /**
     * Current state of the slats (e.g., open/closed). This is for a dedicated Slat service, often part of WindowCovering.
     * Characteristic: CurrentSlatState (0 = Fixed, 1 = Jammed, 2 = Swinging).
     * Used by (Required): Slat.
     */
    @UIField(order = 76, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Slat'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentSlatState() {
        return getJsonData("css");
    }

    public void setCurrentSlatState(String value) {
        setJsonData("css", value);
    }


    // --- Accessory: Slat ---
    /**
     * Type of slats (horizontal or vertical).
     * Characteristic: SlatType (0 = Horizontal, 1 = Vertical).
     * Used by (Optional): Slat.
     */
    @UIField(order = 77)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Slat'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getSlatType() {
        return getJsonData("stype");
    }

    public void setSlatType(String value) {
        setJsonData("stype", value);
    }

    /**
     * Current tilt angle of slats (part of Slat service or WindowCovering).
     * Characteristic: CurrentTiltAngle (degrees, -90 to 90).
     * Used by (Optional): Slat, WindowCovering (if it has tiltable slats).
     */
    @UIField(order = 78)
    @UIFieldShowOnCondition("['Slat', 'WindowCovering'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCurrentTiltAngle() {
        return getJsonData("cta");
    }

    public void setCurrentTiltAngle(String value) {
        setJsonData("cta", value);
    }

    /**
     * Target tilt angle of slats (part of Slat service or WindowCovering).
     * Characteristic: TargetTiltAngle (degrees, -90 to 90).
     * Used by (Optional): Slat, WindowCovering (if it has tiltable slats).
     */
    @UIField(order = 79)
    @UIFieldShowOnCondition("['Slat', 'WindowCovering'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetTiltAngle() {
        return getJsonData("tta");
    }

    public void setTargetTiltAngle(String value) {
        setJsonData("tta", value);
    }

    /**
     * Volume level for audio output.
     * Characteristic: Volume (percentage 0-100).
     * Used by (Optional): SmartSpeaker, Speaker, TelevisionSpeaker. 'mute' is required.
     */
    @UIField(order = 80)
    @UIFieldShowOnCondition("return ['SmartSpeaker', 'Speaker', 'TelevisionSpeaker'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getVolume() {
        return getJsonData("vol");
    }

    public void setVolume(String value) {
        setJsonData("vol", value);
    }


    // --- Accessory: SmartSpeaker / Speaker / TelevisionSpeaker ---
    /**
     * Current media state for SmartSpeaker.
     * Characteristic: CurrentMediaState (0=Play, 1=Pause, 2=Stop, 3=Loading, 4=Interrupted – HAP specific values).
     * Used by (Required): SmartSpeaker.
     */
    @UIField(order = 81, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SmartSpeaker'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getCurrentMediaState() {
        return getJsonData("cms");
    }

    public void setCurrentMediaState(String value) {
        setJsonData("cms", value);
    }

    /**
     * Target media state for SmartSpeaker.
     * Characteristic: TargetMediaState (0=Play, 1=Pause, 2=Stop – HAP specific values).
     * Used by (Optional): SmartSpeaker.
     */
    @UIField(order = 82)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SmartSpeaker'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetMediaState() {
        return getJsonData("tms");
    }

    public void setTargetMediaState(String value) {
        setJsonData("tms", value);
    }

    /**
     * Identifier of the currently active input source on the Television.
     * Characteristic: ActiveIdentifier (UInt32, matches Identifier of an InputSource service).
     * Used by (Required): Television.
     */
    @UIField(order = 83, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getActiveIdentifier() {
        return getJsonData("ai");
    }

    public void setActiveIdentifier(String value) {
        setJsonData("ai", value);
    }


    // --- Accessory: Television ---
    /**
     * Name of the input source as configured by the user (e.g., "HDMI 1", "Netflix").
     * Characteristic: ConfiguredName (String). This is for the InputSource service linked to Television.
     * This field would typically be on an InputSource entity, but placed here if TV manages input names directly.
     * Used by (Optional): Television (via linked InputSource services).
     */
    @UIField(order = 84)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'") // Relates to InputSource naming
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    public String getConfiguredName() {
        return getJsonData("cn");
    }

    public void setConfiguredName(String value) {
        setJsonData("cn", value);
    }

    @UIField(order = 84) // Preserving duplicate order
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'") // Relates to InputSource naming
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    public String getInputDeviceType() {
        return getJsonData("idt");
    }

    public void setInputDeviceType(String value) {
        setJsonData("idt", value);
    }

    @UIField(order = 84) // Preserving duplicate order
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'") // Relates to InputSource naming
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    public String getInputSourceType() {
        return getJsonData("ist");
    }

    public void setInputSourceType(String value) {
        setJsonData("ist", value);
    }

    /**
     * Sleep/wake discovery mode for the Television.
     * Characteristic: SleepDiscoveryMode (0 = Not Discoverable, 1 = Always Discoverable).
     * Used by (Optional): Television.
     */
    @UIField(order = 85)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getSleepDiscoveryMode() {
        return getJsonData("sdm");
    }

    public void setSleepDiscoveryMode(String value) {
        setJsonData("sdm", value);
    }

    /**
     * Simulates a remote key press for the Television. Write-only.
     * Characteristic: RemoteKey (various enum values like PlayPause, ArrowUp, Select, etc.).
     * Used by (Optional): Television.
     */
    @UIField(order = 86)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldVariableSelection(varType = Text)
    @UIFieldGroup("OPT_CHAR")
    public String getRemoteKey() {
        return getJsonData("rk");
    }

    public void setRemoteKey(String value) {
        setJsonData("rk", value);
    }

    /**
     * Selects if a power mode change should trigger OSD display. Write-only.
     * Characteristic: PowerModeSelection (0=Show, 1=Hide).
     * Used by (Optional): Television.
     */
    @UIField(order = 87)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getPowerModeSelection() {
        return getJsonData("pmsel");
    }

    public void setPowerModeSelection(String value) {
        setJsonData("pmsel", value);
    }

    /**
     * Closed captions state for Television.
     * Characteristic: ClosedCaptions (0 = Disabled, 1 = Enabled).
     * Used by (Optional): Television.
     */
    @UIField(order = 88)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection(varType = Bool)
    public String getClosedCaptions() {
        return getJsonData("cc");
    }

    public void setClosedCaptions(String value) {
        setJsonData("cc", value);
    }

    /**
     * Type of volume control supported by Television or TelevisionSpeaker.
     * Characteristic: VolumeControlType (0 = None, 1 = Relative, 2 = Absolute, 3 = Relative with Current).
     * Used by (Optional): Television, TelevisionSpeaker.
     */
    @UIField(order = 89)
    @UIFieldShowOnCondition("['Television', 'TelevisionSpeaker'].includes(context.get('accessoryType'))")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection(varType = Float)
    public String getVolumeControlType() {
        return getJsonData("vct");
    }

    public void setVolumeControlType(String value) {
        setJsonData("vct", value);
    }

    /**
     * Sends volume up/down commands for relative volume control. Write-only.
     * Characteristic: VolumeSelector (0 = Increments, 1 = Decrement).
     * Used by (Optional): Television, TelevisionSpeaker (if VolumeControlType is Relative).
     */
    @UIField(order = 90)
    @UIFieldShowOnCondition("['Television', 'TelevisionSpeaker'].includes(context.get('accessoryType'))")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection(varType = Bool)
    public String getVolumeSelector() {
        return getJsonData("vs");
    }

    public void setVolumeSelector(String value) {
        setJsonData("vs", value);
    }

    @UIField(order = 90) // Preserving duplicate order
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldVariableSelection(varType = Text)
    @UIFieldGroup("OPT_CHAR")
    public String getPictureMode() {
        return getJsonData("pmode");
    }

    public void setPictureMode(String value) {
        setJsonData("pmode", value);
    }

    @UIField(order = 92) // Preserving duplicate order
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Television'")
    @UIFieldVariableSelection(varType = Text)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetVisibilityState() {
        return getJsonData("tvs");
    }

    public void setTargetVisibilityState(String value) {
        setJsonData("tvs", value);
    }

    /**
     * Target temperature for the Thermostat.
     * Characteristic: TargetTemperature (Celsius, typically 10-38).
     * Used by (Required): Thermostat.
     */
    @UIField(order = 91, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Thermostat'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getTargetTemperature() {
        return getJsonData("tt");
    }

    public void setTargetTemperature(String value) {
        setJsonData("tt", value);
    }


    // --- Accessory: Thermostat ---
    /**
     * Cooling threshold temperature for Thermostat (if it supports separate cooling/heating thresholds).
     * Characteristic: CoolingThresholdTemperature (Celsius, typically 10-35).
     * Used by (Optional): Thermostat.
     */
    @UIField(order = 92) // Preserving duplicate order
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Thermostat'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCoolingThresholdTemperatureThermo() {
        return getJsonData("cttt");
    }

    public void setCoolingThresholdTemperatureThermo(String value) {
        setJsonData("cttt", value);
    }

    /**
     * Heating threshold temperature for Thermostat (if it supports separate cooling/heating thresholds).
     * Characteristic: HeatingThresholdTemperature (Celsius, typically 0-25).
     * Used by (Optional): Thermostat.
     */
    @UIField(order = 93)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Thermostat'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getHeatingThresholdTemperatureThermo() {
        return getJsonData("httt");
    }

    public void setHeatingThresholdTemperatureThermo(String value) {
        setJsonData("httt", value);
    }

    /**
     * Current relative humidity, if the Thermostat includes a humidity sensor.
     * Characteristic: CurrentRelativeHumidity (percentage 0-100).
     * Used by (Optional): Thermostat.
     */
    @UIField(order = 94)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Thermostat'")
    @UIFieldVariableSelection(varType = Percentage)
    @UIFieldGroup("OPT_CHAR")
    public String getCurrentRelativeHumidityThermo() {
        return getJsonData("crht");
    }

    public void setCurrentRelativeHumidityThermo(String value) {
        setJsonData("crht", value);
    }

    /**
     * Type of valve.
     * Characteristic: ValveType (0 = Generic, 1 = Irrigation, 2 = Shower Head, 3 = Water Faucet).
     * Used by (Required): Valve.
     */
    @UIField(order = 95, required = true)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Valve'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("REQ_CHAR")
    public String getValveType() {
        return getJsonData("vt");
    }

    public void setValveType(String value) {
        setJsonData("vt", value);
    }

    // --- Accessory: Valve ---
    /**
     * Indicates if the valve is configured and ready for use. Read-only.
     * Characteristic: IsConfigured (0 = Not Configured, 1 = Configured).
     * Used by (Optional): Valve.
     */
    @UIField(order = 96)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Valve'")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    public String getIsConfigured() {
        return getJsonData("ic");
    }

    public void setIsConfigured(String value) {
        setJsonData("ic", value);
    }

    /**
     * Index for a labeled service, e.g., if there are multiple valves in an irrigation system.
     * Characteristic: ServiceLabelIndex (UInt8).
     * Used by (Optional): Valve (especially when grouped, like in IrrigationSystem).
     */
    @UIField(order = 97)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'Valve'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getServiceLabelIndex() {
        return getJsonData("sli");
    }

    public void setServiceLabelIndex(String value) {
        setJsonData("sli", value);
    }

    /**
     * Command to hold the current position of the window covering. Write-only.
     * Characteristic: HoldPosition (Write any value to hold).
     * Used by (Optional): WindowCovering.
     */
    @UIField(order = 98)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WindowCovering'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getWindowHoldPosition() {
        return getJsonData("whp");
    }

    public void setWindowHoldPosition(String value) {
        setJsonData("whp", value);
    }


    // --- Accessory: WindowCovering ---
    /**
     * Current horizontal tilt angle of slats on a window covering.
     * Characteristic: CurrentHorizontalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WindowCovering.
     */
    @UIField(order = 99)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WindowCovering'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCurrentHorizontalTiltAngle() {
        return getJsonData("chta");
    }

    public void setCurrentHorizontalTiltAngle(String value) {
        setJsonData("chta", value);
    }

    /**
     * Target horizontal tilt angle of slats on a window covering.
     * Characteristic: TargetHorizontalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WindowCovering.
     */
    @UIField(order = 100)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WindowCovering'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetHorizontalTiltAngle() {
        return getJsonData("thta");
    }

    public void setTargetHorizontalTiltAngle(String value) {
        setJsonData("thta", value);
    }

    /**
     * Current vertical tilt angle of slats on a window covering.
     * Characteristic: CurrentVerticalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WindowCovering.
     */
    @UIField(order = 101)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WindowCovering'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getCurrentVerticalTiltAngle() {
        return getJsonData("cvta");
    }

    public void setCurrentVerticalTiltAngle(String value) {
        setJsonData("cvta", value);
    }

    /**
     * Target vertical tilt angle of slats on a window covering.
     * Characteristic: TargetVerticalTiltAngle (degrees, -90 to 90).
     * Used by (Optional): WindowCovering.
     */
    @UIField(order = 102)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WindowCovering'")
    @UIFieldVariableSelection(varType = Float)
    @UIFieldGroup("OPT_CHAR")
    public String getTargetVerticalTiltAngle() {
        return getJsonData("tvta");
    }

    public void setTargetVerticalTiltAngle(String value) {
        setJsonData("tvta", value);
    }

    /**
     * Manufacturer of the accessory.
     * Characteristic: Manufacturer (String).
     * Part of the Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 110)
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup(value = "CMN_CHAR", order = 200, borderColor = "#395D96")
    public String getManufacturer() {
        return getJsonData("man");
    }

    public void setManufacturer(String value) {
        setJsonData("man", value);
    }


    // --- Generic Optional Info (Accessory Information Service) ---
    /**
     * Model of the accessory.
     * Characteristic: Model (String).
     * Part of the Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 111)
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("CMN_CHAR")
    public String getModel() {
        return getJsonData("mod");
    }

    public void setModel(String value) {
        setJsonData("mod", value);
    }

    /**
     * Serial number of the accessory.
     * Characteristic: SerialNumber (String).
     * Part of the Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 112)
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("CMN_CHAR")
    public String getSerialNumber() {
        return getJsonData("sn", "none");
    }

    public void setSerialNumber(String value) {
        setJsonData("sn", value);
    }

    /**
     * Firmware revision of the accessory.
     * Characteristic: FirmwareRevision (String).
     * Part of the Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 113)
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("CMN_CHAR")
    public String getFirmwareRevision() {
        return getJsonData("fr", "1");
    }

    public void setFirmwareRevision(String value) {
        setJsonData("fr", value);
    }

    /**
     * Hardware revision of the accessory.
     * Characteristic: HardwareRevision (String).
     * Part of the Accessory Information service.
     * Used by (Optional): All accessory types.
     */
    @UIField(order = 114)
    @UIFieldVariableSelection(varType = Text, rawInput = true)
    @UIFieldGroup("CMN_CHAR")
    public String getHardwareRevision() {
        return getJsonData("hr", "none");
    }

    public void setHardwareRevision(String value) {
        setJsonData("hr", value);
    }

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

    @JsonIgnore
    public int getId() {
        if (id == -1) {
            id = calculateId(getTitle());
        }
        return id;
    }

    @Override
    public String getDefaultName() {
        return "Homekit characteristic";
    }

    @Override
    protected String getSeriesPrefix() {
        return "homekit-acs";
    }

    public boolean getEmulateStopState() {
        return getJsonData("emst", false);
    }

    public void setEmulateStopState(boolean value) {
        setJsonData("emst", value);
    }

    public boolean getEmulateStopSameDirection() {
        return getJsonData("emssd", false);
    }

    public void setEmulateStopSameDirection(boolean value) {
        setJsonData("emssd", value);
    }

    public boolean getSendUpDownForExtents() {
        return getJsonData("sudfe", false);
    }

    public void setSendUpDownForExtents(boolean value) {
        setJsonData("sudfe", value);
    }

    public boolean getInverted() {
        return getJsonData("inv", false);
    }

    public void setInverted(boolean value) {
        setJsonData("inv", value);
    }
}