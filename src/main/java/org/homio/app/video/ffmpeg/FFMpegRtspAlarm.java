package org.homio.app.video.ffmpeg;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.CameraConstants.AlarmEvent;
import org.homio.addon.camera.entity.BaseCameraEntity;
import org.homio.addon.camera.entity.VideoMotionAlarmProvider;
import org.homio.api.ContextMedia.FFMPEG;
import org.homio.api.ContextMedia.FFMPEGFormat;
import org.homio.api.state.DecimalType;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Log4j2
@Service
@Primary
@RequiredArgsConstructor
public class FFMpegRtspAlarm implements VideoMotionAlarmProvider {

  private final Map<String, MotionContext> motionListeners = new ConcurrentHashMap<>();

  @Override
  public void addMotionAlarmListener(BaseCameraEntity<?, ?> entity, String listener) {
    MotionContext motionContext = motionListeners.computeIfAbsent(entity.getEntityID(), s -> new MotionContext());
    log.info("[{}]: Rtsp alarm add listener: {}", entity.getEntityID(), listener);
    motionContext.listeners.add(listener);
    motionContext.updated(entity);
  }

  @Override
  public void removeMotionAlarmListener(BaseCameraEntity<?, ?> entity, String listener) {
    MotionContext motionContext = motionListeners.get(entity.getEntityID());
    if (motionContext != null) {
      log.info("[{}]: Rtsp alarm remove listener: {}", entity.getEntityID(), listener);
      motionContext.listeners.remove(listener);
      motionContext.updated(entity);
    }
  }

  @Override
  public void suspendMotionAlarmListeners(BaseCameraEntity<?, ?> entity) {
    MotionContext motionContext = motionListeners.get(entity.getEntityID());
    if (motionContext != null) {
      motionContext.suspend();
    }
  }

  @Override
  public void resumeMotionAlarmListeners(BaseCameraEntity<?, ?> entity) {
    MotionContext motionContext = motionListeners.get(entity.getEntityID());
    if (motionContext != null) {
      motionContext.resume();
    }
  }

  @Override
  public void entityUpdated(BaseCameraEntity<?, ?> entity) {
    MotionContext motionContext = motionListeners.get(entity.getEntityID());
    if (motionContext != null) {
      motionContext.updated(entity);
    }
  }

  @RequiredArgsConstructor
  private static final class MotionContext {

    private final Set<String> listeners = new ConcurrentSkipListSet<>();
    private BaseCameraEntity<?, ?> entity;
    private FFMpegRtspAlarmImpl ffmpegRtspHelper;
    private boolean suspended;
    private int commandHashCode;

    public void updated(BaseCameraEntity<?, ?> entity) {
      this.entity = entity;
      if (listeners.isEmpty() || (this.entity.getAudioThreshold() == 0 && this.entity.getMotionThreshold() == 0)) {
        FFMPEG.run(ffmpegRtspHelper, FFMPEG::stopConverting);
      } else {
        startIfRequires();
      }
    }

    public void suspend() {
      if (!suspended) {
        log.info("[{}]: Rtsp alarm suspend", entity.getEntityID());
        suspended = true;
        FFMPEG.execute(ffmpegRtspHelper, FFMPEG::stopConverting);
      }
    }

    public void resume() {
      if (suspended) {
        log.info("[{}]: Rtsp alarm resume", entity.getEntityID());
        suspended = false;
        startIfRequires();
      }
    }

    private void startIfRequires() {
      // recreate object of command has been changed
      if (buildCommand(entity).hashCode() != commandHashCode) {
        FFMPEG.execute(ffmpegRtspHelper, FFMPEG::stopConverting);
        String command = buildCommand(entity);
        ffmpegRtspHelper = new FFMpegRtspAlarmImpl(entity, command);
        // add destroy handle in case if you need restart
        ffmpegRtspHelper.addDestroyListener("restart-on-failure", () ->
          entity.context().bgp()
            .builder("restart-rtsp-alarm-" + entity.getEntityID())
            .delay(Duration.ofSeconds(10)).execute(this::startIfRequires));
        commandHashCode = ffmpegRtspHelper.getCommandHashCode();
      }
      if (!suspended && !listeners.isEmpty() && !ffmpegRtspHelper.getIsAlive()) {
        ffmpegRtspHelper.startConverting();
      }
    }

    private String buildCommand(BaseCameraEntity<?, ?> entity) {
      List<String> optionsList = new ArrayList<>();
      if (entity.getAudioThreshold() > 0) {
        optionsList.add("-af silencedetect=n=-" + entity.getAudioThreshold() + "dB:d=2");
      } else {
        optionsList.add("-an");
      }
      if (entity.getMotionThreshold() > 0) {
        optionsList.add("-vf select='gte(scene," + (entity.getMotionThreshold() / 100F) + ")',metadata=print");
      } else {
        optionsList.add("-vn");
      }
      optionsList.add("-f null");
      return FFMPEGImpl.buildCommand("",
        entity.getService().getUrls().getRtspUri(), String.join(" ", optionsList), "-",
        entity.getUser(), entity.getPassword().asString());
    }
  }

  private static final class FFMpegRtspAlarmImpl extends FFMPEGImpl {

    private final BaseCameraEntity<?, ?> entity;

    public FFMpegRtspAlarmImpl(BaseCameraEntity<?, ?> entity, String command) {
      super(entity.getEntityID(), "RTSP ALARM", entity.getService(), FFMPEGFormat.RTSP_ALARMS,
        "-", command, entity.context());
      this.entity = entity;
    }

    @Override
    public boolean getIsAlive() {
      return process != null && process.isAlive();
    }

    @Override
    protected @NotNull Thread createFFMPEGThread() {
      return new RtspAlarmFfmpegThread();
    }

    private class RtspAlarmFfmpegThread extends FFMPEGThread {

      public int countOfMotions;

      RtspAlarmFfmpegThread() {
        super();
        setDaemon(true);
        setName("VideoThread_rtsp_alarm_" + entityID);
      }

      @Override
      protected void handleLine(String line) {
        logDebug(line);
        if (line.contains("lavfi.")) {
          // When the number of pixels that change are below the noise floor we need to look
          // across frames to confirm it is motion and not noise.
          DecimalType score = new DecimalType(line.substring(line.indexOf("lavfi.scene_score=") + "lavfi.scene_score=".length()));
          entity.getService().motionDetected(score);
                    /*if (countOfMotions < 10) {// Stop increasing otherwise it takes too long to go OFF
                        countOfMotions++;
                    }
                    if (countOfMotions > 9
                        || countOfMotions > 4 && motionThreshold > 10
                        || countOfMotions > 3 && motionThreshold > 15
                        || countOfMotions > 2 && motionThreshold > 30
                        || countOfMotions > 0 && motionThreshold > 89) {
                        handler.motionDetected(true);
                        if (countOfMotions < 2) {
                            countOfMotions = 4;// Used to debounce the Alarm.
                        }
                    }*/
        } else if (line.contains("speed=")) {
          entity.getService().motionDetected(null);
                    /*if (countOfMotions > 0) {
                        if (motionThreshold > 89) {
                            countOfMotions--;
                        }
                        if (motionThreshold > 10) {
                            countOfMotions -= 2;
                        } else {
                            countOfMotions -= 4;
                        }
                        if (countOfMotions <= 0) {
                            entity.getService().motionDetected(null);
                            countOfMotions = 0;
                        }
                    }*/
        } else if (line.contains("silence_start")) {
          entity.getService().alarmDetected(false, AlarmEvent.AudioAlarm);
        } else if (line.contains("silence_end")) {
          entity.getService().alarmDetected(true, AlarmEvent.AudioAlarm);
        }
      }
    }
  }
}
