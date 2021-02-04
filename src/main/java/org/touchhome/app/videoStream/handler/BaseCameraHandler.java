package org.touchhome.app.videoStream.handler;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.videoStream.CameraCoordinator;
import org.touchhome.app.videoStream.entity.BaseVideoCameraEntity;
import org.touchhome.app.videoStream.ffmpeg.Ffmpeg;
import org.touchhome.app.videoStream.setting.CameraFFMPEGInstallPathOptions;
import org.touchhome.app.videoStream.setting.CameraFFMPEGOutputSetting;
import org.touchhome.app.videoStream.ui.CameraAction;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.measure.State;
import org.touchhome.bundle.api.model.Status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

@Log4j2
public abstract class BaseCameraHandler<T extends BaseVideoCameraEntity> {

    @Setter
    @Getter
    protected T cameraEntity;
    protected String cameraEntityID;
    protected final EntityContext entityContext;
    protected final CameraCoordinator cameraCoordinator;
    @Getter
    protected final int serverPort;
    @Getter
    protected Path ffmpegOutputPath;
    protected String ffmpegLocation;
    protected Ffmpeg ffmpegSnapshot;

    @Getter
    protected boolean motionDetected = false;
    protected boolean updateImageChannel = false;
    public byte[] currentSnapshot = new byte[]{(byte) 0x00};
    public ReentrantLock lockCurrentSnapshot = new ReentrantLock();
    public boolean ffmpegSnapshotGeneration = false;
    public Double motionThreshold = 0.0016;
    public int audioThreshold = 35;
    public boolean motionAlarmEnabled = false;
    public boolean audioAlarmEnabled = false;

    @Getter
    protected Map<String, State> attributes = new ConcurrentHashMap<>();

    protected boolean isOnline = false; // Used so only 1 error is logged when a network issue occurs.
    private EntityContextBGP.ThreadContext<Void> cameraConnectionJob;

    public BaseCameraHandler(T cameraEntity, EntityContext entityContext) {
        this.cameraEntity = cameraEntity;
        this.cameraEntityID = cameraEntity.getEntityID();

        this.entityContext = entityContext;
        this.cameraCoordinator = entityContext.getBean(CameraCoordinator.class);
        this.serverPort = this.entityContext.getBean(CameraCoordinator.class).findNextServerPort();

        entityContext.setting().listenValueAndGet(CameraFFMPEGOutputSetting.class, "listen-ffmpeg-output-path-" + cameraEntityID,
                path -> {
                    ffmpegOutputPath = path.resolve(cameraEntityID);
                    try {
                        Files.createDirectories(ffmpegOutputPath);
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to create path: " + ffmpegOutputPath);
                    }
                });

        // for custom ffmpeg path
        entityContext.setting().listenValueAndGet(CameraFFMPEGInstallPathOptions.class, "listen-ffmpeg-path-" + cameraEntityID,
                path -> {
                    this.ffmpegLocation = path.toString();
                    this.restart("ffmpeg location changed", null);
                });
    }

    protected abstract void pollingCameraConnection();

    public boolean isStarted() {
        return isOnline;
    }

    public final void initialize(T cameraEntity) {
        if (cameraEntity == null) {
            if (this.cameraEntity == null) {
                throw new RuntimeException("Unable to init camera with id: " + cameraEntityID);
            }
        } else if (!cameraEntity.getEntityID().equals(cameraEntityID)) {
            throw new RuntimeException("Unable to init camera <" + cameraEntity + "> with different id than: " + cameraEntityID);
        } else {
            this.cameraEntity = cameraEntity;
        }
        initialize0();
        cameraConnectionJob = entityContext.bgp().schedule("poll-camera-connection-" + cameraEntityID, 30, TimeUnit.SECONDS,
                this::pollingCameraConnection, true, true);
    }

    protected abstract void initialize0();

    public final void dispose() {
        isOnline = false;
        fireFfmpeg(ffmpegSnapshot, Ffmpeg::stopConverting);
        ffmpegSnapshot = null;
        if (cameraConnectionJob != null) {
            cameraConnectionJob.cancel();
        }
        dispose0();
    }

    protected abstract void dispose0();

    public abstract void updateSnapshot();

    public abstract void recordMp4(String fileName, int secondsToRecord);

    public abstract void recordGif(String fileName, int secondsToRecord);

    protected final void bringCameraOnline() {
        isOnline = true;
        if (cameraConnectionJob != null) {
            updateStatus(Status.ONLINE, null);
            cameraConnectionJob.cancel();
        }
        bringCameraOnline0();
    }

    protected abstract void bringCameraOnline0();

    public final void restart(String reason, T cameraEntity) {
        // will try to reconnect again as camera may be rebooting.
        if (isOnline) { // if already offline dont try reconnecting in 6 seconds, we want 30sec wait.
            updateStatus(Status.OFFLINE, reason);
            dispose();
            initialize(cameraEntity);
        }
    }

    protected void updateStatus(Status status, String message) {
        if (message != null) {
            log.info("Camera update status: <{}>. <{}>", status, message);
        } else {
            log.info("Camera update status: <{}>", status);
        }
        entityContext.updateDeviceStatus(cameraEntity, status, message);
    }

    private List<CameraAction> actions;

    public List<CameraAction> getCameraActions(boolean fetchValues) {
        if (actions == null) {
            actions = CameraAction.assemble(this, this);
            actions.addAll(getAdditionalCameraActions());
        }
        if (fetchValues) {
            for (CameraAction action : actions) {
                action.setValue(action.getGetter().get());
            }
        }
        return actions;
    }

    protected List<CameraAction> getAdditionalCameraActions() {
        return Collections.emptyList();
    }

    public void setAttribute(String channelToUpdate, State valueOf) {
        attributes.put(channelToUpdate, valueOf);
    }

    protected final void fireFfmpeg(Ffmpeg ffmpeg, Consumer<Ffmpeg> handler) {
        if (ffmpeg != null) {
            handler.accept(ffmpeg);
        }
    }

    public void pollImage(boolean on) {
        if (on) {
            ffmpegSnapshotGeneration = true;
            startSnapshot();
        } else {
            fireFfmpeg(ffmpegSnapshot, ffmpeg -> {
                ffmpeg.stopConverting();
                ffmpegSnapshotGeneration = false;
            });
        }
        updateImageChannel = false;
    }

    public abstract void startSnapshot();
}
