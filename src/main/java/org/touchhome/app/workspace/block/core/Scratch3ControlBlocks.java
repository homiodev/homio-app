package org.touchhome.app.workspace.block.core;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.touchhome.bundle.api.workspace.scratch.BlockType.command;
import static org.touchhome.bundle.api.workspace.scratch.Scratch3Block.CONDITION;

import com.pivovarit.function.ThrowingRunnable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.WorkspaceBlockImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP.ThreadContext;
import org.touchhome.bundle.api.state.DecimalType;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.common.exception.ServerException;

@Getter
@Component
public class Scratch3ControlBlocks extends Scratch3ExtensionBlocks {

  private final Scratch3Block foreverBlock;
  private final Scratch3Block waitBlock;
  private final Scratch3Block repeatBlock;
  private final Scratch3Block ifBlock;
  private final Scratch3Block ifElseBlock;
  private final Scratch3Block stopBlock;
  private final Scratch3Block stopInTimeoutBlock;
  private final Scratch3Block waitUntilBlock;
  private final Scratch3Block repeatUntilBlock;
  private final Scratch3Block whenConditionChangedBlock;
  private final Scratch3Block whenValueChangedBlock;
  private final Scratch3Block scheduleBlock;

  private final Scratch3Block scheduleCronBlock;
  private final Scratch3Block whenTimeBetweenBlock;

  public Scratch3ControlBlocks(EntityContext entityContext) {
    super("control", entityContext);

    // Blocks
    this.foreverBlock = Scratch3Block.ofHandler("forever", command, this::foreverHandler);
    this.scheduleBlock = Scratch3Block.ofHandler("schedule", command, this::repeatEveryTimeScheduleHandler);
    this.scheduleCronBlock = Scratch3Block.ofHandler("schedule_cron", command, this::scheduleCronHandler);
    this.repeatBlock = Scratch3Block.ofHandler("repeat", command, this::repeatHandler);
    this.ifBlock = Scratch3Block.ofHandler("if", command, this::ifHandler);
    this.ifElseBlock = Scratch3Block.ofHandler("if_else", command, this::ifElseHandler);
    this.stopBlock = Scratch3Block.ofHandler("stop", command, this::stopHandler);
    this.stopInTimeoutBlock = Scratch3Block.ofHandler("stop_timeout", command, this::stopInTimeoutHandler);

    this.waitBlock = Scratch3Block.ofHandler("wait", command, this::waitHandler);
    this.waitUntilBlock = Scratch3Block.ofHandler("wait_until", command, this::waitUntilHandler);
    this.repeatUntilBlock = Scratch3Block.ofHandler("repeat_until", command, this::repeatUntilHandler);
    this.whenConditionChangedBlock =
        Scratch3Block.ofHandler("when_condition_changed", command, this::whenConditionChangedHandler);
    this.whenValueChangedBlock = Scratch3Block.ofHandler("when_value_changed", command, this::whenValueChangedHandler);
    this.whenTimeBetweenBlock = Scratch3Block.ofHandler("when_time_between", command, this::whenTimeBetweenHandler);
  }

  private void scheduleCronHandler(WorkspaceBlock workspaceBlock) {
    if (workspaceBlock.hasChild()) {
      WorkspaceBlock child = workspaceBlock.getChild();
      String cron = workspaceBlock.getInputString("SEC") + " " + workspaceBlock.getInputString("MIN") + " " +
          workspaceBlock.getInputString("HOUR") + " " + workspaceBlock.getInputString("DAY") + " " +
          workspaceBlock.getInputString("MONTH") + " " + workspaceBlock.getInputString("DOW");
      AtomicInteger index = new AtomicInteger();
      entityContext.bgp().builder("workspace-schedule-cron-" + workspaceBlock.getId())
          .tap(context -> ((WorkspaceBlockImpl) workspaceBlock).setThreadContext(context))
          .interval(cron).execute(() -> {
            workspaceBlock.setValue("INDEX", new DecimalType(index.getAndIncrement()));
            child.handle();
            ((WorkspaceBlockImpl) workspaceBlock).setActiveWorkspace();
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
    Duration timeToExecute = Duration.ofMillis(getExecutionTTL(workspaceBlock, from, to));
    // Calendar cal = Calendar.getInstance();
    // Duration now = Duration.ofMinutes(HOURS.toMinutes(cal.get(Calendar.HOUR)) + cal.get(Calendar.MINUTE));

    LocalDateTime nowDateTime = LocalDateTime.now();
    LocalDateTime nextFromRun = nowDateTime.withHour(from.toHoursPart()).withMinute(from.toMinutesPart()).withSecond(0);
    LocalDateTime nextToRun = nowDateTime.withHour(to.toHoursPart()).withMinute(to.toMinutesPart()).withSecond(0);
    int toDiffFrom = to.compareTo(from); // 1 = same day. -1 = to less than from and mean to 'in next day'

    int diffWithTo = nowDateTime.compareTo(nextToRun);

    if (nowDateTime.compareTo(nextFromRun) > 0) {
      if ((toDiffFrom > 0 && nowDateTime.compareTo(nextToRun) < 0)) {
        Duration singleTimeToExecute = Duration.between(nowDateTime, nextToRun);
        // Duration.between(nowDateTime, nextToRun).plusDays(1)
        runWhenTimeInRange(workspaceBlock, singleTimeToExecute);
      }
      nextFromRun = nextFromRun.plusDays(1);
    } else if (diffWithTo < 0 && toDiffFrom < 0) {
      runWhenTimeInRange(workspaceBlock, Duration.between(nowDateTime, nextToRun));
    }

    Duration duration = Duration.between(nowDateTime, nextFromRun);
    entityContext.bgp().builder("when-time-in-range" + workspaceBlock.getId())
        .tap(context -> ((WorkspaceBlockImpl) workspaceBlock).setThreadContext(context))
        .delay(duration).interval(Duration.ofDays(1)).execute(() -> {
          runWhenTimeInRange(workspaceBlock, timeToExecute);
        });
  }

  private void runWhenTimeInRange(WorkspaceBlock workspaceBlock, Duration timeToExecute) {
    workspaceBlock.logInfo("Fire when-time-execution. Duration: {}", timeToExecute);
    ThreadContext<Void> executeContext = entityContext.bgp()
        .builder("when-time-execution" + workspaceBlock.getId())
        .interval(Duration.ofMillis(100)).execute(() -> {
          workspaceBlock.getChild().handle();
        });

    ThreadContext<Void> stopper = entityContext.bgp().builder("when-time-execution-stop-" + workspaceBlock.getId())
        .delay(timeToExecute).execute(() -> {
          workspaceBlock.logInfo("Cancelling thread: {}", executeContext.getName());
          executeContext.cancel();
          // reset active to 'when-time-in-range' thread
          ((WorkspaceBlockImpl) workspaceBlock).setActiveWorkspace();
        });
    workspaceBlock.onRelease(executeContext::cancel);
    workspaceBlock.onRelease(stopper::cancel);
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
    workspaceBlock.handleChildOptional(child -> {
      if (workspaceBlock.hasInput(inputName)) {
        BroadcastLock lock = workspaceBlock.getBroadcastLockManager().listenEvent(workspaceBlock, supplier);
        workspaceBlock.subscribeToLock(lock, child::handle);
      }
    });
  }

  private void repeatUntilHandler(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleChildOptional(child -> {
      if (workspaceBlock.hasInput(CONDITION)) {
        buildSchedule("until", workspaceBlock, 0, () -> {
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
    BroadcastLock lock = workspaceBlock.getBroadcastLockManager().listenEvent(workspaceBlock,
        () -> workspaceBlock.getInputBoolean(CONDITION));
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
    entityContext.bgp()
        .builder("stop-in-timeout" + workspaceBlock.getId())
        .tap(context -> ((WorkspaceBlockImpl) workspaceBlock).setThreadContext(context))
        .interval(stopInDuration).execute(() -> {
          if (thread.isAlive()) {
            ((WorkspaceBlockImpl) workspaceBlock).release();
            thread.interrupt();
          }
        });
  }

  private void ifElseHandler(WorkspaceBlock workspaceBlock) {
    if (workspaceBlock.hasChild() && workspaceBlock.hasInput("SUBSTACK2") && workspaceBlock.hasInput(CONDITION)) {
      if (workspaceBlock.getInputBoolean(CONDITION)) {
        workspaceBlock.getChild().handle();
      } else {
        workspaceBlock.getInputWorkspaceBlock("SUBSTACK2").handle();
      }
    }
  }

  private void ifHandler(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleChildOptional(child -> {
      if (workspaceBlock.hasInput(CONDITION) && workspaceBlock.getInputBoolean(CONDITION)) {
        child.handle();
      }
    });
  }

  private void repeatHandler(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleChildOptional(child -> {
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
  private void buildSchedule(String name, WorkspaceBlock workspaceBlock, long timeout,
      ThrowingRunnable<Exception> runnable) {
    AtomicInteger index = new AtomicInteger(0);
    entityContext.bgp()
        .builder("workspace-schedule-" + name + "-" + workspaceBlock.getId())
        .interval(Duration.ofMillis(Math.max(100, (int) timeout)))
        .tap(context -> ((WorkspaceBlockImpl) workspaceBlock).setThreadContext(context))
        .execute(() -> {
          runnable.run();
          workspaceBlock.setValue("INDEX", new DecimalType(index.incrementAndGet()));
          ((WorkspaceBlockImpl) workspaceBlock).setActiveWorkspace();
        });
  }

  @SneakyThrows
  private void waitHandler(WorkspaceBlock workspaceBlock) {
    int duration = workspaceBlock.getInputInteger("DURATION");
    if (duration < 1 || duration > 3600) {
      throw new ServerException(
          "Unable to sleep current block, because duration is more than 1 hour. Actual value is: " + duration);
    }
    TimeUnit.SECONDS.sleep(duration);
  }

  @SneakyThrows
  private void foreverHandler(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleChildOptional(child -> {
      buildSchedule("forever", workspaceBlock, 0, child::handle);
    });
  }
}
