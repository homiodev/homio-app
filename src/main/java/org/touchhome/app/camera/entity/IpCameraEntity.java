package org.touchhome.app.camera.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;

import javax.persistence.Entity;
import javax.persistence.Transient;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class IpCameraEntity extends BaseCameraEntity<IpCameraEntity> {

    public static final String PREFIX = "ipcam_";

    @UIField(order = 11)
    public CameraType getCameraType() {
        return getJsonDataEnum("cameraType", CameraType.generic);
    }

    public void setCameraType(CameraType cameraType) {
        setJsonDataEnum("cameraType", cameraType);
    }

    @Override
    @UIField(order = 12, type = UIFieldType.IpAddress, name = "ip", label = "cameraIpAddress")
    public String getIeeeAddress() {
        return super.getIeeeAddress();
    }

    @JsonIgnore
    public String getIp() {
        return getIeeeAddress();
    }

    public void setIp(String ip) {
        setIeeeAddress(ip);
    }

    @UIField(order = 35, type = UIFieldType.Port)
    public int getOnvifPort() {
        return getJsonData("onvifPort", 0);
    }

    public void setOnvifPort(int value) {
        setJsonData("onvifPort", value);
    }

    @UIField(order = 40, type = UIFieldType.Port, label = "cameraServerPort")
    public int getServerPort() {
        return getJsonData("serverPort", 0);
    }

    public void setServerPort(int value) {
        setJsonData("serverPort", value);
    }

    @UIField(order = 45, onlyEdit = true, label = "cameraUsername")
    public String getUser() {
        return getJsonData("user", "");
    }

    public void setUser(String value) {
        setJsonData("user", value);
    }

    @UIField(order = 50, type = UIFieldType.Password, onlyEdit = true, label = "cameraPassword")
    public String getPassword() {
        return getJsonData("password", "");
    }

    public void setPassword(String value) {
        setJsonData("password", value);
    }

    @UIField(order = 55, onlyEdit = true)
    public int getOnvifMediaProfile() {
        return getJsonData("onvifMediaProfile", 0);
    }

    public void setOnvifMediaProfile(int value) {
        setJsonData("onvifMediaProfile", value);
    }

    @UIField(order = 60, onlyEdit = true)
    public int getJpegPollTime() {
        return getJsonData("jpegPollTime", 0);
    }

    public void setJpegPollTime(int value) {
        setJsonData("jpegPollTime", value);
    }

    @UIField(order = 70, onlyEdit = true)
    public int getNvrChannel() {
        return getJsonData("nvrChannel", 0);
    }

    public void setNvrChannel(int value) {
        setJsonData("nvrChannel", value);
    }

    @UIField(order = 75, onlyEdit = true)
    public String getSnapshotUrl() {
        return getJsonData("snapshotUrl", "");
    }

    public void setSnapshotUrl(String value) {
        setJsonData("snapshotUrl", value);
    }

    @UIField(order = 80, onlyEdit = true)
    public String getAlarmInputUrl() {
        return getJsonData("alarmInputUrl", "");
    }

    public void setAlarmInputUrl(String value) {
        setJsonData("alarmInputUrl", value);
    }

    @UIField(order = 85, onlyEdit = true)
    public String getCustomMotionAlarmUrl() {
        return getJsonData("customMotionAlarmUrl", "");
    }

    public void setCustomMotionAlarmUrl(String value) {
        setJsonData("customMotionAlarmUrl", value);
    }

    @UIField(order = 90, onlyEdit = true)
    public String getCustomAudioAlarmUrl() {
        return getJsonData("customAudioAlarmUrl", "");
    }

    public void setCustomAudioAlarmUrl(String value) {
        setJsonData("customAudioAlarmUrl", value);
    }

    @UIField(order = 95, onlyEdit = true)
    public String getMjpegUrl() {
        return getJsonData("mjpegUrl", "");
    }

    public void setMjpegUrl(String value) {
        setJsonData("mjpegUrl", value);
    }

    @UIField(order = 100, onlyEdit = true)
    public String getFfmpegInput() {
        return getJsonData("ffmpegInput", "");
    }

    public void setFfmpegInput(String value) {
        setJsonData("ffmpegInput", value);
    }

    @UIField(order = 105, onlyEdit = true)
    public String getFfmpegInputOptions() {
        return getJsonData("ffmpegInputOptions", "");
    }

    public void setFfmpegInputOptions(String value) {
        setJsonData("ffmpegInputOptions", value);
    }

    @UIField(order = 120, onlyEdit = true)
    public String getHlsOutOptions() {
        return getJsonData("hlsOutOptions", "");
    }

    public void setHlsOutOptions(String value) {
        setJsonData("hlsOutOptions", value);
    }

    @UIField(order = 125, onlyEdit = true)
    public String getGifOutOptions() {
        return getJsonData("gifOutOptions", "");
    }

    public void setGifOutOptions(String value) {
        setJsonData("gifOutOptions", value);
    }

    @UIField(order = 130, onlyEdit = true)
    public String getMjpegOptions() {
        return getJsonData("mjpegOptions", "");
    }

    public void setMjpegOptions(String value) {
        setJsonData("mjpegOptions", value);
    }

    @UIField(order = 135, onlyEdit = true)
    public String getSnapshotOptions() {
        return getJsonData("snapshotOptions", "");
    }

    public void setSnapshotOptions(String value) {
        setJsonData("snapshotOptions", value);
    }

    @UIField(order = 140, onlyEdit = true)
    public String getMotionOptions() {
        return getJsonData("motionOptions", "");
    }

    public void setMotionOptions(String value) {
        setJsonData("motionOptions", value);
    }

    @UIField(order = 145, onlyEdit = true)
    public int getGifPreroll() {
        return getJsonData("gifPreroll", 0);
    }

    public void setGifPreroll(int value) {
        setJsonData("gifPreroll", value);
    }

    @UIField(order = 155, onlyEdit = true)
    public boolean isPtzContinuous() {
        return getJsonData("ptzContinuous", false);
    }

    public void setPtzContinuous(boolean value) {
        setJsonData("ptzContinuous", value);
    }

    @UIField(order = 160, onlyEdit = true)
    public String getMp4OutOptions() {
        return getJsonData("mp4OutOptions", "");
    }

    public void setMp4OutOptions(String value) {
        setJsonData("mp4OutOptions", value);
    }

    @Transient
    @JsonIgnore
    public String getIpWhitelist() {
        return "";
    }

    @Transient
    @JsonIgnore
    private int port;

    @Transient
    @JsonIgnore
    private String updateImageWhen;

    public IpCameraEntity() {
        // set defaults
        setStatus(Status.UNKNOWN);
        setCameraType(CameraType.generic);
        setOnvifPort(80);
    }
}
