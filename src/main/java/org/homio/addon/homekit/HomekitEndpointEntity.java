package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.hapjava.characteristics.impl.television.ClosedCaptionsEnum;
import lombok.Getter;
import lombok.Setter;
import org.homio.addon.homekit.accessories.AbstractHomekitAccessoryImpl;
import org.homio.addon.homekit.accessories.HomekitSwitchImpl;
import org.homio.addon.homekit.enums.HomekitAccessoryType;
import org.homio.api.ContextVar;
import org.homio.api.ContextVar.Variable;
import org.homio.api.entity.BaseEntity;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldType;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.selection.UIFieldEntityByClassSelection;
import org.homio.api.ui.field.selection.UIFieldVariableSelection;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static org.homio.api.ContextVar.VariableType.*;

import java.util.HashMap;
import java.util.Map;

import static org.homio.addon.homekit.enums.HomekitAccessoryType.DUMMY;
import static org.homio.addon.homekit.enums.HomekitAccessoryType.SWITCH;

@Getter
@Setter
public class HomekitEndpointEntity extends BaseEntity {

    private transient HomekitService service;
    private transient int id = -1;

    /**
     * List of service implementation for each accessory type.
     **/
    private static final Map<HomekitAccessoryType, Class<? extends AbstractHomekitAccessoryImpl>> SERVICE_IMPL_MAP = new HashMap<>() {
        {
            /*put(ACCESSORY_GROUP, HomekitAccessoryGroupImpl.class);
            put(AIR_QUALITY_SENSOR, HomekitAirQualitySensorImpl.class);
            put(BASIC_FAN, HomekitBasicFanImpl.class);
            put(BATTERY, HomekitBatteryImpl.class);
            put(CARBON_DIOXIDE_SENSOR, HomekitCarbonDioxideSensorImpl.class);
            put(CARBON_MONOXIDE_SENSOR, HomekitCarbonMonoxideSensorImpl.class);
            put(CONTACT_SENSOR, HomekitContactSensorImpl.class);
            put(DOOR, HomekitDoorImpl.class);
            put(DOORBELL, HomekitDoorbellImpl.class);
            put(FAN, HomekitFanImpl.class);
            put(FAUCET, HomekitFaucetImpl.class);
            put(FILTER_MAINTENANCE, HomekitFilterMaintenanceImpl.class);
            put(GARAGE_DOOR_OPENER, HomekitGarageDoorOpenerImpl.class);
            put(HEATER_COOLER, HomekitHeaterCoolerImpl.class);
            put(HUMIDITY_SENSOR, HomekitHumiditySensorImpl.class);
            put(INPUT_SOURCE, HomekitInputSourceImpl.class);
            put(IRRIGATION_SYSTEM, HomekitIrrigationSystemImpl.class);
            put(LEAK_SENSOR, HomekitLeakSensorImpl.class);
            put(LIGHT_SENSOR, HomekitLightSensorImpl.class);
            put(LIGHTBULB, HomekitLightbulbImpl.class);
            put(LOCK, HomekitLockImpl.class);
            put(MICROPHONE, HomekitMicrophoneImpl.class);
            put(MOTION_SENSOR, HomekitMotionSensorImpl.class);
            put(OCCUPANCY_SENSOR, HomekitOccupancySensorImpl.class);
            put(OUTLET, HomekitOutletImpl.class);
            put(SECURITY_SYSTEM, HomekitSecuritySystemImpl.class);
            put(SLAT, HomekitSlatImpl.class);
            put(SMART_SPEAKER, HomekitSmartSpeakerImpl.class);
            put(SMOKE_SENSOR, HomekitSmokeSensorImpl.class);
            put(SPEAKER, HomekitSpeakerImpl.class);
            put(STATELESS_PROGRAMMABLE_SWITCH, HomekitStatelessProgrammableSwitchImpl.class);*/
            put(SWITCH, HomekitSwitchImpl.class);
            /*put(TELEVISION, HomekitTelevisionImpl.class);
            put(TELEVISION_SPEAKER, HomekitTelevisionSpeakerImpl.class);
            put(TEMPERATURE_SENSOR, HomekitTemperatureSensorImpl.class);
            put(THERMOSTAT, HomekitThermostatImpl.class);
            put(VALVE, HomekitValveImpl.class);
            put(WINDOW, HomekitWindowImpl.class);
            put(WINDOW_COVERING, HomekitWindowCoveringImpl.class);*/
        }
    };

    @UIField(order = 1)
    private String name;

    @UIField(order = 1)
    private HomekitAccessoryType accessoryType = HomekitAccessoryType.SWITCH;

    @UIField(order = 10)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'AIR_QUALITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String airQuality;

    @UIField(order = 12)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'BATTERY'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String batteryLevel;

    @UIField(order = 13)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'BATTERY'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String batteryLowStatus;

    @UIField(order = 13)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'BATTERY'")
    @UIFieldVariableSelection
    @UIFieldGroup(value = "OPT_CHAR", order = 12, borderColor = "#FF000F")
    private String batteryChargingState;

    @UIField(order = 14)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_DIOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String carbonDioxideDetectedState;

    @UIField(order = 14)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_DIOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonDioxideLevel;

    @UIField(order = 15)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_DIOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonDioxidePeakLevel;

    @UIField(order = 15)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_MONOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String carbonMonoxideDetectedState;

    @UIField(order = 15)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_MONOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonMonoxideLevel;

    @UIField(order = 15)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CARBON_MONOXIDE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String carbonMonoxidePeakLevel;

    @UIField(order = 16)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'CONTACT_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String contactSensorState;

    @UIField(order = 17)
    @UIFieldShowOnCondition("return ['WINDOW_COVERING', 'WINDOW', 'DOOR'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentPosition;

    @UIField(order = 18)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'DOOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String doorTargetPosition;

    @UIField(order = 19)
    @UIFieldShowOnCondition("return ['WINDOW_COVERING', 'WINDOW', 'DOOR'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String positionState;

    @UIField(order = 23)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FAN'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String currentFanState;

    @UIField(order = 21)
    @UIFieldShowOnCondition("return ['VALVE', 'HEATER_COOLER', 'FAUCET', 'FAN'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String activeStatus;

    @UIField(order = 23)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FILTER_MAINTENANCE'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String filterChangeIndication;

    @UIField(order = 23)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'FILTER_MAINTENANCE'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String filterResetIndication;

    @UIField(order = 24)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GARAGE_DOOR_OPENER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentDoorState;

    @UIField(order = 25)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'GARAGE_DOOR_OPENER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")

    private String targetDoorState;

    @UIField(order = 27)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HEATER_COOLER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentHeaterCoolerState;

    @UIField(order = 28)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HEATER_COOLER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetHeaterCoolerState;

    @UIField(order = 30)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'HUMIDITY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String relativeHumidity;

    @UIField(order = 32)
    @UIFieldShowOnCondition("return ['VALVE', 'OUTLET', 'IRRIGATION_SYSTEM'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String inuseStatus;

    @UIField(order = 33)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'IRRIGATION_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String programMode;

    @UIField(order = 34)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LEAK_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String leakDetectedState;

    @UIField(order = 35)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHT_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String lightLevel;

    @UIField(order = 36)
    @UIFieldShowOnCondition("return ['OUTLET', 'SWITCH', 'LIGHTBULB', 'BASIC_FAN'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String onState;

    @UIField(order = 37)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LOCK'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")

    private String lockCurrentState;

    @UIField(order = 38)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LOCK'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")

    private String lockTargetState;

    @UIField(order = 39)
    @UIFieldShowOnCondition("return ['TELEVISION_SPEAKER', 'SPEAKER', 'MICROPHONE'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String mute;

    @UIField(order = 40)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'MOTION_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String motionDetectedState;

    @UIField(order = 41)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'OCCUPANCY_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String occupancyDetectedState;

    @UIField(order = 44)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SECURITY_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String securitySystemCurrentState;

    @UIField(order = 45)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SECURITY_SYSTEM'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String securitySystemTargetState;

    @UIField(order = 46)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SMART_SPEAKER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentMediaState;

    @UIField(order = 47)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SMART_SPEAKER'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetMediaState;

    @UIField(order = 48)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SMOKE_SENSOR'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String smokeDetectedState;

    @UIField(order = 49)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'SLAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentSlatState;

    @UIField(order = 51)
    @UIFieldShowOnCondition("return ['DOORBELL', 'STATELESS_PROGRAMMABLE_SWITCH'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String programmableSwitchEvent;

    @UIField(order = 53)
    @UIFieldShowOnCondition("return ['TELEVISION', 'IRRIGATION_SYSTEM'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String active;

    @UIField(order = 53)
    @UIFieldShowOnCondition("context.get('accessoryType') == 'TELEVISION'")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection
    private String closedCaptions;

    @UIField(order = 53)
    @UIFieldShowOnCondition("context.get('accessoryType') == 'TELEVISION'")
    @UIFieldGroup("OPT_CHAR")
    @UIFieldVariableSelection
    private String volumeControlType;

    @UIField(order = 55)
    @UIFieldShowOnCondition("return ['HEATER_COOLER', 'TEMPERATURE_SENSOR', 'THERMOSTAT'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String currentTemperature;

    @UIField(order = 56)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'THERMOSTAT'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String targetHeatingCoolingState;

    @UIField(order = 56)
    @UIFieldShowOnCondition("return ['HEATER_COOLER', 'THERMOSTAT'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String thresholdTemperature;

    @UIField(order = 56)
    @UIFieldShowOnCondition("return ['HEATER_COOLER', 'THERMOSTAT'].includes(context.get('accessoryType'))")
    @UIFieldVariableSelection(varType = Bool)
    @UIFieldGroup("OPT_CHAR")
    private String currentHeatingCoolingState;

    @UIField(order = 61)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String windowTargetPosition;

    @UIField(order = 63)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("REQ_CHAR")
    private String windowCoveringTargetPosition;

    @UIField(order = 63)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldVariableSelection
    @UIFieldGroup("OPT_CHAR")
    private String windowHoldPosition;

    @UIField(order = 65)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String firmwareRevision;

    @UIField(order = 66)
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String hardwareRevision;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String currentTiltTiltAngle;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String currentVerticalTiltAngle;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String currentHorizontalTiltAngle;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String targetHorizontalTiltAngle;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'WINDOW_COVERING'")
    @UIFieldEntityByClassSelection(value = Variable.class, rawInput = true)
    @UIFieldGroup("OPT_CHAR")
    private String targetVerticalTiltAngle;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String hue;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String brightness;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldEntityByClassSelection(value = Variable.class)
    @UIFieldGroup("OPT_CHAR")
    private String colorTemperature;

    @UIField(order = 68)
    @UIFieldShowOnCondition("return context.get('accessoryType') == 'LIGHTBULB'")
    @UIFieldGroup("OPT_CHAR")
    private boolean colorTemperatureInverted;

    public AbstractHomekitAccessoryImpl createAccessory(HomekitService service) {
        this.service = service;
        var accessoryImplClass = SERVICE_IMPL_MAP.get(accessoryType);
        return CommonUtils.newInstance(accessoryImplClass, this);
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

    public static int calculateId(String name) {
        // magic number 629 is the legacy from apache HashCodeBuilder (17*37)
        int id = 629 + name.hashCode();
        if (id < 0) {
            id += Integer.MAX_VALUE;
        }
        if (id < 2) {
            id = 2; // 0 and 1 are reserved
        }
        return id;
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
        return service.getContext().var().getVariable(variableId);
    }
}