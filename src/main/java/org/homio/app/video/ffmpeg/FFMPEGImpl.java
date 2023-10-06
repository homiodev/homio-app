package org.homio.app.video.ffmpeg;

import static org.homio.app.manager.common.impl.EntityContextMediaImpl.FFMPEG_LOCATION;

import com.pivovarit.function.ThrowingRunnable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.SimpleFormatter;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.Level;
import org.homio.api.EntityContext;
import org.homio.api.EntityContextHardware.ProcessStat;
import org.homio.api.EntityContextMedia.FFMPEG;
import org.homio.api.EntityContextMedia.FFMPEGFormat;
import org.homio.api.EntityContextMedia.FFMPEGHandler;
import org.homio.api.model.UpdatableValue;
import org.homio.api.util.CommonUtils;
import org.homio.app.manager.common.impl.EntityContextBGPImpl;
import org.homio.app.manager.common.impl.EntityContextHardwareImpl.ProcessStatImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

/**
 * Responsible for handling multiple ffmpeg conversions which are used for many tasks
 */
@SuppressWarnings("unused")
public class FFMPEGImpl implements FFMPEG {

    public static final @NotNull Path FFMPEG_LOG_PATH;
    private static final Map<String, UpdatableValue<ProcessStat>> PROCESS_STAT_LOADING_CACHE = new ConcurrentHashMap<>();

    static {
        Path ffmpegLogPath = CommonUtils.getLogsPath().resolve("ffmpeg");
        try {
            FileUtils.deleteDirectory(ffmpegLogPath.toFile());
        } catch (IOException ignore) {
        }
        FFMPEG_LOG_PATH = CommonUtils.createDirectoriesIfNotExists(ffmpegLogPath);
    }

    public static Map<String, FFMPEGImpl> ffmpegMap = new HashMap<>();

    private final @Getter Date creationDate = new Date();
    private final @Getter JSONObject metadata = new JSONObject();
    protected final @Getter List<String> commandArrayList = new ArrayList<>();

    protected final FFMPEGHandler handler;
    private final @Getter String description;
    private final @Getter @NotNull FFMPEGFormat format;
    private final @Getter String output;
    protected final @NotNull Map<String, ThrowingRunnable<Exception>> destroyListeners = new HashMap<>();
    private final FileHandler fileHandler;
    private final @Getter @NotNull Path logPath;
    private final @Getter String cmd;
    protected final String entityID;
    private final EntityContext entityContext;
    private final @Getter int commandHashCode;
    protected @Nullable Collection<ThrowingRunnable<Exception>> threadDestroyListeners;
    protected Process process = null;
    // this is indicator that tells if this ffmpeg command is still need by 3th part request
    private int keepAlive = 8;
    private Thread ffmpegThread;
    private long lastAnswerFromFFMPEG = 0;
    private final @Getter AtomicBoolean running = new AtomicBoolean(false);
    private @Setter @Accessors(chain = true) @Nullable Path workingDirectory;

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
        @NotNull EntityContext entityContext) {
        this(entityID, description, handler, format, output, buildCommand(inputArguments,
            input, outArguments, output, username, password), entityContext);
    }

    @SneakyThrows
    public FFMPEGImpl(@NotNull String entityID,
        @NotNull String description,
        @NotNull FFMPEGHandler handler,
        @NotNull FFMPEGFormat format,
        @NotNull String output,
        @NotNull String command,
        @NotNull EntityContext entityContext) {
        this.entityContext = entityContext;
        FFMPEGImpl.ffmpegMap.put(entityID + "_" + description, this);

        this.logPath = FFMPEG_LOG_PATH.resolve(entityID + "_" + description + ".log");
        this.fileHandler = new FileHandler(logPath.toString(), 1024 * 1024, 1, true);
        this.fileHandler.setFormatter(new SimpleFormatter());

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
            return true;
        }
        return false;
    }

    @Override
    public Path getOutputFile() {
        if(workingDirectory == null) {
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
            entityContext.bgp().builder("fetch-ffmpeg-proc-stat")
                         .execute(() -> {
                             cachedValue.update(entityContext.hardware().getProcessStat(process.pid()));
                             onRefreshUpdated.run();
                         }));
    }

    @Override
    public synchronized boolean stopConverting(Duration duration) {
        if (ffmpegThread.isAlive()) {
            logWarn("Stopping '%s' ffmpeg %s now when keepalive is: %s".formatted(description, format, keepAlive));
            if (process != null) {
                EntityContextBGPImpl.stopProcess(process, description);
                process.destroyForcibly();
            }
            if (duration != null) {
                waitRunningProcess(duration);
            }
            return true;
        }
        return false;
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
    }

    protected void logWarn(String message) {
        handler.ffmpegLog(Level.WARN, message);
        fileHandler.publish(new LogRecord(java.util.logging.Level.WARNING, message));
    }

    protected void logInfo(String message) {
        handler.ffmpegLog(Level.INFO, message);
        fileHandler.publish(new LogRecord(java.util.logging.Level.INFO, message));
    }

    protected void logDebug(String message) {
        handler.ffmpegLog(Level.DEBUG, message);
        fileHandler.publish(new LogRecord(java.util.logging.Level.FINE, message));
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
