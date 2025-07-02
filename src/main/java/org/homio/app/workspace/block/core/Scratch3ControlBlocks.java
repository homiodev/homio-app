package org.homio.app.workspace.block.core;

import com.pivovarit.function.ThrowingRunnable;
import lombok.Getter;
import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.ContextBGP.ThreadContext;
import org.homio.api.exception.ServerException;
import org.homio.api.state.DecimalType;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.workspace.WorkspaceBlockImpl;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.HOURS;

@Getter
@Component
public class Scratch3ControlBlocks extends Scratch3ExtensionBlocks {

    public Scratch3ControlBlocks(Context context) {
        super("control", context);

        blockCommand("forever", this::foreverHandler);
        blockCommand("schedule", this::repeatEveryTimeScheduleHandler);
        blockCommand("schedule_cron", this::scheduleCronHandler);
        blockCommand("repeat", this::repeatHandler);
        blockCommand("if", this::ifHandler);
        blockCommand("if_else", this::ifElseHandler);
        blockCommand("stop", this::stopHandler);
        blockCommand("stop_timeout", this::stopInTimeoutHandler);

        blockCommand("wait", this::waitHandler);
        blockCommand("wait_until", this::waitUntilHandler);
        blockCommand("repeat_until", this::repeatUntilHandler);
        blockCommand("when_condition_changed", this::whenConditionChangedHandler);
        blockCommand("when_value_changed", this::whenValueChangedHandler);
        blockCommand("when_time_between", this::whenTimeBetweenHandler);
    }

    private void scheduleCronHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasChild()) {
            WorkspaceBlock child = workspaceBlock.getChild();
            String cron =
                    workspaceBlock.getInputString("SEC")
                    + " "
                    + workspaceBlock.getInputString("MIN")
                    + " "
                    + workspaceBlock.getInputString("HOUR")
                    + " "
                    + workspaceBlock.getInputString("DAY")
                    + " "
                    + workspaceBlock.getInputString("MONTH")
                    + " "
                    + workspaceBlock.getInputString("DOW");
            AtomicInteger index = new AtomicInteger();
            context
                    .bgp()
                    .builder("workspace-schedule-cron-" + workspaceBlock.getId())
                    .tap(workspaceBlock::setThreadContext)
                    .interval(cron)
                    .execute(
                            () -> {
                                workspaceBlock.setActiveWorkspace();
                                workspaceBlock.setValue("INDEX", new DecimalType(index.getAndIncrement()));
                                child.handle();
                            });
        }
    }

    @SneakyThrows
    private void repeatEveryTimeScheduleHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasChild()) {
            Integer time = workspaceBlock.getInputInteger("TIME");
            TimeUnit timeUnit = TimeUnit.valueOf(workspaceBlock.getField("UNIT"));
            WorkspaceBlock child = workspaceBlock.getChild();
            buildSchedule("everytime", workspaceBlock, timeUnit.toMillis(time), child::handle);
        }
    }

    private void whenTimeBetweenHandler(WorkspaceBlock workspaceBlock) {
        if (!workspaceBlock.hasChild()) {
            return;
        }

        Duration from = getTimeMilliseconds(workspaceBlock, "FROM");
        Duration to = getTimeMilliseconds(workspaceBlock, "TO");
        Set<DayOfWeek> validDays = getValidDays(workspaceBlock);
        LocalDateTime now = LocalDateTime.now();

        // First check if we should execute immediately
        if (shouldExecuteNow(now, from, to, validDays)) {
            Duration remainingTime = calculateRemainingTime(now, from, to);
            if (!remainingTime.isZero() && !remainingTime.isNegative()) {
                runWhenTimeInRange(workspaceBlock, remainingTime);
            }
        }

        // Schedule next execution
        scheduleNextExecution(workspaceBlock, now, from, to, validDays);
    }

    private boolean shouldExecuteNow(LocalDateTime now, Duration from, Duration to, Set<DayOfWeek> validDays) {
        if (!validDays.contains(now.getDayOfWeek())) {
            return false;
        }

        LocalTime nowTime = now.toLocalTime();
        LocalTime fromTime = LocalTime.of(from.toHoursPart(), from.toMinutesPart());
        LocalTime toTime = LocalTime.of(to.toHoursPart(), to.toMinutesPart());

        if (fromTime.isBefore(toTime)) {
            // Same day range
            return !nowTime.isBefore(fromTime) && nowTime.isBefore(toTime);
        } else {
            // Cross-day range (e.g., 22:00-06:00)
            return !nowTime.isBefore(fromTime) || nowTime.isBefore(toTime);
        }
    }

    private Duration calculateRemainingTime(LocalDateTime now, Duration from, Duration to) {
        LocalTime nowTime = now.toLocalTime();
        LocalTime fromTime = LocalTime.of(from.toHoursPart(), from.toMinutesPart());
        LocalTime toTime = LocalTime.of(to.toHoursPart(), to.toMinutesPart());

        if (fromTime.isBefore(toTime)) {
            // Same-day range (e.g., 08:00-17:00)
            if (nowTime.isBefore(fromTime)) {
                // Before range starts - shouldn't happen if called from shouldExecuteNow
                return Duration.ZERO;
            } else if (nowTime.isBefore(toTime)) {
                // Within range
                return Duration.between(nowTime, toTime);
            } else {
                // After range ends - shouldn't happen if called from shouldExecuteNow
                return Duration.ZERO;
            }
        } else {
            // Cross-day range (e.g., 22:00-06:00)
            if (nowTime.isBefore(toTime)) {
                // In the "next day" portion of the range (00:00-06:00)
                return Duration.between(nowTime, toTime);
            } else if (nowTime.isBefore(fromTime)) {
                // Outside range (06:00-22:00)
                return Duration.ZERO;
            } else {
                // In the "current day" portion of the range (22:00-23:59)
                Duration untilMidnight = Duration.between(nowTime, LocalTime.MAX);
                Duration fromMidnight = Duration.between(LocalTime.MIN, toTime);
                return untilMidnight.plus(fromMidnight);
            }
        }
    }

    private void scheduleNextExecution(WorkspaceBlock workspaceBlock, LocalDateTime now,
                                       Duration from, Duration to, Set<DayOfWeek> validDays) {
        LocalTime fromTime = LocalTime.of(from.toHoursPart(), from.toMinutesPart());
        LocalDateTime nextExecution = now.with(fromTime).truncatedTo(ChronoUnit.SECONDS);

        if (now.toLocalTime().isAfter(fromTime)) {
            nextExecution = nextExecution.plusDays(1);
        }

        // Find next valid day
        while (!validDays.contains(nextExecution.getDayOfWeek())) {
            nextExecution = nextExecution.plusDays(1);
        }

        Duration initialDelay = Duration.between(now, nextExecution);
        Duration timeToExecute = Duration.ofMillis(getExecutionTTL(workspaceBlock, from, to));

        context.bgp()
                .builder("when-time-in-range" + workspaceBlock.getId())
                .tap(workspaceBlock::setThreadContext)
                .delay(initialDelay)
                .interval(Duration.ofDays(1))
                .execute(() -> {
                    if (validDays.contains(LocalDateTime.now().getDayOfWeek())) {
                        runWhenTimeInRange(workspaceBlock, timeToExecute);
                    }
                });
    }

    private @NotNull Set<DayOfWeek> getValidDays(WorkspaceBlock workspaceBlock) {
        var days = Arrays.stream(workspaceBlock.getField("DAY").split("-->")).toList();
        boolean anyDay = days.contains("ANY");
        if (anyDay) {
            return Set.of(DayOfWeek.values());
        }
        return days.stream()
                .map(String::trim)
                .map(String::toUpperCase)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toSet());
    }

    private void runWhenTimeInRange(WorkspaceBlock workspaceBlock, Duration timeToExecute) {
        workspaceBlock.logInfo("Fire when-time-execution. Duration: {}", timeToExecute);
        AtomicInteger index = new AtomicInteger(0);
        ThreadContext<Void> executeContext =
                context
                        .bgp()
                        .builder("when-time-execution" + workspaceBlock.getId())
                        .interval(Duration.ofMillis(100))
                        .execute(
                                () -> {
                                    workspaceBlock.setActiveWorkspace();
                                    workspaceBlock.setValue("INDEX", new DecimalType(index.getAndIncrement()));
                                    workspaceBlock.getChild().handle();
                                });

        ThreadContext<Void> threadKiller =
                context
                        .bgp()
                        .builder("when-time-execution-killer-" + workspaceBlock.getId())
                        .delay(timeToExecute)
                        .execute(
                                () -> {
                                    workspaceBlock.logInfo("Cancelling thread: {}", executeContext.getName());
                                    executeContext.cancel();
                                    // reset active to 'when-time-in-range' thread
                                    workspaceBlock.setActiveWorkspace();
                                });
        workspaceBlock.onRelease(executeContext::cancel);
        workspaceBlock.onRelease(threadKiller::cancel);
    }

    private long getExecutionTTL(WorkspaceBlock workspaceBlock, Duration from, Duration to) {
        int diff = to.compareTo(from);
        long executionTTL = 0;
        if (diff == 0) {
            workspaceBlock.logErrorAndThrow("'From' time must be different to 'To' time");
        } else if (diff > 0) {
            executionTTL = to.minus(from).toMillis();
        } else {
            executionTTL = TimeUnit.DAYS.toMillis(1) - from.minus(to).toMillis();
        }
        return executionTTL;
    }

    private Duration getTimeMilliseconds(WorkspaceBlock workspaceBlock, String key) {
        String[] items = workspaceBlock.getField(key).split(":");
        return Duration.ofMinutes(HOURS.toMinutes(Integer.parseInt(items[0])) + Integer.parseInt(items[1]));
    }

    private void whenValueChangedHandler(WorkspaceBlock workspaceBlock) {
        AtomicReference<Object> ref = new AtomicReference<>();
        lockForEvent(workspaceBlock, VALUE, () -> {
            Object value = workspaceBlock.getInput(VALUE, true);
            if (!Objects.equals(ref.get(), value)) {
                ref.set(value);
                return true;
            }
            return false;
        });
    }

    private void whenConditionChangedHandler(WorkspaceBlock workspaceBlock) {
        AtomicReference<Boolean> ref = new AtomicReference<>();
        lockForEvent(workspaceBlock, CONDITION, () -> {
            if (!Objects.equals(ref.get(), workspaceBlock.getInputBoolean(CONDITION))) {
                ref.set(!ref.get());
                return true;
            }
            return false;
        });
    }

    private void lockForEvent(WorkspaceBlock workspaceBlock, String inputName, Supplier<Boolean> supplier) {
        workspaceBlock.handleChildOptional(
                child -> {
                    if (workspaceBlock.hasInput(inputName)) {
                        Lock lock = workspaceBlock.getLockManager().listenEvent(workspaceBlock, supplier);
                        workspaceBlock.subscribeToLock(lock, child::handle);
                    }
                });
    }

    private void repeatUntilHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleChildOptional(
                child -> {
                    if (workspaceBlock.hasInput(CONDITION)) {
                        buildSchedule("until", workspaceBlock, 0,
                                () -> {
                                    if (workspaceBlock.getInputBoolean(CONDITION)) {
                                        child.handle();
                                    } else {
                                        throw new CancellationException();
                                    }
                                });
                    }
                });
    }

    @SneakyThrows
    private void waitUntilHandler(WorkspaceBlock workspaceBlock) {
        Lock lock = workspaceBlock.getLockManager().listenEvent(workspaceBlock, () -> workspaceBlock.getInputBoolean(CONDITION));
        lock.await(workspaceBlock);
    }

    private void stopHandler(WorkspaceBlock workspaceBlock) {
        ((WorkspaceBlockImpl) workspaceBlock).release();
        Thread.currentThread().interrupt();
    }

    private void stopInTimeoutHandler(WorkspaceBlock workspaceBlock) {
        int timeout = workspaceBlock.getInputInteger("TIMES");
        TimeUnit timeUnit = TimeUnit.valueOf(workspaceBlock.getField("UNIT"));
        Thread thread = Thread.currentThread();
        // set minimum as 100ms
        Duration stopInDuration = Duration.ofMillis(Math.max(100, timeUnit.toMillis(timeout)));
        context
                .bgp()
                .builder("stop-in-timeout" + workspaceBlock.getId())
                .tap(workspaceBlock::setThreadContext)
                .interval(stopInDuration)
                .execute(
                        () -> {
                            if (thread.isAlive()) {
                                ((WorkspaceBlockImpl) workspaceBlock).release();
                                thread.interrupt();
                            }
                        });
    }

    private void ifElseHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasChild()
            && workspaceBlock.hasInput("SUBSTACK2")
            && workspaceBlock.hasInput(CONDITION)) {
            if (workspaceBlock.getInputBoolean(CONDITION)) {
                workspaceBlock.getChild().handle();
            } else {
                workspaceBlock.getInputWorkspaceBlock("SUBSTACK2").handle();
            }
        }
    }

    private void ifHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleChildOptional(
                child -> {
                    if (workspaceBlock.hasInput(CONDITION)
                        && workspaceBlock.getInputBoolean(CONDITION)) {
                        child.handle();
                    }
                });
    }

    private void repeatHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleChildOptional(
                child -> {
                    AtomicInteger times = new AtomicInteger(workspaceBlock.getInputInteger("TIMES"));
                    if (times.get() > 0) {
                        buildSchedule("ntimes", workspaceBlock, 0, () -> {
                            child.handle();
                            if (times.decrementAndGet() <= 0) {
                                throw new CancellationException();
                            }
                        });
                    }
                });
    }

    /**
     *
     */
    private void buildSchedule(String name, WorkspaceBlock workspaceBlock, long timeout, ThrowingRunnable<Exception> handler) {
        AtomicInteger index = new AtomicInteger(0);
        context
                .bgp()
                .builder("workspace-schedule-" + name + "-" + workspaceBlock.getId())
                .interval(Duration.ofMillis(Math.max(100, (int) timeout)))
                .tap(workspaceBlock::setThreadContext)
                .execute(
                        () -> {
                            workspaceBlock.setActiveWorkspace();
                            workspaceBlock.setValue("INDEX", new DecimalType(index.getAndIncrement()));
                            handler.run();
                        });
    }

    @SneakyThrows
    private void waitHandler(WorkspaceBlock workspaceBlock) {
        int duration = workspaceBlock.getInputInteger("DURATION");
        if (duration < 1 || duration > 3600) {
            throw new ServerException("Unable to sleep current block, because duration is more than 1 hour. Actual value is: " + duration);
        }
        TimeUnit.SECONDS.sleep(duration);
    }

    @SneakyThrows
    private void foreverHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleChildOptional(
                child -> buildSchedule("forever", workspaceBlock, 0, child::handle));
    }
}
