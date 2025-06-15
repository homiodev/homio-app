package org.homio.addon.homekit.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum HomekitAccessoryType {
    AirQualitySensor,
    BasicFan,
    Battery,
    CarbonDioxideSensor,
    CarbonMonoxideSensor,
    ContactSensor,
    Door,
    Doorbell,
    Fan,
    Faucet,
    Filter,
    GarageDoorOpener,
    HeaterCooler,
    HumiditySensor,
    //InputSource,
    LeakSensor,
    LightSensor,
    LightBulb,
    Lock,
    Microphone,
    MotionSensor,
    OccupancySensor,
    Outlet,
    SecuritySystem,
    Slat,
    SmartSpeaker,
    SmokeSensor,
    Speaker,
    StatelessProgrammableSwitch,
    Switch,
    //Television,
    //TelevisionSpeaker,
    TemperatureSensor,
    Thermostat,
    Valve,
    Window,
    WindowCovering
}