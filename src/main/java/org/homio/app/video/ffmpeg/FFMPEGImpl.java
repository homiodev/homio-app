package org.homio.app.video.ffmpeg;

import com.pivovarit.function.ThrowingRunnable;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.logging.log4j.Level;
import org.homio.api.Context;
import org.homio.api.Context.FileLogger;
import org.homio.api.ContextHardware.ProcessStat;
import org.homio.api.ContextMedia.FFMPEG;
import org.homio.api.ContextMedia.FFMPEGFormat;
import org.homio.api.ContextMedia.FFMPEGHandler;
import org.homio.api.model.UpdatableValue;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.impl.ContextBGPImpl;
import org.homio.app.manager.common.impl.ContextHardwareImpl.ProcessStatImpl;
import org.homio.app.model.entity.FFMPEGEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.homio.app.manager.common.impl.ContextMediaImpl.FFMPEG_LOCATION;

/**
 * Responsible for handling multiple ffmpeg conversions which are used for many tasks
 */
@SuppressWarnings("unused")
public class FFMPEGImpl implements FFMPEG {

    private static final Map<String, UpdatableValue<ProcessStat>> PROCESS_STAT_LOADING_CACHE = new ConcurrentHashMap<>();

    public static Map<String, FFMPEGImpl> ffmpegMap = new HashMap<>();
    public static FFMPEGEntity entity;

    private final @Getter Date creationDate = new Date();
    private final @Getter JSONObject metadata = new JSONObject();
    protected final @Getter List<String> commandArrayList = new ArrayList<>();

    protected final FFMPEGHandler handler;
    private final @Getter String description;
    private final @Getter
    @NotNull FFMPEGFormat format;
    private final @Getter String output;
    protected final @NotNull Map<String, ThrowingRunnable<Exception>> destroyListeners = new HashMap<>();
    private final @Getter String cmd;
    protected final String entityID;
    private final Context context;
    private final @Getter int commandHashCode;
    private final @Getter
    @NotNull FileLogger fileLogger;
    protected @Nullable Collection<ThrowingRunnable<Exception>> threadDestroyListeners;
    protected Process process = null;
    // this is indicator that tells if this ffmpeg command is still need by 3th part request
    private int keepAlive = 8;
    private Thread ffmpegThread;
    private long lastAnswerFromFFMPEG = 0;
    private final @Getter AtomicBoolean running = new AtomicBoolean(false);
    private @Setter
    @Accessors(chain = true)
    @Nullable Path workingDirectory;

    @SneakyThrows
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
                      @NotNull Context context) {
        this(entityID, description, handler, format, output, buildCommand(inputArguments,
                input, outArguments, output, username, password), context);
    }

    @SneakyThrows
    public FFMPEGImpl(@NotNull String entityID,
                      @NotNull String description,
                      @NotNull FFMPEGHandler handler,
                      @NotNull FFMPEGFormat format,
                      @NotNull String output,
                      @NotNull String command,
                      @NotNull Context context) {
        this.context = context;
        FFMPEGImpl.ffmpegMap.put(entityID + "_" + description, this);

        this.fileLogger = context.getFileLogger(FFMPEGImpl.entity, description);
        this.entityID = entityID;
        this.description = description;
        this.format = format;
        this.handler = handler;
        this.ffmpegThread = createFFMPEGThread();
        this.output = output;
        this.commandHashCode = command.hashCode();
        Collections.addAll(commandArrayList, command.split("\\s+"));
        cmd = "ffmpeg " + String.join(" ", commandArrayList);
        // ffmpegLocation may have a space in its folder
        commandArrayList.add(0, FFMPEG_LOCATION);
        context.ui().updateItem(entity);
    }

    public static String buildCommand(
            @NotNull String inputArguments,
            @NotNull String input,
            @NotNull String outArguments,
            @NotNull String output,
            @NotNull String username,
            @NotNull String password) {
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
        return String.join(" ", builder);
    }

    public synchronized boolean startConverting() {
        if (keepAlive != -1) {
            keepAlive = 8;
        }
        boolean processAlive = ffmpegThread.isAlive();
        if (processAlive && System.currentTimeMillis() - lastAnswerFromFFMPEG > 30000) {
            stopConverting();
        }
        if (!processAlive) {
            ffmpegThread = createFFMPEGThread();
            running.set(true);
            FFMPEGImpl.ffmpegMap.put(entityID + "_" + description, this);
            ffmpegThread.start();
            context.ui().updateItem(entity);
            return true;
        }
        return false;
    }

    @Override
    public @NotNull Path getOutputFile() {
        if (workingDirectory == null) {
            return Paths.get(output);
        }
        return workingDirectory.resolve(output);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    public void setKeepAlive(int seconds) {
        if (keepAlive == -1 && seconds > 1) {
            return;// When set to -1 this will not auto turn off stream.
        }
        keepAlive = seconds;
    }

    public boolean stopProcessIfNoKeepAlive() {
        if (isRunning()) {
            if (keepAlive == 0) {
                return stopConverting();
            }
            if (keepAlive > 0) {
                keepAlive--;
            }
        }
        return false;
    }

    @Override
    public FFMPEG addDestroyListener(@NotNull String key, @NotNull ThrowingRunnable<Exception> destroyListener) {
        destroyListeners.put(key, destroyListener);
        return this;
    }

    public boolean getIsAlive() {
        return process != null && process.isAlive() && System.currentTimeMillis() - lastAnswerFromFFMPEG < 30000;
    }

    @SneakyThrows
    public ProcessStat getProcessStat(Runnable onRefreshUpdated) {
        UpdatableValue<ProcessStat> cachedValue = PROCESS_STAT_LOADING_CACHE.computeIfAbsent(entityID,
                s -> UpdatableValue.wrap(new ProcessStatImpl(0, 0, 0), entityID));
        return cachedValue.getFreshValue(Duration.ofSeconds(10), () ->
                context.bgp().builder("fetch-ffmpeg-proc-stat")
                        .execute(() -> {
                            cachedValue.update(context.hardware().getProcessStat(process.pid()));
                            onRefreshUpdated.run();
                        }));
    }

    @Override
    public synchronized boolean stopConverting(Duration duration) {
        try {
            if (ffmpegThread.isAlive()) {
                fileLogger.logWarn("Stopping '%s' ffmpeg %s now when keepalive is: %s".formatted(description, format, keepAlive));
                if (process != null) {
                    ContextBGPImpl.stopProcess(process, description);
                    process.destroyForcibly();
                }
                if (duration != null) {
                    waitRunningProcess(duration);
                }
                return true;
            }
            return false;
        } finally {
            context.ui().updateItem(entity);
        }
    }

    protected @NotNull Thread createFFMPEGThread() {
        return new FFMPEGThread();
    }

    private void finishFFMPEG() {
        logInfo("Finish ffmpeg command '%s'".formatted(description));
        running.set(false);

        Collection<ThrowingRunnable<Exception>> destroyListeners = threadDestroyListeners;
        if (destroyListeners != null) {
            destroyListeners.removeIf(runnable -> {
                try {
                    runnable.run();
                } catch (Exception ex) {
                    handler.ffmpegLog(Level.WARN, "Error during call destroy listener: %s".formatted(CommonUtils.getErrorMessage(ex)));
                }
                return true;
            });
        }

        FFMPEGImpl.ffmpegMap.remove(entityID + "_" + description, this);
        context.ui().updateItem(entity);
    }

    protected void logWarn(String message) {
        handler.ffmpegLog(Level.WARN, message);
        fileLogger.logWarn(message);
    }

    protected void logInfo(String message) {
        handler.ffmpegLog(Level.INFO, message);
        fileLogger.logInfo(message);
    }

    protected void logDebug(String message) {
        handler.ffmpegLog(Level.DEBUG, message);
        fileLogger.logDebug(message);
    }

    protected class FFMPEGThread extends Thread {

        public int countOfMotions;

        protected FFMPEGThread() {
            setDaemon(true);
            setName("FFMPEG_thread_" + format + "_" + entityID);
        }

        @Override
        public void run() {
            try {
                threadDestroyListeners = new ArrayList<>(destroyListeners.values());
                logInfo("Starting ffmpeg[%s] command '%s'. Run: %s".formatted(format,
                        description, String.join(" ", commandArrayList)));
                ProcessBuilder builder = new ProcessBuilder(commandArrayList.toArray(new String[0]));
                if (workingDirectory != null) {
                    builder.directory(workingDirectory.toFile());
                }
                process = builder.start();
                lastAnswerFromFFMPEG = System.currentTimeMillis();
                InputStream errorStream = process.getErrorStream();
                InputStreamReader errorStreamReader = new InputStreamReader(errorStream);
                BufferedReader bufferedReader = new BufferedReader(errorStreamReader);
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    lastAnswerFromFFMPEG = System.currentTimeMillis();
                    handleLine(line);
                    if (line.contains("No such file or directory") || line.contains("Could not run graph")) {
                        handler.ffmpegError(line);
                    }
                }
            } catch (Exception ex) {
                handler.ffmpegError(CommonUtils.getErrorMessage(ex));
            } finally {
                finishFFMPEG();
            }
        }

        protected void handleLine(String line) {
            logDebug(line);
        }
    }

    @SneakyThrows
    private void waitRunningProcess(Duration duration) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = duration.toMillis();
        while (running.get()) {
            if (System.currentTimeMillis() - startTime >= timeoutMillis) {
                System.out.println("Timeout: AtomicBoolean did not become false within 10 seconds.");
                break;
            }
            Thread.sleep(100);
        }
        if (running.get()) {
            throw new IllegalStateException("Max timout occurs while waiting to stop ffmpeg process: " + getDescription());
        }
    }
}
