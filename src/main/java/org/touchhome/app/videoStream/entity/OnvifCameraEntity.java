package org.touchhome.app.videoStream.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.touchhome.app.videoStream.handler.BaseCameraHandler;
import org.touchhome.app.videoStream.handler.OnvifCameraHandler;
import org.touchhome.app.videoStream.onvif.util.CameraTypeHandler;
import org.touchhome.app.videoStream.ui.RestartHandlerOnChange;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldPort;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Entity;
import javax.persistence.Transient;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class OnvifCameraEntity extends BaseFFmpegStreamEntity<OnvifCameraEntity> {

    public static final String PREFIX = "onvifcam_";

    @UIField(order = 11)
    @RestartHandlerOnChange
    public OnvifCameraType getCameraType() {
        return getJsonDataEnum("cameraType", OnvifCameraType.onvif);
    }

    public OnvifCameraEntity setCameraType(OnvifCameraType cameraType) {
        return setJsonDataEnum("cameraType", cameraType);
    }

    @Override
    @UIField(order = 12, type = UIFieldType.IpAddress, name = "ip", label = "cameraIpAddress")
    @RestartHandlerOnChange
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @JsonIgnore
    public String getIp() {
        return getIeeeAddress();
    }

    public OnvifCameraEntity setIp(String ip) {
        return setIeeeAddress(ip);
    }

    @UIFieldPort
    @UIField(order = 35)
    @RestartHandlerOnChange
    public int getOnvifPort() {
        return getJsonData("onvifPort", 0);
    }

    public OnvifCameraEntity setOnvifPort(int value) {
        return setJsonData("onvifPort", value);
    }

    @UIField(order = 55, onlyEdit = true)
    @RestartHandlerOnChange
    public int getOnvifMediaProfile() {
        return getJsonData("onvifMediaProfile", 0);
    }

    public void setOnvifMediaProfile(int value) {
        setJsonData("onvifMediaProfile", value);
    }

    @UIField(order = 60, onlyEdit = true)
    @RestartHandlerOnChange
    public int getJpegPollTime() {
        return getJsonData("jpegPollTime", 0);
    }

    public void setJpegPollTime(int value) {
        setJsonData("jpegPollTime", value);
    }

    @UIField(order = 70, onlyEdit = true)
    @RestartHandlerOnChange
    public int getNvrChannel() {
        return getJsonData("nvrChannel", 0);
    }

    public void setNvrChannel(int value) {
        setJsonData("nvrChannel", value);
    }

    @UIField(order = 75, onlyEdit = true)
    @RestartHandlerOnChange
    public String getSnapshotUrl() {
        return getJsonData("snapshotUrl", "");
    }

    public void setSnapshotUrl(String value) {
        setJsonData("snapshotUrl", value);
    }

    @UIField(order = 85, onlyEdit = true)
    @RestartHandlerOnChange
    public String getCustomMotionAlarmUrl() {
        return getJsonData("customMotionAlarmUrl", "");
    }

    public void setCustomMotionAlarmUrl(String value) {
        setJsonData("customMotionAlarmUrl", value);
    }

    @UIField(order = 90, onlyEdit = true)
    @RestartHandlerOnChange
    public String getCustomAudioAlarmUrl() {
        return getJsonData("customAudioAlarmUrl", "");
    }

    public void setCustomAudioAlarmUrl(String value) {
        setJsonData("customAudioAlarmUrl", value);
    }

    @UIField(order = 95, onlyEdit = true)
    @RestartHandlerOnChange
    public String getMjpegUrl() {
        return getJsonData("mjpegUrl", "");
    }

    public void setMjpegUrl(String value) {
        setJsonData("mjpegUrl", value);
    }

    @UIField(order = 100, onlyEdit = true)
    @RestartHandlerOnChange
    public String getFfmpegInput() {
        return getJsonData("ffmpegInput", "");
    }

    public void setFfmpegInput(String value) {
        setJsonData("ffmpegInput", value);
    }

    @UIField(order = 105, onlyEdit = true)
    @RestartHandlerOnChange
    public String getFfmpegInputOptions() {
        return getJsonData("ffmpegInputOptions", "");
    }

    public void setFfmpegInputOptions(String value) {
        setJsonData("ffmpegInputOptions", value);
    }

    @UIField(order = 155, onlyEdit = true)
    public boolean isPtzContinuous() {
        return getJsonData("ptzContinuous", false);
    }

    public void setPtzContinuous(boolean value) {
        setJsonData("ptzContinuous", value);
    }

    @Transient
    @JsonIgnore
    private int port;

    @Transient
    @JsonIgnore
    private String updateImageWhen = "";

    @Transient
    @JsonIgnore
    private CameraTypeHandler cameraTypeHandler;

    @Override
    public BaseCameraHandler createCameraHandler(EntityContext entityContext) {
        return new OnvifCameraHandler(this, entityContext);
    }

    @SneakyThrows
    public CameraTypeHandler getCameraTypeHandler() {
        if (cameraTypeHandler == null) {
            cameraTypeHandler = getCameraType().getCameraHandlerClass().getDeclaredConstructor(OnvifCameraEntity.class).newInstance(this);
        }
        return cameraTypeHandler;
    }

    @Override
    public String toString() {
        return "onvif:" + getIp() + ":" + getOnvifPort();
    }

    @Override
    protected void beforePersist() {
        setCameraType(OnvifCameraType.onvif);
        setUpdateImageWhen("0");
        setSnapshotUrl("ffmpeg");
        setMjpegUrl("ffmpeg");
        setJpegPollTime(1000);
        setOnvifPort(80);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }
}
