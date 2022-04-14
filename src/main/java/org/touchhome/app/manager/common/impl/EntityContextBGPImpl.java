package org.touchhome.app.manager.common.impl;

import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.ThrowingRunnable;
import com.pivovarit.function.ThrowingSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.model.HasEntityIdentifier;
import org.touchhome.common.util.CommonUtils;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

@Log4j2
public class EntityContextBGPImpl implements EntityContextBGP {

    @Getter
    private final EntityContextImpl entityContext;
    private final ThreadPoolTaskScheduler taskScheduler;

    @Getter
    private final Map<String, ThreadContextImpl<?>> schedulers = new ConcurrentHashMap<>();

    // not all threads may be run inside ThreadContextImpl so we need bundle able to register custom threads to show them on ui
    @Getter
    private final Map<String, Consumer<ThreadPuller>> threadsPullers = new ConcurrentHashMap<>();

    private final Map<String, BatchRunContext<?>> batchRunContextMap = new ConcurrentHashMap<>();

    private ThreadContext<Boolean> internetThreadContext;

    public EntityContextBGPImpl(EntityContextImpl entityContext, TouchHomeProperties touchHomeProperties,
                                ThreadPoolTaskScheduler taskScheduler) {
        this.entityContext = entityContext;
        this.taskScheduler = taskScheduler;
        listenInternetStatus(entityContext, touchHomeProperties);
    }

    @Override
    public ThreadContext<Void> schedule(@NotNull String name, int initialDelayInMillis, int timeout, @NotNull TimeUnit timeUnit,
                                        @NotNull ThrowingRunnable<Exception> command, boolean showOnUI,
                                        boolean hideOnUIAfterCancel) {
        return addSchedule(name, initialDelayInMillis, timeout, timeUnit, context -> {
            command.run();
            return null;
        }, showOnUI, hideOnUIAfterCancel);
    }

    @Override
    public void runOnceOnInternetUp(@NotNull String name, @NotNull ThrowingRunnable<Exception> command) {
        this.internetThreadContext.addValueListener(name, (isInternetUp, ignore) -> {
            if (isInternetUp) {
                log.info("Internet up. Run <" + name + "> listener.");
                try {
                    command.run();
                } catch (Exception ex) {
                    log.error("Error occurs while run command: " + name, ex);
                }
                return true;
            }
            return false;
        });
    }

    @Override
    public <T> ThreadContext<T> runAndGet(@NotNull String name, @Nullable Long initialDelayInMillis,
                                          @NotNull ThrowingSupplier<T, Exception> command, boolean showOnUI) {
        return addSchedule(name, initialDelayInMillis, threadContext -> command.get(),
                EntityContextBGPImpl.ScheduleType.SINGLE, showOnUI, true,
                runnable -> taskScheduler.schedule(runnable, initialDelayInMillis == null ?
                        new Date() : new Date(System.currentTimeMillis() + initialDelayInMillis)));
    }

    @Override
    public ThreadContext<Void> runInfinite(@NotNull String name, @NotNull ThrowingRunnable<Exception> command,
                                           boolean showOnUI, int delay, boolean stopOnException) {
        return new ThreadContextImpl<>(name, context -> {
            while (!context.isStopped())
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        command.run();
                        Thread.sleep(delay);
                    }
                } catch (Exception ex) {
                    if (stopOnException) {
                        context.cancel();
                    }
                }
            return null;
        }, EntityContextBGPImpl.ScheduleType.SINGLE, null, showOnUI, true);
    }

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
    public void registerThreadsPuller(String entityId, Consumer<ThreadPuller> threadPullerConsumer) {
        this.threadsPullers.put(entityId, threadPullerConsumer);
    }

    @Override
    public <P extends HasEntityIdentifier, T> void runInBatch(@NotNull String batchName, int maxTerminateTimeoutInSeconds,
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
        run("batch-wait_" + batchName, () -> {
            List<T> result = waitBatchToDone(batchName, maxTerminateTimeoutInSeconds, progressConsumer, batchRunContext);
            finallyProcessBlockHandler.accept(result);
        }, true);
    }

    @SneakyThrows
    @Override
    public <T> List<T> runInBatchAndGet(@NotNull String batchName, int maxTerminateTimeoutInSeconds,
                                        int threadsCount, @NotNull Map<String, Callable<T>> runnableTasks,
                                        @NotNull Consumer<Integer> progressConsumer) {
        BatchRunContext<T> batchRunContext = prepareBatchProcessContext(batchName, threadsCount);
        for (Map.Entry<String, Callable<T>> entry : runnableTasks.entrySet()) {
            batchRunContext.processes.put(entry.getKey(), batchRunContext.executor.submit(entry.getValue()));
        }
        return waitBatchToDone(batchName, maxTerminateTimeoutInSeconds, progressConsumer, batchRunContext);
    }

    @Override
    public boolean isThreadExists(@NotNull String name, boolean checkOnlyRunningThreads) {
        ThreadContextImpl<?> threadContext = this.schedulers.get(name);
        if (checkOnlyRunningThreads) {
            return threadContext != null && !threadContext.stopped;
        }
        return threadContext != null;
    }

    private <T> EntityContextBGPImpl.BatchRunContext<T> prepareBatchProcessContext(String batchName, int threadsCount) {
        if (batchRunContextMap.containsKey(batchName)) {
            throw new IllegalStateException("Batch processes with name " + batchName + " already in progress");
        }
        ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(threadsCount);
        BatchRunContext<T> batchRunContext = new BatchRunContext<>(executor);
        batchRunContextMap.put(batchName, batchRunContext);
        return batchRunContext;
    }

    private <T> List<T> waitBatchToDone(String batchName, int maxTerminateTimeoutInSeconds,
                                        Consumer<Integer> progressConsumer, BatchRunContext<T> batchRunContext) throws InterruptedException, ExecutionException {
        ThreadPoolExecutor executor = batchRunContext.executor;
        executor.shutdown();
        int maxTerminateTimeoutInMilliseconds = maxTerminateTimeoutInSeconds * 1000;
        long batchStarted = System.currentTimeMillis();
        try {
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                    long completedTaskCount = executor.getCompletedTaskCount();
                    progressConsumer.accept((int) completedTaskCount);

                    if (System.currentTimeMillis() - batchStarted > maxTerminateTimeoutInMilliseconds) {
                        log.warn("Exceeded await limit for batch run <{}> (Max {} sec.)", batchName, maxTerminateTimeoutInSeconds);
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

    private <T> ThreadContext<T> addSchedule(String name, int initialDelayInMillis, int timeout, TimeUnit timeUnit,
                                             ThrowingFunction<ThreadContext<T>, T, Exception> command,
                                             boolean showOnUI, boolean hideOnUIAfterCancel) {
        return addSchedule(name, timeUnit.toMillis(timeout), command, EntityContextBGPImpl.ScheduleType.DELAY,
                showOnUI, hideOnUIAfterCancel, runnable -> {
                    Date startDate = new Date(System.currentTimeMillis() + initialDelayInMillis);
                    return taskScheduler.scheduleWithFixedDelay(runnable, startDate, timeUnit.toMillis(timeout));
                });
    }

    @Override
    public <T> ThreadContext<T> schedule(@NotNull String name, @NotNull String cron,
                                         @NotNull ThrowingFunction<ThreadContext<T>, T, Exception> command,
                                         boolean showOnUI, boolean hideOnUIAfterCancel) {
        return addSchedule(name, -1L, command, ScheduleType.CRON, showOnUI, hideOnUIAfterCancel,
                runnable -> taskScheduler.schedule(runnable, new CronTrigger(cron)));
    }

    private <T> ThreadContext<T> addSchedule(@NotNull String name, Long period, @NotNull ThrowingFunction<ThreadContext<T>, T, Exception> command,
                                             @NotNull ScheduleType scheduleType, boolean showOnUI, boolean hideOnUIAfterCancel,
                                             @NotNull Function<Runnable, ScheduledFuture<?>> scheduleHandler) {

        this.cancelThread(name);
        ThreadContextImpl<T> threadContext = new ThreadContextImpl<>(name, command, scheduleType, period, showOnUI, hideOnUIAfterCancel);
        this.schedulers.put(name, threadContext);

        Runnable runnable = () -> {
            try {
                threadContext.runCount++;
                threadContext.state = "STARTED";
                threadContext.setRetValue(threadContext.getCommand().apply(threadContext));
                threadContext.state = "FINISHED";
            } catch (Exception ex) {
                threadContext.state = "FINISHED_WITH_ERROR";
                threadContext.stopped = true;
                threadContext.error = CommonUtils.getErrorMessage(ex);

                log.error("Exception in thread: <{}>. Message: <{}>", name, CommonUtils.getErrorMessage(ex), ex);
                entityContext.ui().sendErrorMessage(ex);
                if (threadContext.errorListener != null) {
                    try {
                        threadContext.errorListener.accept(ex);
                    } catch (Exception uex) {
                        log.error("Unexpected error in thread error listener <{}>", CommonUtils.getErrorMessage(uex));
                    }
                }
            }
        };
        threadContext.scheduledFuture = (ScheduledFuture<T>) scheduleHandler.apply(runnable);
        return threadContext;
    }

    private void listenInternetStatus(EntityContextImpl entityContext, TouchHomeProperties touchHomeProperties) {
        this.internetThreadContext = this.addSchedule("internet-test", 10, 10, TimeUnit.SECONDS, context -> {
            return getEntityContext().googleConnect() != null;
        }, true, false);

        this.internetThreadContext.addValueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
            if (isInternetUp != isInternetWasUp) {
                entityContext.event().fireEvent(isInternetUp ? "internet-up" : "internet-down");
                if (isInternetUp) {
                    entityContext.ui().removeBellNotification("internet-connection");
                    entityContext.ui().addBellInfoNotification("internet-connection", "Internet Connection", "Internet up");
                } else {
                    entityContext.ui().removeBellNotification("internet-up");
                    entityContext.ui().addBellErrorNotification("internet-connection", "Internet Connection", "Internet down");
                }
            }
            return null;
        });
    }

    @RequiredArgsConstructor
    public enum ScheduleType {
        DELAY, RATE, SINGLE, CRON
    }

    @RequiredArgsConstructor
    private static class BatchRunContext<T> {

        private final ThreadPoolExecutor executor;
        private Map<String, Future<T>> processes = new HashMap<>();
    }

    @Getter
    @RequiredArgsConstructor
    public class ThreadContextImpl<T> implements ThreadContext<T> {
        private final String name;
        private final ThrowingFunction<ThreadContext<T>, T, Exception> command;
        private final ScheduleType scheduleType;
        private final Long period;
        private final boolean showOnUI;
        private final boolean hideOnUIAfterCancel;
        private T retValue;
        private String error;
        private ScheduledFuture<T> scheduledFuture;
        @Setter
        private String state;
        @Setter
        private String description;
        private boolean stopped;
        @Setter
        private int runCount;
        private Date creationTime = new Date();
        private Map<String, ThrowingBiFunction<T, T, Boolean, Exception>> valueListeners;

        private Consumer<Exception> errorListener;

        public Long getTimeToNextSchedule() {
            if (period == null) {
                return null;
            }
            return scheduledFuture.getDelay(TimeUnit.MILLISECONDS) / 1000;
        }

        @Override
        public void cancel() {
            if (scheduledFuture.isCancelled() || scheduledFuture.isDone() || scheduledFuture.cancel(true)) {
                stopped = true;
                if (!showOnUI || hideOnUIAfterCancel) {
                    EntityContextBGPImpl.this.schedulers.remove(name);
                }
            }
        }

        @Override
        public T await(long timeout, @NotNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return scheduledFuture.get(timeout, timeUnit);
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
                log.warn("Unable to add run/schedule listener with name <" + name + "> Listener already exists");
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
