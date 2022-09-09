package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.app.workspace.WorkspaceBlockImpl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.EntityContextBGP;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.BlockType;
import org.touchhome.bundle.api.workspace.scratch.Scratch3Block;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.touchhome.common.exception.ServerException;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.touchhome.bundle.api.workspace.scratch.Scratch3Block.CONDITION;

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

    private final BroadcastLockManager broadcastLockManager;
    private final Scratch3Block scheduleCronBlock;

    public Scratch3ControlBlocks(BroadcastLockManager broadcastLockManager, EntityContext entityContext) {
        super("control", entityContext);
        this.broadcastLockManager = broadcastLockManager;

        // Blocks
        this.foreverBlock = Scratch3Block.ofHandler("forever", BlockType.command, this::foreverHandler);
        this.scheduleBlock = Scratch3Block.ofHandler("schedule", BlockType.command, this::scheduleHandler);
        this.scheduleCronBlock = Scratch3Block.ofHandler("schedule_cron", BlockType.command, this::scheduleCronHandler);
        this.repeatBlock = Scratch3Block.ofHandler("repeat", BlockType.command, this::repeatHandler);
        this.ifBlock = Scratch3Block.ofHandler("if", BlockType.command, this::ifHandler);
        this.ifElseBlock = Scratch3Block.ofHandler("if_else", BlockType.command, this::ifElseHandler);
        this.stopBlock = Scratch3Block.ofHandler("stop", BlockType.command, this::stopHandler);
        this.stopInTimeoutBlock = Scratch3Block.ofHandler("stop_timeout", BlockType.command, this::stopInTimeoutHandler);

        this.waitBlock = Scratch3Block.ofHandler("wait", BlockType.command, this::waitHandler);
        this.waitUntilBlock = Scratch3Block.ofHandler("wait_until", BlockType.command, this::waitUntilHandler);
        this.repeatUntilBlock = Scratch3Block.ofHandler("repeat_until", BlockType.command, this::repeatUntilHandler);
        this.whenConditionChangedBlock =
                Scratch3Block.ofHandler("when_condition_changed", BlockType.command, this::whenConditionChangedHandler);
        this.whenValueChangedBlock =
                Scratch3Block.ofHandler("when_value_changed", BlockType.command, this::whenValueChangedHandler);
    }

    private void scheduleCronHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasChild()) {
            WorkspaceBlock child = workspaceBlock.getChild();
            String cron = workspaceBlock.getInputString("SEC") + " " +
                    workspaceBlock.getInputString("MIN") + " " +
                    workspaceBlock.getInputString("HOUR") + " " +
                    workspaceBlock.getInputString("DAY") + " " +
                    workspaceBlock.getInputString("MONTH") + " " +
                    workspaceBlock.getInputString("DOW");
            AtomicInteger index = new AtomicInteger();
            EntityContextBGP.ThreadContext<Void> schedule =
                    entityContext.bgp().schedule("workspace-schedule-" + workspaceBlock.getId(), cron,
                            () -> {
                                workspaceBlock.setValue("index", index.getAndIncrement());
                                child.handle();
                            }, true, true);
            workspaceBlock.onRelease(schedule::cancel);
        }
    }

    @SneakyThrows
    private void scheduleHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasChild()) {
            Integer time = workspaceBlock.getInputInteger("TIME");
            TimeUnit timeUnit = TimeUnit.valueOf(workspaceBlock.getField("UNIT"));
            WorkspaceBlock child = workspaceBlock.getChild();
            int index = 0;
            while (!workspaceBlock.isDestroyed()) {
                workspaceBlock.setState("execute");
                workspaceBlock.setValue("index", index++);
                child.handle();
                workspaceBlock.setState("Wait next event");
                Thread.sleep(timeUnit.toMillis(time));
            }
        }
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
                BroadcastLock lock = broadcastLockManager.listenEvent(workspaceBlock, supplier);
                workspaceBlock.subscribeToLock(lock, child::handle);
            }
        });
    }

    private void repeatUntilHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.handleChildOptional(child -> {
            if (workspaceBlock.hasInput(CONDITION)) {
                int index = 0;
                while (workspaceBlock.getInputBoolean(CONDITION)) {
                    workspaceBlock.setValue("index", index++);
                    child.handle();

                    Thread.sleep(100); // wait at least 100ms for 'clumsy hands'
                }
            }
        });
    }

    @SneakyThrows
    private void waitUntilHandler(WorkspaceBlock workspaceBlock) {
        BroadcastLock lock = broadcastLockManager.listenEvent(workspaceBlock, () -> workspaceBlock.getInputBoolean(CONDITION));
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
        entityContext.bgp().run("stop-in-timeout" + workspaceBlock.getId(), timeUnit.toMillis(timeout), () -> {
            if (thread.isAlive()) {
                ((WorkspaceBlockImpl) workspaceBlock).release();
                thread.interrupt();
            }
        }, false);
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
            int times = workspaceBlock.getInputInteger("TIMES");
            for (int index = 0; index < times; index++) {
                workspaceBlock.setValue("index", index);
                child.handle();
            }
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
            int index = 0;
            while (!workspaceBlock.isDestroyed()) {
                workspaceBlock.setValue("index", index++);
                child.handle();

                Thread.sleep(100); // wait at least 100ms for 'clumsy hands'
            }
        });
    }
}
