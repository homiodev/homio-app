package org.homio.addon.homekit.accessories;

import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.characteristics.impl.windowcovering.CurrentPositionCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.PositionStateCharacteristic;
import io.github.hapjava.characteristics.impl.windowcovering.PositionStateEnum;
import io.github.hapjava.characteristics.impl.windowcovering.TargetPositionCharacteristic;
import io.github.hapjava.services.Service;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.HomekitEndpointContext;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Log4j2
public abstract class AbstractHomekitPositionAccessory extends AbstractHomekitAccessory<PositionStateCharacteristic> {

    final boolean emulateState;
    final boolean emulateStopSameDirection;
    final boolean sendUpDownForExtents;
    final int closedPosition;
    final int openPosition;
    private final CurrentPositionCharacteristic currentPositionCharacteristic;
    private final TargetPositionCharacteristic targetPositionCharacteristic;

    public AbstractHomekitPositionAccessory(@NotNull HomekitEndpointContext ctx,
                                            @NotNull Class<? extends Service> serviceClass) {
        super(ctx, PositionStateCharacteristic.class, serviceClass);
        emulateState = ctx.endpoint().getEmulateStopState();
        emulateStopSameDirection = ctx.endpoint().getEmulateStopState();
        sendUpDownForExtents = ctx.endpoint().getSendUpDownForExtents();
        closedPosition = inverted ? 0 : 100;
        openPosition = inverted ? 100 : 0;
        currentPositionCharacteristic = getCharacteristic(CurrentPositionCharacteristic.class);
        targetPositionCharacteristic = getCharacteristic(TargetPositionCharacteristic.class);
        // currentDoorStateVar = getVariable("currentDoorState", HomekitEndpointEntity::getCurrentDoorState);
    }

    public CompletableFuture<Integer> getCurrentPosition() {
        return currentPositionCharacteristic.getValue();
        // return CompletableFuture.completedFuture(convertPositionState(currentPositionVar, openPosition, closedPosition));
    }

    public CompletableFuture<PositionStateEnum> getPositionState() {
        return emulateState ? CompletableFuture.completedFuture(PositionStateEnum.STOPPED) :
                masterCharacteristic.getEnumValue();
    }

    public CompletableFuture<Integer> getTargetPosition() {
        return targetPositionCharacteristic.getValue();
    }

    public CompletableFuture<Void> setTargetPosition(int value) {
        return setTargetPosition((Integer) value);
    }

    @SneakyThrows
    public CompletableFuture<Void> setTargetPosition(Integer value) {
        targetPositionCharacteristic.setValue(value);
        // getCharacteristic(TargetPosition).ifPresentOrElse(taggedItem -> {
        // int targetPosition = convertPosition(value, openPosition);
        // updateVar(targetPositionVar, targetPosition);
        // Item item = taggedItem.getItem();
                /* if (item instanceof RollershutterItem itemAsRollerShutterItem) {
                    // HomeKit home app never sends STOP. we emulate stop if we receive 100% or 0% while the blind is moving
                    if (emulateState && (targetPosition == 100 && emulatedState == PositionStateEnum.DECREASING)
                        || ((targetPosition == 0 && emulatedState == PositionStateEnum.INCREASING))) {
                        if (emulateStopSameDirection) {
                            // some blinds devices do not support "STOP" but would stop if receive UP/DOWN while moving
                            itemAsRollerShutterItem
                                    .send(emulatedState == PositionStateEnum.INCREASING ? UpDownType.UP : UpDownType.DOWN);
                        } else {
                            itemAsRollerShutterItem.send(StopMoveType.STOP);
                        }
                        emulatedState = PositionStateEnum.STOPPED;
                    } else {
                        if (sendUpDownForExtents && targetPosition == 0) {
                            itemAsRollerShutterItem.send(UpDownType.UP);
                        } else if (sendUpDownForExtents && targetPosition == 100) {
                            itemAsRollerShutterItem.send(UpDownType.DOWN);
                        } else {
                            itemAsRollerShutterItem.send(new PercentType(targetPosition));
                        }
                        if (emulateState) {
                            @Nullable
                            PercentType currentPosition = item.getStateAs(PercentType.class);
                            emulatedState = currentPosition == null || currentPosition.intValue() == targetPosition
                                    ? PositionStateEnum.STOPPED
                                    : currentPosition.intValue() < targetPosition ? PositionStateEnum.INCREASING
                                    : PositionStateEnum.DECREASING;
                        }
                    }
                } else if (item instanceof DimmerItem itemAsDimmerItem) {
                    itemAsDimmerItem.send(new PercentType(targetPosition));
                } else if (item instanceof NumberItem itemAsNumberItem) {
                    itemAsNumberItem.send(new DecimalType(targetPosition));
                } else if (item instanceof GroupItem itemAsGroupItem
                           && itemAsGroupItem.getBaseItem() instanceof RollershutterItem) {
                    itemAsGroupItem.send(new PercentType(targetPosition));
                } else if (item instanceof GroupItem itemAsGroupItem
                           && itemAsGroupItem.getBaseItem() instanceof DimmerItem) {
                    itemAsGroupItem.send(new PercentType(targetPosition));
                } else if (item instanceof GroupItem itemAsGroupItem
                           && itemAsGroupItem.getBaseItem() instanceof NumberItem) {
                    itemAsGroupItem.send(new DecimalType(targetPosition));
                } else {
                    log.warn(
                            "Unsupported item type for characteristic {} at accessory {}. Expected Rollershutter, Dimmer or Number item, got {}",
                            TargetPosition, getName(), item.getClass());
                }*/
        //}, () -> log.warn("Mandatory characteristic {} not found at accessory {}. ", TargetPosition, getName()));
        return CompletableFuture.completedFuture(null);
    }

    public void subscribeCurrentPosition(HomekitCharacteristicChangeCallback callback) {
        currentPositionCharacteristic.subscribe(callback);
    }

    public void subscribePositionState(HomekitCharacteristicChangeCallback callback) {
        masterCharacteristic.subscribe(callback);
    }

    public void subscribeTargetPosition(HomekitCharacteristicChangeCallback callback) {
        targetPositionCharacteristic.subscribe(callback);
    }

    public void unsubscribeCurrentPosition() {
        currentPositionCharacteristic.unsubscribe();
    }

    public void unsubscribePositionState() {
        masterCharacteristic.unsubscribe();
    }

    public void unsubscribeTargetPosition() {
        targetPositionCharacteristic.unsubscribe();
    }
}