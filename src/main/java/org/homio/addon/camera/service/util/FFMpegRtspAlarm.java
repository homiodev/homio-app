package org.homio.addon.camera.service.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.camera.entity.BaseVideoEntity;
import org.homio.addon.camera.service.BaseVideoService;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.state.StringType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Log4j2
@RequiredArgsConstructor
public class FFMpegRtspAlarm {

    private final Set<String> motionAlarmObservers = new HashSet<>();
    private FFMPEG ffmpegRtspHelper = null;
    private int motionThreshold;
    private int audioThreshold;
    private final EntityContext entityContext;
    private final BaseVideoEntity videoStreamEntity;

    public void addMotionAlarmListener(String listener) {
        motionAlarmObservers.add(listener);
        runFFMPEGRtspAlarmThread();
    }

    public void removeMotionAlarmListener(String listener) {
        motionAlarmObservers.remove(listener);
        if (motionAlarmObservers.isEmpty()) {
            stop();
        }
    }

    public void stop() {
        FFMPEG.run(ffmpegRtspHelper, FFMPEG::stopConverting);
    }

    private void runFFMPEGRtspAlarmThread() {
        BaseVideoService service = videoStreamEntity.getService();
        String inputOptions = service.getFFMPEGInputOptions();

        if (ffmpegRtspHelper != null) {
            // stop stream if threshold - 0
            if (videoStreamEntity.getAudioThreshold() == 0 && videoStreamEntity.getMotionThreshold() == 0) {
                ffmpegRtspHelper.stopConverting();
                return;
            }
            // if values that involved in precious run same as new - just skip restarting
            if (ffmpegRtspHelper.getIsAlive() && motionThreshold == videoStreamEntity.getMotionThreshold() &&
                    audioThreshold == videoStreamEntity.getAudioThreshold()) {
                return;
            }
            ffmpegRtspHelper.stopConverting();
        }
        this.motionThreshold = videoStreamEntity.getMotionThreshold();
        this.audioThreshold = videoStreamEntity.getAudioThreshold();
        String input = defaultIfEmpty(videoStreamEntity.getAlarmInputUrl(), service.urls.getRtspUri());

        List<String> filterOptionsList = new ArrayList<>();
        filterOptionsList.add(this.audioThreshold > 0 ? "-af silencedetect=n=-" + audioThreshold + "dB:d=2" : "-an");
        if (this.motionThreshold > 0) {
            filterOptionsList.addAll(videoStreamEntity.getMotionOptions());
            filterOptionsList.add("-vf select='gte(scene," + (motionThreshold / 100F) + ")',metadata=print");
        } else {
            filterOptionsList.add("-vn");
        }
        ffmpegRtspHelper = entityContext.media().buildFFMPEG(videoStreamEntity.getEntityID(), "FFMPEG rtsp alarm",
                service, FFMPEGFormat.RTSP_ALARMS, inputOptions, input,
                String.join(" ", filterOptionsList), "-f null -",
                videoStreamEntity.getUser(),
                videoStreamEntity.getPassword().asString());
        FFMPEG.run(ffmpegRtspHelper, FFMPEG::startConverting);
        service.setAttribute("FFMPEG_RTSP_ALARM", new StringType(String.join(" ", ffmpegRtspHelper.getCommandArrayList())));
    }
}
