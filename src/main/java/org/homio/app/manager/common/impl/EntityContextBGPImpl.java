package org.homio.app.manager.common.impl;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import com.pivovarit.function.ThrowingBiConsumer;
import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingConsumer;
import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.ThrowingRunnable;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
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
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.homio.api.EntityContextBGP;
import org.homio.api.exception.ServerException;
import org.homio.api.model.HasEntityIdentifier;
import org.homio.api.service.EntityService.WatchdogService;
import org.homio.api.ui.field.ProgressBar;
import org.homio.api.util.CommonUtils;
import org.homio.app.config.AppProperties;
import org.homio.app.json.BgpProcessResponse;
import org.homio.app.manager.bgp.InternetAvailabilityBgpService;
import org.homio.app.manager.bgp.WatchdogBgpService;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.hquery.HQueryProgressBar;
import org.homio.hquery.LinesReader;
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

    // not all threads may be run inside ThreadContextImpl, so we need addon able to register
    // custom
    // threads to show them on ui
    @Getter
    private final Map<String, Consumer<ThreadPuller>> threadsPullers = new ConcurrentHashMap<>();

    private final Map<String, BatchRunContext<?>> batchRunContextMap = new ConcurrentHashMap<>();

    @Getter
    private final InternetAvailabilityBgpService internetAvailabilityService;
    private final WatchdogBgpService watchdogBgpService;

    public EntityContextBGPImpl(EntityContextImpl entityContext, ThreadPoolTaskScheduler taskScheduler, AppProperties appProperties) {
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
    public <T> @NotNull List<T> runInBatchAndGet(
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
    public void executeOnExit(ThrowingRunnable runnable) {
        executeOnExitImpl(runnable);
    }

    private static void executeOnExitImpl(ThrowingRunnable runnable) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                runnable.run();
            } catch (Exception ignore) {
            }
        }));
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

    @Override
    public <T> @NotNull ScheduleBuilder<T> builder(@NotNull String name) {
        ThreadContextImpl<T> context = new ThreadContextImpl<>();
        context.name = name;
        context.scheduleType = ScheduleType.SINGLE;
        return new ScheduleBuilder<>() {
            private Function<Runnable, ScheduledFuture<?>> scheduleHandler;

            @Override
            public @NotNull ThreadContext<T> execute(@NotNull ThrowingFunction<ThreadContext<T>, T, Exception> command, boolean start) {
                context.command = command;
                if (scheduleHandler == null) {
                    scheduleHandler = runnable -> {
                        Date startDate = new Date();
                        if (context.delay != null) {
                            startDate = new Date(System.currentTimeMillis() + context.delay.toMillis());
                        }
                        if (context.scheduleType == ScheduleType.SINGLE) {
                            return taskScheduler.schedule(runnable, startDate.toInstant());
                        } else {
                            return taskScheduler.scheduleWithFixedDelay(runnable, startDate.toInstant(), context.period);
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
            public @NotNull ThreadContext<Void> execute(@NotNull ThrowingRunnable<Exception> command, boolean start) {
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
            public @NotNull ScheduleBuilder<T> interval(@NotNull String cron) {
                context.scheduleType = ScheduleType.CRON;
                scheduleHandler = runnable -> taskScheduler.schedule(runnable, new CronTrigger(cron));
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> interval(@NotNull Duration duration) {
                context.scheduleType = ScheduleType.DELAY;
                context.period = duration;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> throwOnError(boolean value) {
                context.throwOnError = value;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> onError(@NotNull Consumer<Exception> errorListener) {
                context.errorListener = errorListener;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> metadata(@NotNull String key, @NotNull Object value) {
                context.metadata.put(key, value);
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> tap(Consumer<ThreadContext<T>> handler) {
                handler.accept(context);
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> delay(@NotNull Duration duration) {
                context.delay = duration;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> auth() {
                context.authentication = SecurityContextHolder.getContext().getAuthentication();
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> hideOnUI(boolean value) {
                context.showOnUI = !value;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> hideOnUIAfterCancel(boolean value) {
                context.hideOnUIAfterCancel = value;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> cancelOnError(boolean value) {
                context.cancelOnError = value;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> linkLogFile(@NotNull Path logFile) {
                context.logFile = logFile;
                return this;
            }

            @Override
            public @NotNull ScheduleBuilder<T> valueListener(@NotNull String name, @NotNull ThrowingBiFunction<T, T, Boolean, Exception> valueListener) {
                context.addValueListener(name, valueListener);
                return this;
            }
        };
    }

    @Override
    public @NotNull ProcessBuilder processBuilder(@NotNull String name) {
        ProcessContextImpl processContext = new ProcessContextImpl();
        processContext.name = name;
        return new ProcessBuilder() {

            @Override
            public @NotNull ProcessBuilder logToConsole(boolean value) {
                processContext.logToConsole = value;
                return this;
            }

            @Override
            public @NotNull ProcessBuilder onStarted(@NotNull ThrowingConsumer<ProcessContext, Exception> startedHandler) {
                processContext.startedHandler = startedHandler;
                return this;
            }

            @Override
            public @NotNull ProcessBuilder onFinished(@NotNull ThrowingBiConsumer<@Nullable Exception, @NotNull Integer, Exception> finishHandler) {
                processContext.finishHandler = finishHandler;
                return this;
            }

            @Override
            public @NotNull ProcessContext execute(@NotNull String command) {
                builder(name)
                    .metadata("processContext", processContext)
                    .onError(processContext::onError)
                    .execute(() -> processContext.run(command));
                return processContext;
            }
        };
    }

    @Override
    @SneakyThrows
    public @NotNull ThreadContext<Void> runDirectoryWatchdog(@NotNull Path dir, @NotNull ThrowingConsumer<WatchEvent<Path>, Exception> onUpdateCommand,
        Kind<?>... eventsToListen) {
        String threadKey = "dir-watchdog-" + dir.getFileName().toString();
        if (isThreadExists(threadKey, true)) {
            throw new RuntimeException("Directory already watching");
        }
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Path: " + dir + " is not a directory");
        }
        if (eventsToListen.length == 0) {
            eventsToListen = new Kind<?>[]{ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};
        }
        WatchService watchService = FileSystems.getDefault().newWatchService();
        dir.register(watchService, eventsToListen);

        return builder(threadKey)
            .interval(Duration.ofSeconds(1))
            .execute(() -> {
                WatchKey key;
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        try {
                            onUpdateCommand.accept((WatchEvent<Path>) event);
                        } catch (Exception ex) {
                            log.error("Error during handle on watch directory {}/{}", event.kind(), event.context());
                        }
                    }
                    key.reset();
                }
            });
    }

    @Override
    @SneakyThrows
    public @NotNull ThreadContext<Void> runFileWatchdog(@NotNull Path file, String key, @NotNull ThrowingRunnable<Exception> onUpdateCommand) {
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

    @Override
    public @NotNull ProgressBuilder runWithProgress(@NotNull String key) {
        return new ProgressBuilderImpl(key);
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
                        threadContext.processFinished();
                    }
                } catch (Exception ex) {
                    if (ex instanceof CancellationException) {
                        threadContext.state = "FINISHED";
                        threadContext.processFinished();
                        return;
                    }
                    threadContext.state = "FINISHED_WITH_ERROR";
                    threadContext.error = CommonUtils.getErrorMessage(ex);
                    if (threadContext.throwOnError) {
                        throw new ServerException(threadContext.error);
                    }

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
                        threadContext.processFinished();
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

    @Setter
    @Accessors(chain = true)
    @RequiredArgsConstructor
    private class ProgressBuilderImpl implements ProgressBuilder {

        private @NotNull final String key;
        private boolean cancellable;
        private boolean logToConsole = true;
        private @Nullable Consumer<Exception> onFinally;
        private @Nullable Runnable onError;
        private @Nullable Exception errorIfExists;

        @Override
        public @NotNull ProgressBuilder onFinally(@Nullable Consumer<Exception> finallyBlock) {
            this.onFinally = finallyBlock;
            return this;
        }

        @Override
        public @NotNull ProgressBuilder onError(@Nullable Runnable errorBlock) {
            this.onError = errorBlock;
            return this;
        }

        @Override
        @SneakyThrows
        public <R> @NotNull ThreadContext<R> execute(@NotNull ThrowingFunction<ProgressBar, R, Exception> command) {
            if (errorIfExists != null && isThreadExists(key, true)) {
                throw errorIfExists;
            }
            ScheduleBuilder<R> builder = builder(key);
            return builder.execute(arg -> {
                ProgressBar progressBar = (progress, message) -> {
                    if (logToConsole) {
                        log.info("Progress: {}", message);
                    }
                    getEntityContext().ui().progress(key, progress, message, cancellable);
                };
                progressBar.progress(0, key);
                Exception exception = null;
                try {
                    return command.apply(progressBar);
                } catch (Exception ex) {
                    exception = ex;
                    if (onError != null) {
                        onError.run();
                    }
                } finally {
                    progressBar.done();
                    if (onFinally != null) {
                        onFinally.accept(exception);
                    }
                }
                return null;
            });
        }
    }

    @Getter
    public class ThreadContextImpl<T> implements ThreadContext<T> {

        private final JSONObject metadata = new JSONObject();
        private final Date creationTime = new Date();
        // in case if start = false
        public Function<Runnable, ScheduledFuture<?>> postponeScheduleHandler;
        private boolean throwOnError;
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
        private Consumer<Exception> errorListener;

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
        public void setMetadata(@NotNull String key, @NotNull Object value) {
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

        @Override
        @SneakyThrows
        public T await(@NotNull Duration timeout) {
            scheduledFuture.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return getRetValue();
        }

        @Override
        public boolean addValueListener(@NotNull String name, @NotNull ThrowingBiFunction<T, T, Boolean, Exception> valueListener) {
            if (valueListeners == null) {
                valueListeners = new ConcurrentHashMap<>();
            }
            if (valueListeners.containsKey(name)) {
                log.warn("Unable to add run/schedule listener with name <{}> Listener already exists", name);
                return false;
            }
            valueListeners.put(name, valueListener);
            return true;
        }

        @Override
        public boolean removeValueListener(@NotNull String name) {
            if (valueListeners != null) {
                return valueListeners.remove(name) != null;
            }
            return false;
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

        private void cancelProcessInternal() {
            if (scheduledFuture != null) {
                if (scheduledFuture.isCancelled() || scheduledFuture.isDone() || scheduledFuture.cancel(true)) {
                    processFinished();
                }
            }
        }

        private void processFinished() {
            stopped = true;
            if (!showOnUI || hideOnUIAfterCancel) {
                EntityContextBGPImpl.this.schedulers.remove(name);
            }
        }
    }

    @Getter
    public static class ProcessContextImpl implements ProcessContext {

        private final Date creationTime = new Date();
        public @Nullable ThrowingConsumer<ProcessContext, Exception> startedHandler;
        public @Nullable ThrowingBiConsumer<Exception, Integer, Exception> finishHandler;
        public Process process;
        private boolean logToConsole;
        private String name;
        private Thread inputThread;
        private Thread errorThread;

        @Override
        public boolean isStopped() {
            return process == null || !process.isAlive();
        }

        @Override
        public void cancel() {
            process.destroyForcibly();
            // executeFinishHandler(null, -1, false);
        }

        public void run(@NotNull String command) throws Exception {
            process = Runtime.getRuntime().exec(command);
            executeOnExitImpl(() -> {
                if (process != null) {
                    process.destroyForcibly();
                }
            });
            if (startedHandler != null) {
                try {
                    startedHandler.accept(this);
                } catch (Exception ex) {
                    log.error("Error during process: '{}' start handler. Err: {}", name, CommonUtils.getErrorMessage(ex));
                    throw ex;
                }
            }

            if(logToConsole) {
                HQueryProgressBar progressBar = HQueryProgressBar.of(s -> {});
                inputThread = new Thread(new LinesReader(name + "inputReader", process.getInputStream(), progressBar, false, message ->
                    log.log(message.contains("error") ? Level.ERROR : Level.INFO, "[{}]: {}", name, message)));
                errorThread = new Thread(new LinesReader(name + "errorReader", process.getErrorStream(), progressBar, true, message ->
                    log.error("[{}]: {}", name, message)));
                inputThread.start();
                errorThread.start();
            }

            int responseCode = process.waitFor();
            executeFinishHandler(null, responseCode, true);
        }

        public void onError(Exception ex) {
            executeFinishHandler(ex, -1, true);
        }

        private void executeFinishHandler(Exception ex, int exitCode, boolean callFinishHandler) {
            if (ex != null) {
                log.error("Process '{}' finished with error code: {}. Err: {}", name, exitCode, CommonUtils.getErrorMessage(ex));
            } else {
                log.info("Process '{}' finished with code: {}", name, exitCode);
            }
            if (callFinishHandler && finishHandler != null) {
                try {
                    finishHandler.accept(ex, exitCode);
                } catch (Exception fex) {
                    log.error("Error occurred during finish process: {}", name);
                }
            }
            if (inputThread != null) {
                executeSilently(() -> inputThread.join(100));
            }
            if (errorThread != null) {
                executeSilently(() -> errorThread.join(100));
            }
        }

        private void executeSilently(ThrowingRunnable<Exception> handler) {
            try {
                handler.run();
            } catch (Exception ignore) {
            }
        }
    }
}
