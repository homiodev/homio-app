package org.homio.app.manager.common.impl;

import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.ThrowingRunnable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.app.config.AppProperties;
import org.homio.app.json.BgpProcessResponse;
import org.homio.app.manager.bgp.InternetAvailabilityBgpService;
import org.homio.app.manager.bgp.WatchdogBgpService;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextBGP;
import org.homio.bundle.api.model.HasEntityIdentifier;
import org.homio.bundle.api.service.EntityService.WatchdogService;
import org.homio.bundle.api.setting.SettingPlugin;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.hquery.LinesReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Log4j2
public class EntityContextBGPImpl implements EntityContextBGP {

    @Getter private final EntityContextImpl entityContext;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Getter private final Map<String, ThreadContextImpl<?>> schedulers = new ConcurrentHashMap<>();

    // not all threads may be run inside ThreadContextImpl, so we need bundle able to register
    // custom
    // threads to show them on ui
    @Getter
    private final Map<String, Consumer<ThreadPuller>> threadsPullers = new ConcurrentHashMap<>();

    private final Map<String, BatchRunContext<?>> batchRunContextMap = new ConcurrentHashMap<>();

    @Getter
    private final InternetAvailabilityBgpService internetAvailabilityService;
    private final WatchdogBgpService watchdogBgpService;

    public EntityContextBGPImpl(
        EntityContextImpl entityContext, ThreadPoolTaskScheduler taskScheduler, AppProperties appProperties) {
        this.entityContext = entityContext;
        this.taskScheduler = taskScheduler;
        this.internetAvailabilityService = new InternetAvailabilityBgpService(entityContext, appProperties, this);
        this.watchdogBgpService = new WatchdogBgpService(this);
    }

    public void onContextCreated() {
        // send update to UI about changed processes. Send only of user open console with thread/scheduler tab!
        this.builder("send-bgp-to-ui")
            .interval(Duration.ofSeconds(1))
            .cancelOnError(false)
            .execute(() -> this.entityContext.ui().sendDynamicUpdate("bgp", getProcesses()));
    }

    public BgpProcessResponse getProcesses() {
        BgpProcessResponse response = new BgpProcessResponse();
        for (EntityContextBGPImpl.ThreadContextImpl<?> context : schedulers.values()) {
            response.add(context);
        }
        return response;
    }

    /* @Override
    public <T> ThreadContext<T> runAndGet(@NotNull String name,
        @Nullable Duration delay,
        @NotNull ThrowingSupplier<T, Exception> command,
        boolean showOnUI) {
      return createSchedule(name, delay, threadContext -> command.get(),
          EntityContextBGPImpl.ScheduleType.SINGLE, showOnUI, true,
          runnable -> taskScheduler.schedule(runnable, delay == null ?
              new Date() : new Date(System.currentTimeMillis() + delay.toMillis())));
    }*/

    @Override
    public void cancelThread(@NotNull String name) {
        ThreadContextImpl<?> context = this.schedulers.get(name);
        if (context != null) {
            log.info("Cancel process: <{}>", context.getName());
            context.cancel();
        } else if (this.batchRunContextMap.containsKey(name)) {
            log.info("Stop batch process: <{}>", name);
            this.batchRunContextMap.get(name).executor.shutdownNow();
        } else {
            for (Map.Entry<String, BatchRunContext<?>> entry : this.batchRunContextMap.entrySet()) {
                if (entry.getValue().processes.containsKey(name)) {
                    log.info("Stop single process <{}> in batch <{}", name, entry.getKey());
                    Future<?> future = entry.getValue().processes.get(name);
                    if (future.isDone()) {
                        log.warn("Process {} already done", name);
                    } else {
                        future.cancel(true);
                    }
                }
            }
        }
    }

    @Override
    public void registerThreadsPuller(@NotNull String entityId, @NotNull Consumer<ThreadPuller> threadPullerConsumer) {
        this.threadsPullers.put(entityId, threadPullerConsumer);
    }

    @Override
    public <P extends HasEntityIdentifier, T> void runInBatch(
        @NotNull String batchName,
        @Nullable Duration maxTerminateTimeout,
        @NotNull Collection<P> batchItems,
        @NotNull Function<P, Callable<T>> callableProducer,
        @NotNull Consumer<Integer> progressConsumer,
        @NotNull Consumer<List<T>> finallyProcessBlockHandler) {
        BatchRunContext<T> batchRunContext = prepareBatchProcessContext(batchName, batchItems.size());
        for (P batchItem : batchItems) {
            Callable<T> callable = callableProducer.apply(batchItem);
            if (callable != null) {
                batchRunContext.processes.put(batchItem.getEntityID(), batchRunContext.executor.submit(callable));
            }
        }
        builder("batch-wait_" + batchName).execute(() -> {
            List<T> result = waitBatchToDone(batchName, maxTerminateTimeout, progressConsumer, batchRunContext);
            finallyProcessBlockHandler.accept(result);
        });
    }

    @SneakyThrows
    @Override
    public <T> List<T> runInBatchAndGet(
        @NotNull String batchName,
        @Nullable Duration maxTerminateTimeout,
        int threadsCount,
        @NotNull Map<String, Callable<T>> runnableTasks,
        @NotNull Consumer<Integer> progressConsumer) {
        BatchRunContext<T> batchRunContext = prepareBatchProcessContext(batchName, threadsCount);
        for (Map.Entry<String, Callable<T>> entry : runnableTasks.entrySet()) {
            batchRunContext.processes.put(entry.getKey(), batchRunContext.executor.submit(entry.getValue()));
        }
        return waitBatchToDone(batchName, maxTerminateTimeout, progressConsumer, batchRunContext);
    }

    @Override
    public void executeOnExit(Runnable runnable) {
        Runtime.getRuntime().addShutdownHook(new Thread(runnable));
    }

    @Override
    public boolean isThreadExists(@NotNull String name, boolean checkOnlyRunningThreads) {
        ThreadContextImpl<?> threadContext = this.schedulers.get(name);
        if (checkOnlyRunningThreads) {
            return threadContext != null && !threadContext.stopped;
        }
        return threadContext != null;
    }

    public void addWatchDogService(String key, WatchdogService watchdogService) {
        this.watchdogBgpService.addWatchDogService(key, watchdogService);
    }

    private <T> EntityContextBGPImpl.BatchRunContext<T> prepareBatchProcessContext(@NotNull String batchName, int threadsCount) {
        if (batchRunContextMap.containsKey(batchName)) {
            throw new IllegalStateException("Batch processes with name " + batchName + " already in progress");
        }
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
        BatchRunContext<T> batchRunContext = new BatchRunContext<>(executor);
        batchRunContextMap.put(batchName, batchRunContext);
        return batchRunContext;
    }

    private <T> List<T> waitBatchToDone(
        @NotNull String batchName,
        @Nullable Duration maxTerminateTimeout,
        @NotNull Consumer<Integer> progressConsumer,
        @NotNull BatchRunContext<T> batchRunContext)
        throws InterruptedException, ExecutionException {
        ThreadPoolExecutor executor = batchRunContext.executor;
        executor.shutdown();
        long batchStarted = System.currentTimeMillis();
        long maxTerminatedMillis = maxTerminateTimeout == null ? 0 : maxTerminateTimeout.toMillis();
        try {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                    long completedTaskCount = executor.getCompletedTaskCount();
                    progressConsumer.accept((int) completedTaskCount);

                    if (maxTerminatedMillis > 0 && System.currentTimeMillis() - batchStarted > maxTerminatedMillis) {
                        log.warn("Exceeded await limit for batch run <{}> (Max {} sec.)", batchName, maxTerminatedMillis);
                        executor.shutdownNow();
                    }
                } catch (Exception ignore) {
                    log.error("Error while await termination for batch run: <{}>", batchName);
                    executor.shutdownNow();
                }
            }
            List<T> list = new ArrayList<>();
            for (Map.Entry<String, Future<T>> entry : batchRunContext.processes.entrySet()) {
                try {
                    if (entry.getValue().isDone()) { // in case of cancellation from UI
                        list.add(entry.getValue().get());
                    }
                } catch (CancellationException ex) {
                    log.warn("Process <{}> in batch <{}> has been cancelled. Ignore result", entry.getKey(), batchName);
                }
            }
            return list;
        } finally {
            batchRunContextMap.remove(batchName);
            log.info("Finish batch run <{}>", batchName);
        }
    }

    @Override
    public <T> ScheduleBuilder<T> builder(@NotNull String name) {
        ThreadContextImpl<T> context = new ThreadContextImpl<>();
        context.name = name;
        context.scheduleType = ScheduleType.SINGLE;
        return new ScheduleBuilder<T>() {
            private Function<Runnable, ScheduledFuture<?>> scheduleHandler;

            @Override
            public ThreadContext<T> execute(@NotNull ThrowingFunction<ThreadContext<T>, T, Exception> command, boolean start) {
                context.command = command;
                if (scheduleHandler == null) {
                    scheduleHandler = runnable -> {
                        Date startDate = new Date();
                        if (context.delay != null) {
                            startDate = new Date(System.currentTimeMillis() + context.delay.toMillis());
                        }
                        if (context.scheduleType == ScheduleType.SINGLE) {
                            return taskScheduler.schedule(runnable, startDate);
                        } else {
                            return taskScheduler.scheduleWithFixedDelay(runnable, startDate, context.period.toMillis());
                        }
                    };
                }

                if (start) {
                    createSchedule(context, scheduleHandler);
                } else {
                    context.postponeScheduleHandler = scheduleHandler;
                    cancelThread(context.name);
                    schedulers.put(context.name, context);

                }
                return context;
            }

            @Override
            public ThreadContext<Void> execute(@NotNull ThrowingRunnable<Exception> command, boolean start) {
                context.command = arg -> {
                    if (context.authentication == null) {
                        command.run();
                    } else {
                        try {
                            SecurityContextHolder.getContext().setAuthentication(context.authentication);
                            command.run();
                        } finally {
                            SecurityContextHolder.getContext().setAuthentication(null);
                        }
                    }
                    return null;
                };
                return (ThreadContext<Void>) execute(context.command, start);
            }

            @Override
            public ScheduleBuilder<T> interval(@NotNull String cron) {
                context.scheduleType = ScheduleType.CRON;
                scheduleHandler = runnable -> taskScheduler.schedule(runnable, new CronTrigger(cron));
                return this;
            }

            @Override
            public ScheduleBuilder<T> interval(@NotNull Duration duration) {
                context.scheduleType = ScheduleType.DELAY;
                context.period = duration;
                return this;
            }

            @Override
            public ScheduleBuilder<T> tap(Consumer<ThreadContext<T>> handler) {
                handler.accept(context);
                return this;
            }

            @Override
            public ScheduleBuilder<T> delay(@NotNull Duration duration) {
                context.delay = duration;
                return this;
            }

            @Override
            public ScheduleBuilder<T> auth() {
                context.authentication = SecurityContextHolder.getContext().getAuthentication();
                return this;
            }

            @Override
            public ScheduleBuilder<T> hideOnUI(boolean value) {
                context.showOnUI = !value;
                return this;
            }

            @Override
            public ScheduleBuilder<T> hideOnUIAfterCancel(boolean value) {
                context.hideOnUIAfterCancel = value;
                return this;
            }

            @Override
            public ScheduleBuilder<T> cancelOnError(boolean value) {
                context.cancelOnError = value;
                return this;
            }

            @Override
            public ScheduleBuilder<T> linkLogFile(Path logFile) {
                context.logFile = logFile;
                return this;
            }
        };
    }

    @Override
    public <T> void runService(@NotNull EntityContext entityContext, @NotNull Consumer<Process> processConsumer, @NotNull String name,
        @NotNull Class<? extends SettingPlugin<T>> settingClass) {
        if (SystemUtils.IS_OS_LINUX) {
            entityContext.hardware().startSystemCtl(name);
        } else {
            Path targetPath = Paths.get(entityContext.setting().getRawValue(settingClass));
            Path logFile = targetPath.getParent().resolve("execution-" + name + ".log");
            entityContext.bgp().builder(name + "-service").linkLogFile(logFile).hideOnUIAfterCancel(false).execute(() -> {
                Process process = Runtime.getRuntime().exec(targetPath.toString());
                entityContext.bgp().executeOnExit(() -> {
                    if (process != null) {
                        process.destroyForcibly();
                    }
                });
                processConsumer.accept(process);
                Thread inputThread = new Thread(new LinesReader(name + "inputReader", process.getInputStream(), null, message ->
                    log.info("[{}]: {}", name, message)));
                Thread errorThread = new Thread(new LinesReader(name + "errorReader", process.getErrorStream(), null, message ->
                    log.error("[{}]: {}", name, message)));
                inputThread.start();
                errorThread.start();

                process.waitFor();
                inputThread.interrupt();
                errorThread.interrupt();
            });
        }
    }

    @Override
    @SneakyThrows
    public ThreadContext<Void> runFileWatchdog(@NotNull Path file, String key, @NotNull ThrowingRunnable<Exception> onUpdateCommand) {
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File: " + file + " does not exists");
        }
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("File: " + file + " is not a regular file");
        }
        if (!Files.isReadable(file)) {
            throw new IllegalArgumentException("File: " + file + " is not readable");
        }

        ThreadContext<Void> fileWatchDog = (ThreadContext<Void>) this.schedulers.get("file-watchdog" + file);
        if (fileWatchDog != null) {
            ((ThreadContextImpl) fileWatchDog).simpleWorkUnitListeners.put(key, onUpdateCommand);
        } else {
            ScheduleBuilder<Void> scheduleBuilder = builder("file-watchdog" + file);
            final AtomicLong lastModified = new AtomicLong(Files.readAttributes(file, BasicFileAttributes.class).lastModifiedTime().toMillis());
            fileWatchDog = scheduleBuilder.delay(Duration.ofSeconds(10)).interval(Duration.ofSeconds(10))
                                          .execute(context -> {
                                              checkFileModifiedAndFireHandler(file, lastModified, (ThreadContextImpl) context);
                                              return null;
                                          });
            ((ThreadContextImpl) fileWatchDog).simpleWorkUnitListeners = new ConcurrentHashMap<>();
            ((ThreadContextImpl) fileWatchDog).simpleWorkUnitListeners.put(key, onUpdateCommand);
        }
        return fileWatchDog;
    }

    private void checkFileModifiedAndFireHandler(@NotNull Path file, AtomicLong lastModified, ThreadContextImpl context) throws Exception {
        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
        if (attr.lastModifiedTime().toMillis() > lastModified.get()) {
            lastModified.set(attr.lastModifiedTime().toMillis());
            Map<String, ThrowingRunnable<Exception>> listeners = context.simpleWorkUnitListeners;
            for (ThrowingRunnable<Exception> runnable : listeners.values()) {
                runnable.run();
            }
        }
    }

    private <T> void createSchedule(ThreadContextImpl<T> threadContext, @NotNull Function<Runnable, ScheduledFuture<?>> scheduleHandler) {

        this.cancelThread(threadContext.name);
        this.schedulers.put(threadContext.name, threadContext);

        Runnable runnable =
            () -> {
                try {
                    threadContext.runCount++;
                    threadContext.state = "STARTED";
                    threadContext.setRetValue(threadContext.getCommand().apply(threadContext));
                    threadContext.state = "FINISHED";
                    if (threadContext.scheduleType == ScheduleType.SINGLE) {
                        if (threadContext.scheduledFuture == null) {
                            try {
                                Thread.sleep(100); // hack: sleep 100ms to allow assign
                            } catch (InterruptedException ignore) {}
                            // threadContext.scheduledFuture = ....
                        }
                        threadContext.cancelProcessInternal();
                    }
                } catch (Exception ex) {
                    if (ex instanceof CancellationException) {
                        threadContext.state = "FINISHED";
                        threadContext.cancel();
                        return;
                    }
                    threadContext.state = "FINISHED_WITH_ERROR";
                    threadContext.error = CommonUtils.getErrorMessage(ex);

                    log.error("Exception in thread: <{}>. Message: <{}>", threadContext.name, CommonUtils.getErrorMessage(ex), ex);
                    entityContext.ui().sendErrorMessage(ex);
                    if (threadContext.errorListener != null) {
                        try {
                            threadContext.errorListener.accept(ex);
                        } catch (Exception uex) {
                            log.error("Unexpected error in thread error listener <{}>", CommonUtils.getErrorMessage(uex));
                        }
                    }

                    if (threadContext.cancelOnError || threadContext.scheduleType == ScheduleType.SINGLE) {
                        threadContext.cancel();
                    }
                }
            };
        threadContext.scheduledFuture = (ScheduledFuture<T>) scheduleHandler.apply(runnable);
    }

    @RequiredArgsConstructor
    public enum ScheduleType {
        DELAY,
        RATE,
        SINGLE,
        CRON
    }

    @RequiredArgsConstructor
    private static class BatchRunContext<T> {

        private final ThreadPoolExecutor executor;
        private final Map<String, Future<T>> processes = new HashMap<>();
    }

    @Getter
    @NoArgsConstructor
    public class ThreadContextImpl<T> implements ThreadContext<T> {

        private final JSONObject metadata = new JSONObject();
        private final Date creationTime = new Date();
        private Authentication authentication;
        private Path logFile;
        private Duration delay;
        private String name;
        private ThrowingFunction<ThreadContext<T>, T, Exception> command;
        private ScheduleType scheduleType;
        private Duration period;
        private boolean showOnUI = true;
        private boolean hideOnUIAfterCancel = true;
        private T retValue;
        private String error;
        private ScheduledFuture<T> scheduledFuture;
        @Setter private String state;
        @Setter private String description;
        private boolean stopped;
        @Setter private int runCount;
        @Setter private boolean cancelOnError = true;
        private Map<String, ThrowingBiFunction<T, T, Boolean, Exception>> valueListeners;
        private Map<String, ThrowingRunnable<Exception>> simpleWorkUnitListeners;

        // in case if start = false
        public Function<Runnable, ScheduledFuture<?>> postponeScheduleHandler;

        private Consumer<Exception> errorListener;

        public ThreadContextImpl(String name, ThrowingFunction<ThreadContext<T>, T, Exception> command, ScheduleType scheduleType, Duration period,
            boolean showOnUI, boolean hideOnUIAfterCancel) {
            this.name = name;
            this.command = command;
            this.scheduleType = scheduleType;
            this.period = period;
            this.showOnUI = showOnUI;
            this.hideOnUIAfterCancel = hideOnUIAfterCancel;
        }

        @Override
        public String getTimeToNextSchedule() {
            if (scheduledFuture == null || (period == null && delay == null)) {
                return null;
            }
            return Duration.ofMillis(scheduledFuture.getDelay(TimeUnit.MILLISECONDS)).truncatedTo(ChronoUnit.SECONDS).toString().substring(2);
        }

        @Override
        public Object getMetadata(String key) {
            return metadata.get(key);
        }

        @Override
        public void setMetadata(String key, Object value) {
            metadata.put(key, value);
        }

        @Override
        public void cancel() {
            log.info("Cancel process: '{}'", name);
            cancelProcessInternal();
        }

        @Override
        public void reset() {
            createSchedule(this, this.postponeScheduleHandler);
        }

        private ThreadContextImpl<?> cancelProcessInternal() {
            if (scheduledFuture != null) {
                if (scheduledFuture.isCancelled() || scheduledFuture.isDone() || scheduledFuture.cancel(true)) {
                    stopped = true;
                    if (!showOnUI || hideOnUIAfterCancel) {
                        return EntityContextBGPImpl.this.schedulers.remove(name);
                    }
                }
            }
            return null;
        }

        @Override
        @SneakyThrows
        public T await(@NotNull Duration timeout) {
            return scheduledFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public void onError(@NotNull Consumer<Exception> errorListener) {
            this.errorListener = errorListener;
        }

        @Override
        public boolean addValueListener(@NotNull String name, @NotNull ThrowingBiFunction<T, T, Boolean, Exception> valueListener) {
            if (this.valueListeners == null) {
                this.valueListeners = new ConcurrentHashMap<>();
            }
            if (this.valueListeners.containsKey(name)) {
                log.warn("Unable to add run/schedule listener with name <{}> Listener already exists", name);
                return false;
            }
            this.valueListeners.put(name, valueListener);
            return true;
        }

        public void setRetValue(T value) {
            T oldValue = this.retValue;
            this.retValue = value;

            // fire listeners if require
            if (this.valueListeners != null) {
                for (Iterator<ThrowingBiFunction<T, T, Boolean, Exception>> iterator = this.valueListeners.values().iterator(); iterator.hasNext(); ) {
                    ThrowingBiFunction<T, T, Boolean, Exception> fn = iterator.next();
                    try {
                        if (Boolean.TRUE.equals(fn.apply(value, oldValue))) {
                            iterator.remove();
                        }
                    } catch (Exception ex) {
                        log.error("Error while fire listener for run/schedule <" + this.name + ">", ex);
                        iterator.remove();
                    }
                }
                // clean if not require anymore
                if (this.valueListeners.isEmpty()) {
                    this.valueListeners = null;
                }
            }
        }
    }
}
