package org.homio.addon.homekit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Getter
@RequiredArgsConstructor
public enum HomekitAccessoryType {
    ACCESSORY_GROUP("AccessoryGroup"),
    DUMMY("Dummy"),
    AIR_QUALITY_SENSOR("AirQualitySensor"),
    BASIC_FAN("BasicFan"),
    BATTERY("Battery"),
    CARBON_DIOXIDE_SENSOR("CarbonDioxideSensor"),
    CARBON_MONOXIDE_SENSOR("CarbonMonoxideSensor"),
    CONTACT_SENSOR("ContactSensor"),
    DOOR("Door"),
    DOORBELL("Doorbell"),
    FAN("Fan"),
    FAUCET("Faucet"),
    FILTER_MAINTENANCE("Filter"),
    GARAGE_DOOR_OPENER("GarageDoorOpener"),
    HEATER_COOLER("HeaterCooler"),
    HUMIDITY_SENSOR("HumiditySensor"),
    INPUT_SOURCE("InputSource"),
    IRRIGATION_SYSTEM("IrrigationSystem"),
    LEAK_SENSOR("LeakSensor"),
    LIGHT_SENSOR("LightSensor"),
    LIGHTBULB("Lighting"),
    LOCK("Lock"),
    MICROPHONE("Microphone"),
    MOTION_SENSOR("MotionSensor"),
    OCCUPANCY_SENSOR("OccupancySensor"),
    OUTLET("Outlet"),
    SECURITY_SYSTEM("SecuritySystem"),
    SLAT("Slat"),
    SMART_SPEAKER("SmartSpeaker"),
    SMOKE_SENSOR("SmokeSensor"),
    SPEAKER("Speaker"),
    STATELESS_PROGRAMMABLE_SWITCH("StatelessProgrammableSwitch"),
    SWITCH("Switchable"),
    TELEVISION("Television"),
    TELEVISION_SPEAKER("TelevisionSpeaker"),
    TEMPERATURE_SENSOR("TemperatureSensor"),
    THERMOSTAT("Thermostat"),
    VALVE("Valve"),
    WINDOW("Window"),
    WINDOW_COVERING("WindowCovering");

    private static final Map<String, HomekitAccessoryType> TAG_MAP = new HashMap<>();

    static {
        for (HomekitAccessoryType type : HomekitAccessoryType.values()) {
            TAG_MAP.put(type.tag, type);
        }
    }

    private final String tag;

    public static Optional<HomekitAccessoryType> valueOfTag(String tag) {
        return Optional.ofNullable(TAG_MAP.get(tag));
    }
}
