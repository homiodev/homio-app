package org.homio.app.workspace.block.core;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextBGP;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.exception.NotFoundException;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

import static org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks.DEVICE;
import static org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks.ENDPOINT;
import static org.homio.api.workspace.scratch.Scratch3BaseDeviceBlocks.OnOff;

@Log4j2
@Getter
@Component
public class Scratch3DeviceBlocks extends Scratch3ExtensionBlocks {

  public Scratch3DeviceBlocks(Context context) {
    super("device", context);

    blockReporter("time_since_last_event", workspaceBlock -> {
      Duration timeSinceLastEvent = getDeviceEndpoint(workspaceBlock).getTimeSinceLastEvent();
      return new DecimalType(timeSinceLastEvent.toSeconds()).setUnit("sec");
    });
    blockReporter("value", workspaceBlock ->
      getDeviceEndpoint(workspaceBlock).getLastValue());
    blockCommand("read_edp", workspaceBlock ->
      getDeviceEndpoint(workspaceBlock).readValue());
    blockCommand("write_edp", workspaceBlock -> {
      Object value = workspaceBlock.getInput(VALUE, true);
      getDeviceEndpoint(workspaceBlock).writeValue(State.of(value));
    });
    blockCommand("write_bool", workspaceBlock -> {
      OnOff value = OnOff.valueOf(workspaceBlock.getField(VALUE));
      getDeviceEndpoint(workspaceBlock).writeValue(OnOffType.of(value == OnOff.on));
    });
    blockHat("when_value_change", this::whenValueChange);
    blockHat("when_value_change_to", this::whenValueChangeTo);
    blockHat("when_no_value_change", this::whenNoValueChangeSince);
    blockHat("when_ready", this::whenDeviceReady);
    blockReporter("ready", workspaceBlock -> {
      var device = getDevice(workspaceBlock);
      return OnOffType.of(device.getStatus().isOnline());
    });
  }

  private void whenDeviceReady(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNextOptional(next -> {
      var device = getDevice(workspaceBlock);
      if (device.getStatus().isOnline()) {
        next.handle();
      } else {
        var readyLock = workspaceBlock.getLockManager().getLock(workspaceBlock, "device-ready-" + device.getIeeeAddress());
        if (readyLock.await(workspaceBlock)) {
          if (device.getStatus().isOnline()) {
            next.handle();
          } else {
            log.error("Unable to execute step for device: <{}>. Waited for ready status but got: <{}>", device.getTitle(), device.getStatus());
          }
        }
      }
    });
  }

  private void whenValueChange(@NotNull WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNext(next -> {
      DeviceEndpoint endpoint = getDeviceEndpoint(workspaceBlock);
      Lock lock = workspaceBlock.getLockManager().getLock(workspaceBlock);

      endpoint.addChangeListener(workspaceBlock.getId(), state -> lock.signalAll());
      workspaceBlock.onRelease(() -> endpoint.removeChangeListener(workspaceBlock.getId()));
      workspaceBlock.subscribeToLock(lock, next::handle);
    });
  }

  private void whenValueChangeTo(@NotNull WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNext(next -> {
      DeviceEndpoint endpoint = getDeviceEndpoint(workspaceBlock);
      String value = workspaceBlock.getInputString(VALUE);
      if (StringUtils.isEmpty(value)) {
        workspaceBlock.logErrorAndThrow("Value must be not empty");
      }
      Lock lock = workspaceBlock.getLockManager().getLock(workspaceBlock);

      endpoint.addChangeListener(workspaceBlock.getId(), state -> {
        if (state.stringValue().equals(value)) {
          lock.signalAll();
        }
      });
      workspaceBlock.onRelease(() -> endpoint.removeChangeListener(workspaceBlock.getId()));
      workspaceBlock.subscribeToLock(lock, next::handle);
    });
  }

  /**
   * Handler to wait specific seconds after some event and fire event after that
   */
  private void whenNoValueChangeSince(@NotNull WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNext(next -> {
      Integer secondsToWait = workspaceBlock.getInputInteger("DURATION");
      if (secondsToWait < 1) {
        workspaceBlock.logErrorAndThrow("Duration must be greater than 1 seconds. Value: {}", secondsToWait);
      }
      DeviceEndpoint endpoint = getDeviceEndpoint(workspaceBlock);
      Lock eventOccurredLock = workspaceBlock.getLockManager().getLock(workspaceBlock);

      // add listener on target endpoint for any changes and wake up lock
      endpoint.addChangeListener(workspaceBlock.getId(), state -> eventOccurredLock.signalAll());

      // thread context that will be started when endpoint's listener fire event
      ContextBGP.ThreadContext<Void> delayThread = context.bgp().builder("when-no-val-" + workspaceBlock.getId())
        .delay(Duration.ofSeconds(secondsToWait))
        .tap(workspaceBlock::setThreadContext)
        .execute(next::handle, false);
      // remove listener from endpoint. ThreadContext will be canceled automatically
      workspaceBlock.onRelease(() ->
        endpoint.removeChangeListener(workspaceBlock.getId()));
      // subscribe to lock that will restart delay thread after event
      workspaceBlock.subscribeToLock(eventOccurredLock, delayThread::reset);
    });
  }

  protected @NotNull DeviceEndpoint getDeviceEndpoint(@NotNull WorkspaceBlock workspaceBlock) {
    String endpointID = workspaceBlock.getField(ENDPOINT);
    return getDeviceEndpoint(workspaceBlock, endpointID);
  }

  protected @NotNull DeviceEndpoint getDeviceEndpoint(
    @NotNull WorkspaceBlock workspaceBlock,
    @NotNull String endpointID) {
    var device = getDevice(workspaceBlock);
    DeviceEndpoint endpoint = ((DeviceEndpointsBehaviourContract) device).getDeviceEndpoint(endpointID);
    if (endpoint == null) {
      workspaceBlock.logErrorAndThrow("Unable to find endpoint: {}/{}", device.getEntityID(), endpointID);
      throw new NotImplementedException();
    }
    return endpoint;
  }

  private DeviceBaseEntity getDevice(WorkspaceBlock workspaceBlock) {
    String ieeeAddress = workspaceBlock.getInputWorkspaceBlock(DEVICE).getField(DEVICE);
    List<DeviceBaseEntity> entities = context.db().getDeviceEntity(ieeeAddress, null);
    if (entities.isEmpty()) {
      throw new NotFoundException("Unable to find entity: " + ieeeAddress);
    }
    if (entities.size() > 1) {
      throw new NotFoundException("Found multiple entities with id: " + ieeeAddress);
    }
    return entities.iterator().next();
  }
}
