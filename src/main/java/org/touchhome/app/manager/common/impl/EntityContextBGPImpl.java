package org.touchhome.app.manager.common.impl;

import com.pivovarit.function.ThrowingRunnable;
import com.pivovarit.function.ThrowingSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContextBGP;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.touchhome.bundle.api.util.TouchHomeUtils.getErrorMessage;

@Log4j2
public class EntityContextBGPImpl implements EntityContextBGP {
    private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1000, r -> new Thread(r, "entity-manager"));

    @Getter
    private final Map<String, ThreadContextImpl> schedulers = new HashMap<>();

    public EntityContextBGPImpl() {
        ((ScheduledThreadPoolExecutor) this.scheduleService).setRemoveOnCancelPolicy(true);
    }

    @Override
    public ThreadContext<Void> schedule(String name, int timeout, TimeUnit timeUnit, ThrowingRunnable<Exception> command, boolean showOnUI) {
        return addSchedule(name, timeout, timeUnit, command, showOnUI);
    }

    @Override
    public <T> ThreadContext<T> run(String name, ThrowingSupplier<T, Exception> command, boolean showOnUI) {
        return addSchedule(name, 0, TimeUnit.MILLISECONDS, command, EntityContextBGPImpl.ScheduleType.SINGLE, showOnUI);
    }

    @Override
    public void cancelThread(String name) {
        if (name != null) {
            ThreadContextImpl context = this.schedulers.remove(name);
            if (context != null) {
                context.cancel();
            }
        }
    }

    @Override
    public boolean isThreadExists(String name) {
        return this.schedulers.containsKey(name);
    }

    public ThreadContext<Void> addSchedule(String name, int timeout, TimeUnit timeUnit, ThrowingRunnable<Exception> command, boolean showOnUI) {
        return addSchedule(name, timeout, timeUnit, () -> {
            command.run();
            return null;
        }, EntityContextBGPImpl.ScheduleType.DELAY, showOnUI);
    }

    public <T> ThreadContext<T> addSchedule(String name, int timeout, TimeUnit timeUnit, ThrowingSupplier<T, Exception> command,
                                            ScheduleType scheduleType, boolean showOnUI) {
        this.cancelThread(name);
        ThreadContextImpl<T> threadContext = new ThreadContextImpl<T>(name, command, scheduleType, timeout > 0 ? timeUnit.toMillis(timeout) : null, showOnUI);
        this.schedulers.put(name, threadContext);

        Runnable runnable = () -> {
            try {
                threadContext.runCount++;
                threadContext.state = "STARTED";
                threadContext.getCommand().get();
                threadContext.state = "FINISHED";
            } catch (Exception ex) {
                log.error("Exception in thread: <{}>. Message: <{}>", name, getErrorMessage(ex));
                if (threadContext.errorListener != null) {
                    try {
                        threadContext.errorListener.accept(ex);
                    } catch (Exception uex) {
                        log.error("Unexpected error in thread error listener <{}>", getErrorMessage(uex));
                    }
                }
            }
        };
        ScheduledFuture<?> scheduledFuture = null;
        switch (scheduleType) {
            case DELAY:
                scheduledFuture = scheduleService.scheduleWithFixedDelay(runnable, 0, timeout, timeUnit);
                break;
            case RATE:
                scheduledFuture = scheduleService.scheduleAtFixedRate(runnable, 0, timeout, timeUnit);
                break;
            case SINGLE:
                scheduledFuture = scheduleService.schedule(runnable, 0, TimeUnit.MILLISECONDS);
                break;
        }
        threadContext.scheduledFuture = (ScheduledFuture<T>) scheduledFuture;
        return threadContext;
    }

    @RequiredArgsConstructor
    public enum ScheduleType {
        DELAY, RATE, SINGLE
    }

    @Getter
    @RequiredArgsConstructor
    public static class ThreadContextImpl<T> implements ThreadContext<T> {
        private final String name;
        private final ThrowingSupplier<T, Exception> command;
        private final ScheduleType scheduleType;
        private final Long period;
        private final boolean showOnUI;
        private ScheduledFuture<T> scheduledFuture;
        @Setter
        private String state;
        @Setter
        private String description;
        private boolean stopped;
        @Setter
        private int runCount;
        @Getter
        private Date creationTime = new Date();

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
            }
        }

        @Override
        public T await(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return scheduledFuture.get(timeout, timeUnit);
        }

        public void onError(Consumer<Exception> errorListener) {
            this.errorListener = errorListener;
        }
    }
}
