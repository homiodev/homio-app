package org.homio.app.video.ffmpeg;

import static org.homio.api.EntityContextMedia.FFMPEG_MOTION_ALARM;
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
import org.apache.logging.log4j.Level;
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
    private final Runnable destroyListener;
    private final @Getter String description;
    private final @Getter Date creationDate = new Date();
    private final @NotNull FFMPEGFormat format;
    private final @Getter List<String> commandArrayList = new ArrayList<>();
    private Process process = null;
    private IpVideoFfmpegThread ipVideoFfmpegThread;
    private int keepAlive = 8;
    private final String entityID;
    private boolean notFrozen = true;
    private @Getter boolean running;

    public FFMPEGImpl(@NotNull String entityID,
        @NotNull String description,
        @NotNull FFMPEGHandler handler,
        @NotNull FFMPEGFormat format,
        @NotNull String inputArguments,
        @NotNull String input,
        @NotNull String outArguments,
        @NotNull String output,
        @NotNull String username,
        @NotNull String password,
        @Nullable Runnable destroyListener) {
        FFMPEGImpl.ffmpegMap.put(entityID + "_" + description, this);

        this.entityID = entityID;
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
        handler.ffmpegLog(Level.WARN, "Generated ffmpeg command '%s' for: %s.\n%s\n\n"
            .formatted(description, format, String.join(" ", commandArrayList)));
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
            running = true;
            return true;
        }
        if (keepAlive != -1) {
            keepAlive = 8;
        }
        return false;
    }

    public boolean getIsAlive() {
        if (process != null && process.isAlive() && notFrozen) {
            notFrozen = false;
            return true;
        }
        return false;
    }

    public synchronized void stopConverting() {
        if (ipVideoFfmpegThread.isAlive()) {
            handler.ffmpegLog(Level.DEBUG, "Stopping '%s' ffmpeg %s now when keepalive is: %s".formatted(description, format, keepAlive));
            if (process != null) {
                process.destroyForcibly();
            }
            if (destroyListener != null) {
                destroyListener.run();
            }
        }
        running = false;
    }

    private class IpVideoFfmpegThread extends Thread {

        public int countOfMotions;

        IpVideoFfmpegThread() {
            setDaemon(true);
            setName("VideoThread_" + format + "_" + entityID);
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
                        notFrozen = true;
                        if (format == FFMPEGFormat.RTSP_ALARMS) {
                            handler.ffmpegLog(Level.INFO, line);
                            int motionThreshold = handler.getMotionThreshold().intValue();
                            if (line.contains("lavfi.")) {
                                // When the number of pixels that change are below the noise floor we need to look
                                // across frames to confirm it is motion and not noise.
                                if (countOfMotions < 10) {// Stop increasing otherwise it takes too long to go OFF
                                    countOfMotions++;
                                }
                                if (countOfMotions > 9
                                    || countOfMotions > 4 && motionThreshold > 10
                                    || countOfMotions > 3 && motionThreshold > 15
                                    || countOfMotions > 2 && motionThreshold > 30
                                    || countOfMotions > 0 && motionThreshold > 89) {
                                    handler.motionDetected(true, FFMPEG_MOTION_ALARM);
                                    if (countOfMotions < 2) {
                                        countOfMotions = 4;// Used to debounce the Alarm.
                                    }
                                }
                            } else if (line.contains("speed=")) {
                                if (countOfMotions > 0) {
                                    if (motionThreshold > 89) {
                                        countOfMotions--;
                                    }
                                    if (motionThreshold > 10) {
                                        countOfMotions -= 2;
                                    } else {
                                        countOfMotions -= 4;
                                    }
                                    if (countOfMotions <= 0) {
                                        handler.motionDetected(false, FFMPEG_MOTION_ALARM);
                                        countOfMotions = 0;
                                    }
                                }
                            } else if (line.contains("silence_start")) {
                                handler.audioDetected(false);
                            } else if (line.contains("silence_end")) {
                                handler.audioDetected(true);
                            }
                        } else {
                            handler.ffmpegLog(Level.DEBUG, line);
                        }
                        if (line.contains("No such file or directory")) {
                            handler.ffmpegError(line);
                        }
                    }
                }
            } catch (Exception ex) {
                handler.ffmpegError(CommonUtils.getErrorMessage(ex));
            } finally {
                running = false;
            }
        }
    }
}
