package org.touchhome.bundle.zigbee.workspace;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.z2m.Z2MEntrypoint;

@Getter
@Component
public class Scratch3ZigBeeSensorsBlocks extends Scratch3ExtensionBlocks {

   /* private final MenuBlock.ServerMenuBlock alarmSensorMenu;
    private final MenuBlock.ServerMenuBlock smokeSensorMenu;
    private final MenuBlock.ServerMenuBlock illuminanceSensorMenu;
    private final MenuBlock.ServerMenuBlock occupancySensorMenu;
    private final MenuBlock.ServerMenuBlock temperatureSensorMenu;
    private final MenuBlock.ServerMenuBlock pressureSensorMenu;
    private final MenuBlock.ServerMenuBlock humiditySensorMenu;
    private final MenuBlock.ServerMenuBlock waterSensorMenu;*/

    public Scratch3ZigBeeSensorsBlocks(EntityContext entityContext, Z2MEntrypoint z2MEntrypoint) {
        super("#8A6854", entityContext, z2MEntrypoint, "sensor");

        // Menu
     /*   this.alarmSensorMenu = menuServer("alarmSensorMenu", ZIGBEE_ALARM_URL, "Alarm Sensor");
        this.smokeSensorMenu = menuServer("smokeSensorMenu", ZIGBEE_CLUSTER_NAME_URL + ZigBeeConverterIasFireIndicator.CLUSTER_NAME,
            "Smoke sensor");
        this.waterSensorMenu = menuServer("waterSensorMenu", ZIGBEE_CLUSTER_NAME_URL + ZigBeeConverterIasWaterSensor.CLUSTER_NAME,
            "Water sensor");
        this.illuminanceSensorMenu = menuServer("illuminanceSensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclIlluminanceMeasurementCluster.CLUSTER_ID,
            "Illuminance Sensor", "-", ZclIlluminanceMeasurementCluster.CLUSTER_ID);
        this.occupancySensorMenu = menuServer("occupancySensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclOccupancySensingCluster.CLUSTER_ID,
            "Occupancy Sensor", "-", ZclOccupancySensingCluster.CLUSTER_ID);

        this.temperatureSensorMenu = menuServer("temperatureSensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclTemperatureMeasurementCluster.CLUSTER_ID,
            "Temperature Sensor", "-", ZclTemperatureMeasurementCluster.CLUSTER_ID);
        this.pressureSensorMenu = menuServer("pressureSensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclPressureMeasurementCluster.CLUSTER_ID,
            "Pressure Sensor", "-", ZclPressureMeasurementCluster.CLUSTER_ID);
        this.humiditySensorMenu = menuServer("humiditySensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclRelativeHumidityMeasurementCluster.CLUSTER_ID,
            "Humidity Sensor", "-", ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);

        // illuminance sensor
        blockTargetReporter(10, "illuminance_value",
            "illuminance [ILLUMINANCE_SENSOR]", this::illuminanceValueEvaluate, Scratch3ZigBeeBlock.class, block -> {
                block.overrideColor("#802F59");
                block.addArgument(ILLUMINANCE_SENSOR, illuminanceSensorMenu);
            });
        // motion sensor
        blockTargetReporter(20, "motion_value",
            "motion detected [OCCUPANCY_SENSOR]", this::motionDetectedEvaluate, Scratch3ZigBeeBlock.class, block -> {
                block.overrideColor("#802F59");
                block.addArgument(OCCUPANCY_SENSOR, occupancySensorMenu);
                block.appendSpace();
            });

        // temperature sensor
        blockTargetReporter(30, "temperature_value",
            "temperature value [TEMPERATURE_SENSOR]", this::temperatureValueEvaluate, Scratch3ZigBeeBlock.class, block -> {
                block.overrideColor("#633582");
                block.addArgument(TEMPERATURE_SENSOR, temperatureSensorMenu);
            });
        // pressure sensor
        blockTargetReporter(50, "pressure_value",
            "pressure value[PRESSURE_SENSOR]", this::pressureValueEvaluate, Scratch3ZigBeeBlock.class, block -> {
                block.overrideColor("#633582");
                block.addArgument(PRESSURE_SENSOR, pressureSensorMenu);
            });

        // humidity sensor
        blockTargetReporter(60, "humidity_value",
            "humidity value [HUMIDITY_SENSOR]", this::humidityValueEvaluate, Scratch3ZigBeeBlock.class, block -> {
                block.overrideColor("#633582");
                block.addArgument(HUMIDITY_SENSOR, humiditySensorMenu);
                block.appendSpace();
            });

        // smoke sensor
        blockTargetReporter(70, "smoke_sensor_value",
            "Smoke sensor [SMOKE_SENSOR]", this::smokeSensorValueEval, Scratch3ZigBeeBlock.class, block ->
                block.addArgument(SMOKE_SENSOR, this.smokeSensorMenu));

        // water sensor
        blockTargetReporter(80, "water_sensor_value",
            "water sensor [WATER_SENSOR]", this::waterSensorValueEval, Scratch3ZigBeeBlock.class, block ->
                block.addArgument(WATER_SENSOR, this.waterSensorMenu));

        blockHat(90, "when_alarm_event_detected",
            "alarm [ALARM_SENSOR] detected", this::whenAlarmEventDetectedHandler, block ->
                block.addArgument(ALARM_SENSOR, this.alarmSensorMenu));*/
    }

    /* private void whenAlarmEventDetectedHandler(WorkspaceBlock workspaceBlock) {
         workspaceBlock.handleNext(next -> {
             String[] keys = workspaceBlock.getMenuValue(ALARM_SENSOR, alarmSensorMenu).split("/");
             ZigBeeDeviceEntity zigBeeDevice = getZigBeeDevice(workspaceBlock, keys[0]);
             String alarmCluster = keys[1];
             BroadcastLock lock = workspaceBlock.getBroadcastLockManager().getOrCreateLock(workspaceBlock);
             entityContext.event().addEventListener(zigBeeDevice.getIeeeAddress() + "_" + ZclIasZoneCluster.CLUSTER_ID, state -> {
                 if (state == OnOffType.ON) {
                     lock.signalAll();
                 }
             });
             workspaceBlock.subscribeToLock(lock, next::handle);
         });
     }

     private State waterSensorValueEval(WorkspaceBlock workspaceBlock) {
         return getEndpointState(workspaceBlock, WATER_SENSOR, waterSensorMenu, ZclIasZoneCluster.CLUSTER_ID, ZigBeeConverterIasWaterSensor.CLUSTER_NAME);
     }

     private State smokeSensorValueEval(WorkspaceBlock workspaceBlock) {
         return getEndpointState(workspaceBlock, SMOKE_SENSOR, smokeSensorMenu, ZclIasZoneCluster.CLUSTER_ID, ZigBeeConverterIasFireIndicator.CLUSTER_NAME);
     }

     private State getEndpointState(WorkspaceBlock workspaceBlock, String key, ServerMenuBlock menuBlock, int clusterId, String clusterName) {
         return fetchState(getZigBeeDevice(workspaceBlock, key, menuBlock).filterEndpoints(clusterId));
     }

     private State motionDetectedEvaluate(WorkspaceBlock workspaceBlock) {
     *//*ScratchDeviceState scratchDeviceState =
        fetchValueFromDevice(workspaceBlock, ZclOccupancySensingCluster.CLUSTER_ID, OCCUPANCY_SENSOR,
            occupancySensorMenu);
    if (scratchDeviceState != null && !scratchDeviceState.isHandled()) {
      scratchDeviceState.setHandled(true);
      return OnOffType.of(true);
    }*//*
        return OnOffType.of(false);
    }

    private State humidityValueEvaluate(WorkspaceBlock workspaceBlock) {
        return getEndpointState(workspaceBlock, HUMIDITY_SENSOR, humiditySensorMenu, ZclRelativeHumidityMeasurementCluster.CLUSTER_ID, null);
    }

    private State pressureValueEvaluate(WorkspaceBlock workspaceBlock) {
        return getEndpointState(workspaceBlock, PRESSURE_SENSOR, pressureSensorMenu, ZclPressureMeasurementCluster.CLUSTER_ID, null);
    }

    private State temperatureValueEvaluate(WorkspaceBlock workspaceBlock) {
        return getEndpointState(workspaceBlock, TEMPERATURE_SENSOR, temperatureSensorMenu, ZclTemperatureMeasurementCluster.CLUSTER_ID, null);
    }

    private State illuminanceValueEvaluate(WorkspaceBlock workspaceBlock) {
        return getEndpointState(workspaceBlock, ILLUMINANCE_SENSOR, illuminanceSensorMenu, ZclIlluminanceMeasurementCluster.CLUSTER_ID, null);
    }*/
    private static final String ALARM_SENSOR = "ALARM_SENSOR";
    private static final String WATER_SENSOR = "WATER_SENSOR";
    private static final String SMOKE_SENSOR = "SMOKE_SENSOR";

    /*private ScratchDeviceState fetchValueFromDevice(WorkspaceBlock workspaceBlock, int clustersId, String sensor,
        MenuBlock.ServerMenuBlock menuBlock) {
      return Scratch3ZigBeeBlocks.fetchValueFromDevice(workspaceBlock, new Integer[]{clustersId}, sensor, menuBlock);
    }*/
    private static final String ILLUMINANCE_SENSOR = "ILLUMINANCE_SENSOR";
    private static final String OCCUPANCY_SENSOR = "OCCUPANCY_SENSOR";
    private static final String TEMPERATURE_SENSOR = "TEMPERATURE_SENSOR";
    private static final String PRESSURE_SENSOR = "PRESSURE_SENSOR";
    private static final String HUMIDITY_SENSOR = "HUMIDITY_SENSOR";
}
