package org.touchhome.app.videoStream.ffmpeg;

import org.touchhome.app.videoStream.handler.BaseFfmpegCameraHandler;
import org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;
import org.touchhome.bundle.api.measure.DecimalType;
import org.touchhome.bundle.api.measure.OnOffType;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.touchhome.app.videoStream.onvif.util.IpCameraBindingConstants.*;

/**
 * Responsible for handling multiple ffmpeg conversions which are used for many tasks
 * Parameters:
 * '-an' - remove audio from stream
 * '-vn' remove video from stream
 */
public class Ffmpeg {

    private BaseFfmpegCameraHandler baseFfmpegCameraHandler;
    private Process process = null;
    private String ffmpegCommand;
    private FFmpegFormat format;
    private List<String> commandArrayList = new ArrayList<>();
    private IpCameraFfmpegThread ipCameraFfmpegThread = new IpCameraFfmpegThread();
    private int keepAlive = 8;
    private String password;

    public Ffmpeg(BaseFfmpegCameraHandler handle, FFmpegFormat format, String ffmpegLocation, String inputArguments,
                  String input, String outArguments, String output, String username, String password) {
        this.format = format;
        this.password = password;
        inputArguments = inputArguments.trim();
        baseFfmpegCameraHandler = handle;
        String altInput = input;
        // Input can be snapshots not just rtsp or http
        if (!password.isEmpty() && !input.contains("@") && input.contains("rtsp")) {
            String credentials = username + ":" + password + "@";
            // will not work for https: but currently binding does not use https
            altInput = input.substring(0, 7) + credentials + input.substring(7);
        }
        if (inputArguments.isEmpty()) {
            ffmpegCommand = "-i " + altInput + " " + outArguments + " " + output;
        } else {
            ffmpegCommand = inputArguments + " -i " + altInput + " " + outArguments + " " + output;
        }
        Collections.addAll(commandArrayList, ffmpegCommand.trim().split("\\s+"));
        // ffmpegLocation may have a space in its folder
        commandArrayList.add(0, ffmpegLocation);
    }

    public void setKeepAlive(int numberOfEightSeconds) {
        // We poll every 8 seconds due to mjpeg stream requirement.
        if (keepAlive == -1 && numberOfEightSeconds > 1) {
            return;// When set to -1 this will not auto turn off stream.
        }
        keepAlive = numberOfEightSeconds;
    }

    public void checkKeepAlive() {
        if (keepAlive == 1) {
            stopConverting();
        } else if (keepAlive <= -1 && !getIsAlive()) {
            baseFfmpegCameraHandler.getLog().warn("HLS stream was not running, restarting it now.");
            startConverting();
        }
        if (keepAlive > 0) {
            keepAlive--;
        }
    }

    private class IpCameraFfmpegThread extends Thread {
        private ScheduledExecutorService threadPool = Executors.newScheduledThreadPool(2);
        public int countOfMotions;

        IpCameraFfmpegThread() {
            setDaemon(true);
        }

        private void gifCreated() {
            // Without a small delay, Pushover sends no file 10% of time.
            baseFfmpegCameraHandler.setAttribute(CHANNEL_RECORDING_GIF, DecimalType.ZERO);
            baseFfmpegCameraHandler.setGifHistoryLength(++baseFfmpegCameraHandler.gifHistoryLength);
        }

        private void mp4Created() {
            baseFfmpegCameraHandler.setAttribute(CHANNEL_RECORDING_MP4, DecimalType.ZERO);
            baseFfmpegCameraHandler.setMp4HistoryLength(++baseFfmpegCameraHandler.mp4HistoryLength);
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
                        if (format.equals(FFmpegFormat.RTSP_ALARMS)) {
                            baseFfmpegCameraHandler.getLog().debug("{}", line);
                            if (line.contains("lavfi.")) {
                                if (countOfMotions == 4) {
                                    baseFfmpegCameraHandler.motionDetected(CHANNEL_FFMPEG_MOTION_ALARM);
                                } else {
                                    countOfMotions++;
                                }
                            } else if (line.contains("speed=")) {
                                if (countOfMotions > 0) {
                                    countOfMotions--;
                                    countOfMotions--;
                                    if (countOfMotions <= 0) {
                                        baseFfmpegCameraHandler.noMotionDetected(CHANNEL_FFMPEG_MOTION_ALARM);
                                    }
                                }
                            } else if (line.contains("silence_start")) {
                                baseFfmpegCameraHandler.noAudioDetected();
                            } else if (line.contains("silence_end")) {
                                baseFfmpegCameraHandler.audioDetected();
                            }
                        } else {
                            baseFfmpegCameraHandler.getLog().info("{}", line);
                        }
                        if (line.contains("No such file or directory")) {
                            baseFfmpegCameraHandler.ffmpegError(line);
                        }
                    }
                }
            } catch (IOException ex) {
                baseFfmpegCameraHandler.getLog().warn("An error occurred trying to process the messages from FFmpeg.");
                baseFfmpegCameraHandler.ffmpegError(TouchHomeUtils.getErrorMessage(ex));
            } finally {
                switch (format) {
                    case GIF:
                        threadPool.schedule(this::gifCreated, 800, TimeUnit.MILLISECONDS);
                        break;
                    case RECORD:
                        threadPool.schedule(this::mp4Created, 800, TimeUnit.MILLISECONDS);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    public void startConverting() {
        if (!ipCameraFfmpegThread.isAlive()) {
            ipCameraFfmpegThread = new IpCameraFfmpegThread();
            baseFfmpegCameraHandler.getLog()
                    .debug("Starting ffmpeg with this command now:{}",
                            ffmpegCommand.replaceAll(password, "********"));
            ipCameraFfmpegThread.start();
            if (format.equals(FFmpegFormat.HLS)) {
                baseFfmpegCameraHandler.setAttribute(CHANNEL_START_STREAM, OnOffType.ON);
            }
        }
        if (keepAlive != -1) {
            keepAlive = 8;
        }
    }

    public boolean getIsAlive() {
        Process localProcess = process;
        if (localProcess != null) {
            return localProcess.isAlive();
        }
        return false;
    }

    public void stopConverting() {
        if (ipCameraFfmpegThread.isAlive()) {
            baseFfmpegCameraHandler.getLog().debug("Stopping ffmpeg {} now when keepalive is:{}", format, keepAlive);
            Process localProcess = process;
            if (localProcess != null) {
                localProcess.destroyForcibly();
            }
            if (format.equals(FFmpegFormat.HLS)) {
                baseFfmpegCameraHandler.setAttribute(CHANNEL_START_STREAM, OnOffType.OFF);
            }
        }
    }
}
