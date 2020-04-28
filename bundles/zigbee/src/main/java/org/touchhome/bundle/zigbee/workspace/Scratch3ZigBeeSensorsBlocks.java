package org.touchhome.bundle.zigbee.workspace;

import com.zsmartsystems.zigbee.zcl.clusters.*;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.DeviceStatus;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.zigbee.ZigBeeCoordinatorHandler;
import org.touchhome.bundle.zigbee.ZigBeeDeviceStateUUID;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterIasFireIndicator;
import org.touchhome.bundle.zigbee.converter.impl.ZigBeeConverterIasWaterSensor;
import org.touchhome.bundle.zigbee.model.OnOffType;
import org.touchhome.bundle.zigbee.model.ZigBeeDeviceEntity;
import org.touchhome.bundle.zigbee.setting.ZigbeeCoordinatorHandlerSetting;
import org.touchhome.bundle.zigbee.setting.ZigbeeStatusSetting;

import static org.touchhome.bundle.zigbee.workspace.Scratch3ZigBeeBlocks.*;

@Getter
@Component
@Scratch3Extension("zigbee-sensor")
public class Scratch3ZigBeeSensorsBlocks extends Scratch3ZigbeeExtensionBlocks {

    private static final String ALARM_SENSOR = "ALARM_SENSOR";
    private static final String WATER_SENSOR = "WATER_SENSOR";
    private static final String SMOKE_SENSOR = "SMOKE_SENSOR";
    private static final String ILLUMINANCE_SENSOR = "ILLUMINANCE_SENSOR";
    private static final String OCCUPANCY_SENSOR = "OCCUPANCY_SENSOR";
    private static final String TEMPERATURE_SENSOR = "TEMPERATURE_SENSOR";
    private static final String PRESSURE_SENSOR = "PRESSURE_SENSOR";
    private static final String HUMIDITY_SENSOR = "HUMIDITY_SENSOR";

    private final MenuBlock.ServerMenuBlock alarmSensorMenu;
    private final MenuBlock.ServerMenuBlock smokeSensorMenu;
    private final MenuBlock.ServerMenuBlock illuminanceSensorMenu;
    private final MenuBlock.ServerMenuBlock occupancySensorMenu;
    private final MenuBlock.ServerMenuBlock temperatureSensorMenu;
    private final MenuBlock.ServerMenuBlock pressureSensorMenu;
    private final MenuBlock.ServerMenuBlock humiditySensorMenu;
    private final MenuBlock.ServerMenuBlock waterSensorMenu;

    private final Scratch3ZigbeeBlock motionDetected;
    private final Scratch3ZigbeeBlock illuminanceValue;

    private final Scratch3Block alarmSensorEvent;

    private final Scratch3ZigbeeBlock waterSensorValue;
    private final Scratch3ZigbeeBlock smokeSensorValue;
    private final Scratch3ZigbeeBlock temperatureValue;
    private final Scratch3ZigbeeBlock pressureValue;
    private final Scratch3ZigbeeBlock humidityValue;
    private final BroadcastLockManager broadcastLockManager;
    private final ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener;
    private ZigBeeCoordinatorHandler coordinatorHandler;

    public Scratch3ZigBeeSensorsBlocks(EntityContext entityContext, BroadcastLockManager broadcastLockManager,
                                       ZigBeeDeviceUpdateValueListener zigBeeDeviceUpdateValueListener) {
        super("#8a6854", entityContext);
        setName("Zigbee Sensors");
        this.broadcastLockManager = broadcastLockManager;
        this.zigBeeDeviceUpdateValueListener = zigBeeDeviceUpdateValueListener;
        this.entityContext.listenSettingValue(ZigbeeStatusSetting.class, status -> {
            if (status == DeviceStatus.ONLINE) {
                this.coordinatorHandler = this.entityContext.getSettingValue(ZigbeeCoordinatorHandlerSetting.class);
            } else {
                this.coordinatorHandler = null;
            }
        });

        // Menu
        this.alarmSensorMenu = MenuBlock.ofServer("alarmSensorMenu", ZIGBEE_ALARM_URL, "Alarm Sensor", "-");
        this.smokeSensorMenu = MenuBlock.ofServer("smokeSensorMenu", ZIGBEE_CLUSTER_NAME_URL + ZigBeeConverterIasFireIndicator.CLUSTER_NAME, "Smoke sensor", "-");
        this.waterSensorMenu = MenuBlock.ofServer("waterSensorMenu", ZIGBEE_CLUSTER_NAME_URL + ZigBeeConverterIasWaterSensor.CLUSTER_NAME, "Water sensor", "-");
        this.illuminanceSensorMenu = MenuBlock.ofServer("illuminanceSensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclIlluminanceMeasurementCluster.CLUSTER_ID, "Illuminance Sensor", "-", ZclIlluminanceMeasurementCluster.CLUSTER_ID);
        this.occupancySensorMenu = MenuBlock.ofServer("occupancySensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclOccupancySensingCluster.CLUSTER_ID, "Occupancy Sensor", "-", ZclOccupancySensingCluster.CLUSTER_ID);

        this.temperatureSensorMenu = MenuBlock.ofServer("temperatureSensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclTemperatureMeasurementCluster.CLUSTER_ID, "Temperature Sensor", "-", ZclTemperatureMeasurementCluster.CLUSTER_ID);
        this.pressureSensorMenu = MenuBlock.ofServer("pressureSensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclPressureMeasurementCluster.CLUSTER_ID, "Pressure Sensor", "-", ZclPressureMeasurementCluster.CLUSTER_ID);
        this.humiditySensorMenu = MenuBlock.ofServer("humiditySensorMenu", ZIGBEE_CLUSTER_ID_URL + ZclRelativeHumidityMeasurementCluster.CLUSTER_ID, "Humidity Sensor", "-", ZclRelativeHumidityMeasurementCluster.CLUSTER_ID);

        // illuminance sensor
        this.illuminanceValue = Scratch3Block.ofEvaluate(10, "illuminance_value", BlockType.reporter, "illuminance [ILLUMINANCE_SENSOR]", this::illuminanceValueEvaluate, Scratch3ZigbeeBlock.class);
        this.illuminanceValue.addArgumentServerSelection(ILLUMINANCE_SENSOR, illuminanceSensorMenu);
        this.illuminanceValue.setDefaultLinkFloatHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Illuminance", ILLUMINANCE_SENSOR, illuminanceSensorMenu, ZclIlluminanceMeasurementCluster.CLUSTER_ID,
                null, "zigbee-sensor");
        this.illuminanceValue.overrideColor("#802F59");

        // motion sensor
        this.motionDetected = Scratch3Block.ofEvaluate(20, "motion_value", BlockType.reporter, "motion detected [OCCUPANCY_SENSOR]", this::motionDetectedEvaluate, Scratch3ZigbeeBlock.class);
        this.motionDetected.addArgumentServerSelection(OCCUPANCY_SENSOR, occupancySensorMenu);
        this.motionDetected.setDefaultLinkFloatHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Occupancy", OCCUPANCY_SENSOR, occupancySensorMenu, ZclOccupancySensingCluster.CLUSTER_ID,
                null, "zigbee-sensor");
        this.motionDetected.overrideColor("#802F59");
        this.motionDetected.appendSpace();

        // temperature sensor
        this.temperatureValue = Scratch3Block.ofEvaluate(30, "temperature_value", BlockType.reporter, "temperature value [TEMPERATURE_SENSOR]", this::temperatureValueEvaluate, Scratch3ZigbeeBlock.class);
        this.temperatureValue.addArgumentServerSelection(TEMPERATURE_SENSOR, temperatureSensorMenu);
        this.temperatureValue.setDefaultLinkFloatHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Temperature", TEMPERATURE_SENSOR, temperatureSensorMenu, ZclTemperatureMeasurementCluster.CLUSTER_ID,
                null, "zigbee-sensor");
        this.temperatureValue.overrideColor("#633582");

        // pressure sensor
        this.pressureValue = Scratch3Block.ofEvaluate(50, "pressure_value", BlockType.reporter, "pressure value[PRESSURE_SENSOR]", this::pressureValueEvaluate, Scratch3ZigbeeBlock.class);
        this.pressureValue.addArgumentServerSelection(PRESSURE_SENSOR, pressureSensorMenu);
        this.pressureValue.setDefaultLinkFloatHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Pressure", PRESSURE_SENSOR, pressureSensorMenu, ZclPressureMeasurementCluster.CLUSTER_ID,
                null, "zigbee-sensor");
        this.pressureValue.overrideColor("#633582");

        // humidity sensor
        this.humidityValue = Scratch3Block.ofEvaluate(60, "humidity_value", BlockType.reporter, "humidity value [HUMIDITY_SENSOR]", this::humidityValueEvaluate, Scratch3ZigbeeBlock.class);
        this.humidityValue.addArgumentServerSelection(HUMIDITY_SENSOR, humiditySensorMenu);
        this.humidityValue.setDefaultLinkFloatHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Humidity", HUMIDITY_SENSOR, humiditySensorMenu, ZclRelativeHumidityMeasurementCluster.CLUSTER_ID,
                null, "zigbee-sensor");

        this.humidityValue.overrideColor("#633582");
        this.humidityValue.appendSpace();

        // smoke sensor
        this.smokeSensorValue = Scratch3Block.ofEvaluate(70, "smoke_sensor_value", BlockType.reporter,
                "Smoke sensor [SMOKE_SENSOR]", this::smokeSensorValueEval, Scratch3ZigbeeBlock.class);
        this.smokeSensorValue.addArgumentServerSelection(SMOKE_SENSOR, this.smokeSensorMenu);
        this.smokeSensorValue.setDefaultLinkBooleanHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Smoke sensor", SMOKE_SENSOR, smokeSensorMenu, ZclIasZoneCluster.CLUSTER_ID,
                ZigBeeConverterIasFireIndicator.CLUSTER_NAME, "zigbee-sensor");

        // water sensor
        this.waterSensorValue = Scratch3Block.ofEvaluate(80, "water_sensor_value", BlockType.reporter,
                "water sensor [WATER_SENSOR]", this::waterSensorValueEval, Scratch3ZigbeeBlock.class);
        this.waterSensorValue.addArgumentServerSelection(WATER_SENSOR, this.waterSensorMenu);
        this.waterSensorValue.setDefaultLinkBooleanHandler(entityContext, zigBeeDeviceUpdateValueListener,
                "Water sensor", WATER_SENSOR, waterSensorMenu, ZclIasZoneCluster.CLUSTER_ID,
                ZigBeeConverterIasWaterSensor.CLUSTER_NAME, "zigbee-sensor");

        this.alarmSensorEvent = Scratch3Block.ofHandler(90, "when_alarm_event_detected", BlockType.hat,
                "alarm [ALARM_SENSOR] detected", this::whenAlarmEventDetectedHandler);
        this.alarmSensorEvent.addArgumentServerSelection(ALARM_SENSOR, this.alarmSensorMenu);

        this.postConstruct();
    }

    private void whenAlarmEventDetectedHandler(WorkspaceBlock workspaceBlock) {
        WorkspaceBlock substack = workspaceBlock.getNext();
        if (substack == null) {
            workspaceBlock.logErrorAndThrow("No next block found");
        } else {
            String[] keys = workspaceBlock.getMenuValue(ALARM_SENSOR, alarmSensorMenu, String.class).split("/");
            ZigBeeDeviceEntity zigBeeDevice = getZigBeeDevice(workspaceBlock, keys[0]);
            String alarmCluster = keys[1];
            BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock.getId());
            ZigBeeDeviceStateUUID zigBeeDeviceStateUUID = ZigBeeDeviceStateUUID.require(zigBeeDevice.getIeeeAddress(),
                    ZclIasZoneCluster.CLUSTER_ID, null, alarmCluster);
            this.zigBeeDeviceUpdateValueListener.addListener(zigBeeDeviceStateUUID, deviceState -> {
                if (deviceState.getState() == OnOffType.ON) {
                    lock.signalAll();
                }
            });

            while (!Thread.currentThread().isInterrupted()) {
                if (lock.await(workspaceBlock)) {
                    substack.handle();
                }
            }
        }
    }

    private Float waterSensorValueEval(WorkspaceBlock workspaceBlock) {
        return fetchFloat(Scratch3ZigBeeBlocks.fetchValueFromDevice(zigBeeDeviceUpdateValueListener, workspaceBlock,
                ZclIasZoneCluster.CLUSTER_ID, ZigBeeConverterIasWaterSensor.CLUSTER_NAME, WATER_SENSOR, waterSensorMenu));
    }

    private Float smokeSensorValueEval(WorkspaceBlock workspaceBlock) {
        return fetchFloat(Scratch3ZigBeeBlocks.fetchValueFromDevice(zigBeeDeviceUpdateValueListener, workspaceBlock,
                ZclIasZoneCluster.CLUSTER_ID, ZigBeeConverterIasFireIndicator.CLUSTER_NAME, SMOKE_SENSOR, smokeSensorMenu));
    }

    private ScratchDeviceState fetchValueFromDevice(WorkspaceBlock workspaceBlock, int clustersId, String sensor, MenuBlock menuBlock) {
        return Scratch3ZigBeeBlocks.fetchValueFromDevice(zigBeeDeviceUpdateValueListener, workspaceBlock, new Integer[]{clustersId}, sensor, menuBlock);
    }

    private boolean motionDetectedEvaluate(WorkspaceBlock workspaceBlock) {
        ScratchDeviceState scratchDeviceState = fetchValueFromDevice(workspaceBlock, ZclOccupancySensingCluster.CLUSTER_ID, OCCUPANCY_SENSOR, occupancySensorMenu);
        if (scratchDeviceState != null && !scratchDeviceState.isHandled()) {
            scratchDeviceState.setHandled(true);
            return true;
        }
        return false;
    }

    private float humidityValueEvaluate(WorkspaceBlock workspaceBlock) {
        return fetchFloat(fetchValueFromDevice(workspaceBlock, ZclRelativeHumidityMeasurementCluster.CLUSTER_ID, HUMIDITY_SENSOR, humiditySensorMenu));
    }

    private float pressureValueEvaluate(WorkspaceBlock workspaceBlock) {
        return fetchFloat(fetchValueFromDevice(workspaceBlock, ZclPressureMeasurementCluster.CLUSTER_ID, PRESSURE_SENSOR, pressureSensorMenu));
    }

    private float temperatureValueEvaluate(WorkspaceBlock workspaceBlock) {
        return fetchFloat(fetchValueFromDevice(workspaceBlock, ZclTemperatureMeasurementCluster.CLUSTER_ID, TEMPERATURE_SENSOR, temperatureSensorMenu));
    }

    private int illuminanceValueEvaluate(WorkspaceBlock workspaceBlock) {
        return fetchInt(fetchValueFromDevice(workspaceBlock, ZclIlluminanceMeasurementCluster.CLUSTER_ID, ILLUMINANCE_SENSOR, illuminanceSensorMenu));
    }
}
