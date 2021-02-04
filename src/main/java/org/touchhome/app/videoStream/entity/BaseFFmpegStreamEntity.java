package org.touchhome.app.videoStream.entity;

import org.touchhome.app.videoStream.ui.RestartHandlerOnChange;
import org.touchhome.app.videoStream.util.FFMPEGDependencyExecutableInstaller;
import org.touchhome.bundle.api.entity.dependency.RequireExecutableDependency;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldType;

@RequireExecutableDependency(name = "ffmpeg", installer = FFMPEGDependencyExecutableInstaller.class)
public abstract class BaseFFmpegStreamEntity<T extends BaseFFmpegStreamEntity> extends BaseVideoCameraEntity<T> {

    @UIField(order = 45, onlyEdit = true, label = "cameraUsername")
    @RestartHandlerOnChange
    public String getUser() {
        return getJsonData("user", "");
    }

    public void setUser(String value) {
        setJsonData("user", value);
    }

    @UIField(order = 50, type = UIFieldType.Password, onlyEdit = true, label = "cameraPassword")
    @RestartHandlerOnChange
    public String getPassword() {
        return getJsonData("password", "");
    }

    public void setPassword(String value) {
        setJsonData("password", value);
    }

    @UIField(order = 80, onlyEdit = true)
    @RestartHandlerOnChange
    public String getAlarmInputUrl() {
        return getJsonData("alarmInputUrl", "");
    }

    public void setAlarmInputUrl(String value) {
        setJsonData("alarmInputUrl", value);
    }

    @UIField(order = 120, onlyEdit = true, advanced = true)
    @RestartHandlerOnChange
    public String getHlsOutOptions() {
        return getJsonData("hlsOutOptions", "");
    }

    public void setHlsOutOptions(String value) {
        setJsonData("hlsOutOptions", value);
    }

    @UIField(order = 125, onlyEdit = true, advanced = true)
    @RestartHandlerOnChange
    public String getGifOutOptions() {
        return getJsonData("gifOutOptions", "");
    }

    public void setGifOutOptions(String value) {
        setJsonData("gifOutOptions", value);
    }

    @UIField(order = 130, onlyEdit = true, advanced = true)
    @RestartHandlerOnChange
    public String getMjpegOptions() {
        return getJsonData("mjpegOptions", "");
    }

    public void setMjpegOptions(String value) {
        setJsonData("mjpegOptions", value);
    }

    @UIField(order = 135, onlyEdit = true)
    @RestartHandlerOnChange
    public String getSnapshotOptions() {
        return getJsonData("snapshotOptions", "");
    }

    public void setSnapshotOptions(String value) {
        setJsonData("snapshotOptions", value);
    }

    @UIField(order = 140, onlyEdit = true, advanced = true)
    @RestartHandlerOnChange
    public String getMotionOptions() {
        return getJsonData("motionOptions", "");
    }

    public void setMotionOptions(String value) {
        setJsonData("motionOptions", value);
    }

    @UIField(order = 145, onlyEdit = true, advanced = true)
    @RestartHandlerOnChange
    public int getGifPreroll() {
        return getJsonData("gifPreroll", 0);
    }

    public void setGifPreroll(int value) {
        setJsonData("gifPreroll", value);
    }

    @UIField(order = 160, onlyEdit = true, advanced = true)
    @RestartHandlerOnChange
    public String getMp4OutOptions() {
        return getJsonData("mp4OutOptions", "");
    }

    public void setMp4OutOptions(String value) {
        setJsonData("mp4OutOptions", value);
    }

    @Override
    protected void beforePersist() {
        setGifPreroll(0);
        setHlsOutOptions("-strict -2 -f lavfi -i aevalsrc=0 -acodec aac -vcodec copy -hls_flags delete_segments -hls_time 2 -hls_list_size 4");
        setMjpegOptions("-q:v 5 -r 2 -vf scale=640:-2 -update 1");
        setSnapshotOptions("-an -vsync vfr -q:v 2 -update 1 -frames:v 1");
        setGifOutOptions("-r 2 -filter_complex scale=-2:360:flags=lanczos,setpts=0.5*PTS,split[o1][o2];[o1]palettegen[p];[o2]fifo[o3];[o3][p]paletteuse");
        setMp4OutOptions("-c:v copy -c:a copy");
    }
}
