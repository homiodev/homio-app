package org.touchhome.app.workspace.block.core;

import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.scratch.BlockType;
import org.touchhome.bundle.api.scratch.Scratch3Block;
import org.touchhome.bundle.api.scratch.Scratch3ExtensionBlocks;
import org.touchhome.bundle.api.scratch.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.touchhome.bundle.api.scratch.Scratch3Block.CONDITION;
import static org.touchhome.bundle.api.scratch.Scratch3Block.SUBSTACK;

@Getter
@Component
public class Scratch3ControlBlocks extends Scratch3ExtensionBlocks {

    private final Scratch3Block foreverBlock;
    private final Scratch3Block waitBlock;
    private final Scratch3Block repeatBlock;
    private final Scratch3Block ifBlock;
    private final Scratch3Block ifElseBlock;
    private final Scratch3Block stopBlock;
    private final Scratch3Block waitUntilBlock;
    private final Scratch3Block repeatUntilBlock;
    private final Scratch3Block whenConditionChangedBlock;
    private final Scratch3Block whenValueChangedBlock;

    private final BroadcastLockManager broadcastLockManager;

    public Scratch3ControlBlocks(BroadcastLockManager broadcastLockManager, EntityContext entityContext) {
        super("control", entityContext);
        this.broadcastLockManager = broadcastLockManager;

        // Blocks
        this.foreverBlock = Scratch3Block.ofHandler("forever", BlockType.conditional, this::foreverHandler);
        this.repeatBlock = Scratch3Block.ofHandler("repeat", BlockType.conditional, this::repeatHandler);
        this.ifBlock = Scratch3Block.ofHandler("if", BlockType.conditional, this::ifHandler);
        this.ifElseBlock = Scratch3Block.ofHandler("if_else", BlockType.conditional, this::ifElseHandler);
        this.stopBlock = Scratch3Block.ofHandler("stop", BlockType.command, this::stopHandler);
        this.waitBlock = Scratch3Block.ofHandler("wait", BlockType.command, this::waitHandler);
        this.waitUntilBlock = Scratch3Block.ofHandler("wait_until", BlockType.command, this::waitUntilHandler);
        this.repeatUntilBlock = Scratch3Block.ofHandler("repeat_until", BlockType.command, this::repeatUntilHandler);
        this.whenConditionChangedBlock = Scratch3Block.ofHandler("when_condition_changed", BlockType.command, this::whenConditionChangedHandler);
        this.whenValueChangedBlock = Scratch3Block.ofHandler("when_value_changed", BlockType.command, this::whenValueChangedHandler);

        this.postConstruct();
    }

    private void whenValueChangedHandler(WorkspaceBlock workspaceBlock) {
        AtomicReference<Object> ref = new AtomicReference<>();
        lockForEvent(workspaceBlock, "VALUE", () -> {
            Object value = workspaceBlock.getInput("VALUE", true);
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
        if (workspaceBlock.hasInput(inputName) && workspaceBlock.hasInput(SUBSTACK)) {
            WorkspaceBlock child = workspaceBlock.getInputWorkspaceBlock(SUBSTACK);
            BroadcastLock lock = broadcastLockManager.listenEvent(workspaceBlock, supplier);
            while (!Thread.currentThread().isInterrupted()) {
                if (lock.await(workspaceBlock)) {
                    child.handle();
                }
            }
        }
    }

    private void repeatUntilHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasInput(SUBSTACK) && workspaceBlock.hasInput(CONDITION)) {
            WorkspaceBlock child = workspaceBlock.getInputWorkspaceBlock(SUBSTACK);
            while (workspaceBlock.getInputBoolean(CONDITION)) {
                child.handle();
            }
        }
    }

    @SneakyThrows
    private void waitUntilHandler(WorkspaceBlock workspaceBlock) {
        BroadcastLock lock = broadcastLockManager.listenEvent(workspaceBlock, () -> workspaceBlock.getInputBoolean(CONDITION));
        lock.await(workspaceBlock);
    }

    private void stopHandler(WorkspaceBlock ignore) {
        if (Thread.currentThread().getName().startsWith("pool-")) {
            Thread.currentThread().interrupt();
        }
    }

    private void ifElseHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasInput(SUBSTACK) && workspaceBlock.hasInput("SUBSTACK2") && workspaceBlock.hasInput(CONDITION)) {
            if (workspaceBlock.getInputBoolean(CONDITION)) {
                workspaceBlock.getInputWorkspaceBlock(SUBSTACK).handle();
            } else {
                workspaceBlock.getInputWorkspaceBlock("SUBSTACK2").handle();
            }
        }
    }

    private void ifHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasInput(CONDITION) && workspaceBlock.hasInput(SUBSTACK)) {
            if (workspaceBlock.getInputBoolean(CONDITION)) {
                workspaceBlock.getInputWorkspaceBlock(SUBSTACK).handle();
            }
        }
    }

    private void repeatHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasInput(SUBSTACK)) {
            WorkspaceBlock child = workspaceBlock.getInputWorkspaceBlock(SUBSTACK);
            int times = workspaceBlock.getInputInteger("TIMES");
            for (int i = 0; i < times; i++) {
                child.handle();
            }
        }
    }

    @SneakyThrows
    private void waitHandler(WorkspaceBlock workspaceBlock) {
        int duration = workspaceBlock.getInputInteger("DURATION");
        if (duration < 1 || duration > 3600) {
            throw new RuntimeException("Unable to sleep current block, because duration is more than 1 hour. Actual value is: " + duration);
        }
        TimeUnit.SECONDS.sleep(duration);
    }

    @SneakyThrows
    private void foreverHandler(WorkspaceBlock workspaceBlock) {
        if (workspaceBlock.hasInput(SUBSTACK)) {
            WorkspaceBlock child = workspaceBlock.getInputWorkspaceBlock(SUBSTACK);
            while (!Thread.currentThread().isInterrupted()) {
                child.handle();

                Thread.sleep(100); // wait at least 100ms for 'clumsy hands'
            }
        }
    }
}
