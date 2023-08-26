package org.homio.app.manager.common.impl;

import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.EntityContextMedia;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.video.ffmpeg.FFMPEGImpl;
import org.homio.app.video.ffmpeg.FfmpegHardwareRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
@RequiredArgsConstructor
public class EntityContextMediaImpl implements EntityContextMedia {

    @Getter
    private final EntityContextImpl entityContext;
    private final FfmpegHardwareRepository repo;
    public static String FFMPEG_LOCATION = SystemUtils.IS_OS_LINUX ? "ffmpeg" :
            CommonUtils.getInstallPath().resolve("ffmpeg").resolve("ffmpeg.exe").toString();


    @Override
    public void fireFfmpeg(@NotNull String inputOptions, @NotNull String source, @NotNull String output, int maxWaitTimeout) {
        repo.fireFfmpeg(FFMPEG_LOCATION, inputOptions, source, output, maxWaitTimeout);
    }

    @Override
    public VideoInputDevice createVideoInputDevice(@NotNull String vfile) {
        return repo.createVideoInputDevice(FFMPEG_LOCATION, vfile);
    }

    @Override
    public Set<String> getVideoDevices() {
        return repo.getVideoDevices(FFMPEG_LOCATION);
    }

    @Override
    public Set<String> getAudioDevices() {
        return repo.getAudioDevices(FFMPEG_LOCATION);
    }

    @Override
    public @NotNull FFMPEG buildFFMPEG(@NotNull String entityID, @NotNull String description, @NotNull FFMPEGHandler handler,
                                       @NotNull FFMPEGFormat format, @NotNull String inputArguments, @NotNull String input, @NotNull String outArguments, @NotNull String output,
                                       @NotNull String username, @NotNull String password, @Nullable Runnable destroyListener) {
        return new FFMPEGImpl(entityID, description, handler, format, inputArguments, input, outArguments, output, username,
                password, destroyListener);
    }

    public void onContextCreated() throws Exception {
        entityContext.bgp().registerThreadsPuller("camera-ffmpeg", threadPuller -> {
            for (Map.Entry<String, FFMPEGImpl> threadEntry : FFMPEGImpl.ffmpegMap.entrySet()) {
                FFMPEG ffmpeg = threadEntry.getValue();
                if (ffmpeg.getIsAlive()) {
                    threadPuller.addThread(threadEntry.getKey(), ffmpeg.getDescription(), ffmpeg.getCreationDate(),
                            "working", null,
                            "Command: " + String.join(" ", ffmpeg.getCommandArrayList())
                    );
                }
            }
        });
    }
}
