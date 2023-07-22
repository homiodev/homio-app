package org.homio.app.video.ffmpeg;

import static org.homio.api.EntityContextMedia.CHANNEL_FFMPEG_MOTION_ALARM;
import static org.homio.app.manager.common.impl.EntityContextMediaImpl.FFMPEG_LOCATION;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import org.apache.logging.log4j.Logger;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Responsible for handling multiple ffmpeg conversions which are used for many tasks
 */
@SuppressWarnings("unused")
public class FFMPEGImpl implements FFMPEG {

    public static Map<String, FFMPEGImpl> ffmpegMap = new HashMap<>();

    private final FFMPEGHandler handler;
    private final Logger log;
    private final Runnable destroyListener;
    @Getter private final String description;
    @Getter private final Date creationDate = new Date();
    private final FFMPEGFormat format;
    @Getter private final List<String> commandArrayList = new ArrayList<>();
    private Process process = null;
    private IpVideoFfmpegThread ipVideoFfmpegThread;
    private int keepAlive = 8;
    private final String entityID;

    public FFMPEGImpl(@NotNull String entityID, @NotNull String description,
        @NotNull FFMPEGHandler handler, @NotNull Logger log, @NotNull FFMPEGFormat format,
        @NotNull String inputArguments, @NotNull String input, @NotNull String outArguments,
        @NotNull String output, @NotNull String username, @NotNull String password,
        @Nullable Runnable destroyListener) {
        FFMPEGImpl.ffmpegMap.put(entityID + "_" + description, this);

        this.entityID = entityID;
        this.log = log;
        this.description = description;
        this.format = format;
        this.destroyListener = destroyListener;
        this.handler = handler;
        this.ipVideoFfmpegThread = new IpVideoFfmpegThread();
        inputArguments = inputArguments.trim();
        List<String> builder = new ArrayList<>();
        CommonUtils.addToListSafe(builder, inputArguments.trim());
        if (!input.startsWith("-i")) {
            builder.add("-i");
        }
        // Input can be snapshots not just rtsp or http
        if (!password.isEmpty() && !input.contains("@") && input.contains("rtsp")) {
            String credentials = username + ":" + password + "@";
            // will not work for https: but currently binding does not use https
            builder.add(input.substring(0, 7) + credentials + input.substring(7));
        } else {
            builder.add(input);
        }
        builder.add(outArguments.trim());
        builder.add(output.trim());

        Collections.addAll(commandArrayList, String.join(" ", builder).split("\\s+"));
        // ffmpegLocation may have a space in its folder
        commandArrayList.add(0, FFMPEG_LOCATION);
        log.warn("\n\n[{}]: Generated ffmpeg command for: {}.\n{}\n\n", entityID, format, String.join(" ", commandArrayList));
    }

    public void setKeepAlive(int seconds) {
        if (keepAlive == -1 && seconds > 1) {
            return;// When set to -1 this will not auto turn off stream.
        }
        keepAlive = seconds;
    }

    public boolean stopProcessIfNoKeepAlive() {
        if (keepAlive == 1) {
            stopConverting();
        } else if (keepAlive <= -1 && !getIsAlive()) {
            return startConverting();
        }
        if (keepAlive > 0) {
            keepAlive--;
        }
        return false;
    }

    public synchronized boolean startConverting() {
        if (!ipVideoFfmpegThread.isAlive()) {
            ipVideoFfmpegThread = new IpVideoFfmpegThread();
            ipVideoFfmpegThread.start();
            return true;
        }
        if (keepAlive != -1) {
            keepAlive = 8;
        }
        return false;
    }

    public boolean getIsAlive() {
        return process != null && process.isAlive();
    }

    public void stopConverting() {
        if (ipVideoFfmpegThread.isAlive()) {
            log.debug("[{}]: Stopping ffmpeg {} now when keepalive is:{}", entityID, format, keepAlive);
            if (process != null) {
                process.destroyForcibly();
            }
            if (destroyListener != null) {
                destroyListener.run();
            }
        }
    }

    private class IpVideoFfmpegThread extends Thread {

        public int countOfMotions;

        IpVideoFfmpegThread() {
            setDaemon(true);
            setName("VideoThread_" + format + "_" + handler.getEntityID());
        }

        @Override
        public void run() {
            try {
                process = Runtime.getRuntime().exec(commandArrayList.toArray(new String[0]));
                Process localProcess = process;
                if (localProcess != null) {
                    InputStream errorStream = localProcess.getErrorStream();
                    InputStreamReader errorStreamReader = new InputStreamReader(errorStream);
                    BufferedReader bufferedReader = new BufferedReader(errorStreamReader);
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        if (format.equals(FFMPEGFormat.RTSP_ALARMS)) {
                            log.info("[{}]: {}", entityID, line);
                            if (line.contains("lavfi.")) {
                                if (countOfMotions == 4) {
                                    handler.motionDetected(true, CHANNEL_FFMPEG_MOTION_ALARM);
                                } else {
                                    countOfMotions++;
                                }
                            } else if (line.contains("speed=")) {
                                if (countOfMotions > 0) {
                                    countOfMotions--;
                                    countOfMotions--;
                                    if (countOfMotions <= 0) {
                                        handler.motionDetected(false, CHANNEL_FFMPEG_MOTION_ALARM);
                                    }
                                }
                            } else if (line.contains("silence_start")) {
                                handler.audioDetected(false);
                            } else if (line.contains("silence_end")) {
                                handler.audioDetected(true);
                            }
                        } else {
                            log.info("[{}]: {}", entityID, line);
                        }
                        if (line.contains("No such file or directory")) {
                            handler.ffmpegError(line);
                        }
                    }
                }
            } catch (Exception ex) {
                handler.ffmpegError(CommonUtils.getErrorMessage(ex));
            }
        }
    }
}
