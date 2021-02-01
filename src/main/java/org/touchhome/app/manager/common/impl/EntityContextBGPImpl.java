package org.touchhome.app.manager.common.impl;

import com.pivovarit.function.ThrowingBiFunction;
import com.pivovarit.function.ThrowingFunction;
import com.pivovarit.function.ThrowingRunnable;
import com.pivovarit.function.ThrowingSupplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.bundle.api.EntityContextBGP;

import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.touchhome.bundle.api.util.TouchHomeUtils.getErrorMessage;

@Log4j2
public class EntityContextBGPImpl implements EntityContextBGP {
    private final ScheduledExecutorService scheduleService = Executors.newScheduledThreadPool(1000, r -> new Thread(r, "entity-manager"));

    @Getter
    private final EntityContextImpl entityContext;

    @Getter
    private final Map<String, ThreadContextImpl<?>> schedulers = new HashMap<>();

    private ThreadContext<Boolean> internetThreadContext;

    public EntityContextBGPImpl(EntityContextImpl entityContext, TouchHomeProperties touchHomeProperties) {
        this.entityContext = entityContext;
        ((ScheduledThreadPoolExecutor) this.scheduleService).setRemoveOnCancelPolicy(true);
        listenInternetStatus(entityContext, touchHomeProperties);
    }

    @Override
    public ThreadContext<Void> schedule(String name, int timeout, TimeUnit timeUnit, ThrowingRunnable<Exception> command, boolean showOnUI) {
        return addSchedule(name, timeout, timeUnit, command, showOnUI);
    }

    @Override
    public void runOnceOnInternetUp(String name, ThrowingRunnable<Exception> command) {
        this.internetThreadContext.addValueListener(name, (isInternetUp, ignore) -> {
            if (isInternetUp) {
                log.info("Internet up. Run <" + name + "> listener.");
                command.run();
                return true;
            }
            return false;
        });
    }

    @Override
    public <T> ThreadContext<T> run(String name, ThrowingSupplier<T, Exception> command, boolean showOnUI) {
        return addSchedule(name, 0, TimeUnit.MILLISECONDS, threadContext -> command.get(), EntityContextBGPImpl.ScheduleType.SINGLE, showOnUI);
    }

    @Override
    public ThreadContext<Void> runInfinite(String name, ThrowingRunnable<Exception> command, boolean showOnUI, int delay, boolean stopOnException) {
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
        }, EntityContextBGPImpl.ScheduleType.SINGLE, null, showOnUI);
    }

    @Override
    public void cancelThread(String name) {
        if (name != null) {
            ThreadContextImpl<?> context = this.schedulers.remove(name);
            if (context != null) {
                context.cancel();
            }
        }
    }

    @Override
    public boolean isThreadExists(String name, boolean checkOnlyRunningThreads) {
        ThreadContextImpl<?> threadContext = this.schedulers.get(name);
        if (checkOnlyRunningThreads) {
            return threadContext != null && !threadContext.stopped;
        }
        return threadContext != null;
    }

    private ThreadContext<Void> addSchedule(String name, int timeout, TimeUnit timeUnit, ThrowingRunnable<Exception> command, boolean showOnUI) {
        return addSchedule(name, timeout, timeUnit, voidThreadContext -> {
            command.run();
            return null;
        }, EntityContextBGPImpl.ScheduleType.DELAY, showOnUI);
    }

    private <T> ThreadContext<T> addSchedule(String name, int timeout, TimeUnit timeUnit, ThrowingFunction<ThreadContext<T>, T, Exception> command,
                                             ScheduleType scheduleType, boolean showOnUI) {
        this.cancelThread(name);
        ThreadContextImpl<T> threadContext = new ThreadContextImpl<T>(name, command, scheduleType, timeout > 0 ? timeUnit.toMillis(timeout) : null, showOnUI);
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

                log.error("Exception in thread: <{}>. Message: <{}>", name, getErrorMessage(ex));
                entityContext.ui().sendErrorMessage(ex);
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

    private void listenInternetStatus(EntityContextImpl entityContext, TouchHomeProperties touchHomeProperties) {
        this.internetThreadContext = this.addSchedule("internet-test", 10, TimeUnit.SECONDS, booleanThreadContext -> {
            try {
                URLConnection connection = new URL(touchHomeProperties.getCheckConnectivityURL()).openConnection();
                connection.connect();
                return true;
            } catch (Exception ex) {
                return false;
            }
        }, ScheduleType.DELAY, true);

        this.internetThreadContext.addValueListener("internet-hardware-event", (isInternetUp, isInternetWasUp) -> {
            if (isInternetUp != isInternetWasUp) {
                entityContext.event().fireEvent(isInternetUp ? "internet-up" : "internet-down");
            }
            return null;
        });
    }

    @RequiredArgsConstructor
    public enum ScheduleType {
        DELAY, RATE, SINGLE
    }

    @Getter
    @RequiredArgsConstructor
    public static class ThreadContextImpl<T> implements ThreadContext<T> {
        private final String name;
        private final ThrowingFunction<ThreadContext<T>, T, Exception> command;
        private final ScheduleType scheduleType;
        private final Long period;
        private final boolean showOnUI;
        public T retValue;
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
            }
        }

        @Override
        public T await(long timeout, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
            return scheduledFuture.get(timeout, timeUnit);
        }

        @Override
        public void onError(Consumer<Exception> errorListener) {
            this.errorListener = errorListener;
        }

        @Override
        public boolean addValueListener(String name, ThrowingBiFunction<T, T, Boolean, Exception> valueListener) {
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
