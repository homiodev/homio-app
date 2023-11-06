package org.homio.addon.firmata.provider;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.firmata4j.Consumer;
import org.firmata4j.IODevice;
import org.firmata4j.IODeviceEventListener;
import org.firmata4j.IOEvent;
import org.firmata4j.firmata.FirmataWatchdog;
import org.firmata4j.firmata.parser.FirmataEventType;
import org.firmata4j.fsm.Event;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.provider.command.FirmataCommand;
import org.homio.addon.firmata.provider.command.FirmataCommandPlugin;
import org.homio.addon.firmata.provider.command.FirmataOneWireResponseDataCommand;
import org.homio.addon.firmata.provider.util.THUtil;
import org.homio.addon.firmata.setting.FirmataWatchDogIntervalSetting;
import org.homio.api.Context;
import org.homio.api.model.Status;
import org.homio.api.state.DecimalType;
import org.homio.api.util.CommonUtils;


@Log4j2
@RequiredArgsConstructor
public abstract class FirmataDeviceCommunicator<T extends FirmataBaseEntity<T>> extends Consumer<Event>
    implements IODeviceEventListener {

  @Getter
  private final FirmataOneWireResponseDataCommand oneWireCommand;

  private final Context context;
  private final FirmataCommandPlugins firmataCommandPlugins;
  private T entity;

  @Getter
  private IODeviceWrapper device;
  private IODevice ioDevice;

  private Long lastRestartAttempt = 0L;

  public FirmataDeviceCommunicator(Context context, T entity) {
    this.entity = entity;
    this.context = context;
    this.oneWireCommand = context.getBean(FirmataOneWireResponseDataCommand.class);
    this.firmataCommandPlugins = context.getBean(FirmataCommandPlugins.class);

    this.context.event().addEntityUpdateListener(entity.getEntityID(),
        "firmata-update-listener" + entity.getEntityID(),
        (java.util.function.Consumer<T>) t -> FirmataDeviceCommunicator.this.entityUpdated(t, false));

    this.context.event().addEntityRemovedListener(entity.getEntityID(),
        "firmata-remove-listener" + entity.getEntityID(),
        (java.util.function.Consumer<T>) t -> FirmataDeviceCommunicator.this.entityUpdated(t, true));
  }

  protected abstract IODevice createIODevice(T entity) throws Exception;

  public abstract long generateUniqueIDOnRegistrationSuccess();

  protected T getEntity() {
    return entity;
  }

  @SneakyThrows
  public void destroy() {
    if (this.ioDevice != null) {
      try {
        this.ioDevice.stop();
      } catch (Exception ex) {
        log.warn("Unable to stop firmata communicator: <{}>", ex.getMessage());
      }
    }
  }

  public final String restart() {
    // skip restart if status ONLINE
    if (entity.getStatus() == Status.ONLINE && entity.getJoined() == Status.ONLINE) {
      return "action.communicator.already_run";
    }

    // try restart not often that once per minute
    if (System.currentTimeMillis() - lastRestartAttempt < 60000) {
      throw new RuntimeException("action.communicator.restart_too_often");
    }
    lastRestartAttempt = System.currentTimeMillis();

    // try restart
    try {
      this.destroy();

      this.ioDevice = this.createIODevice(this.entity);
      if (ioDevice == null) {
        throw new RuntimeException("action.communicator.unable_create");
      }
      this.device = new IODeviceWrapper(ioDevice, this);
      ioDevice.addProtocolMessageHandler("sysexCustomMessage", this);
      ioDevice.addEventListener(this);
      ioDevice.start();

      // this method throws exception if unable to get any notifications from
      ioDevice.ensureInitializationIsDone();
      updateStatus(entity, Status.ONLINE, null);

      FirmataWatchdog watchdog = new FirmataWatchdog(
          TimeUnit.MINUTES.toMillis(context.setting().getValue(FirmataWatchDogIntervalSetting.class)), () ->
          context.db().updateDelayed(entity, t -> t.setJoined(Status.ERROR)));

      ioDevice.addProtocolMessageHandler(FirmataEventType.ANY, watchdog);
      if (this.entity.getTarget() != -1) {
        this.device.sendMessage(FirmataCommand.SYSEX_REGISTER);
      }

      return "action.communicator.success";
    } catch (Exception ex) {
      updateStatus(entity, Status.ERROR, CommonUtils.getErrorMessage(ex));
      log.error("Error while initialize device: {} for device type: {}", entity.getTitle(), getClass().getSimpleName(), ex);
      throw new RuntimeException("action.communicator.unknown_error");
    }
  }

  @Override
  public void accept(Event event) {
    ByteBuffer payload = ByteBuffer.wrap((byte[]) event.getBodyItem("sysexCustomMessage"));
    byte commandID = payload.get();
    FirmataCommandPlugin handler = firmataCommandPlugins.getFirmataCommandPlugin(commandID);
    if (handler == null) {
      log.error("Unable to find firmata custom handler with type: <{}>", commandID);
      return;
    }
    byte messageID = handler.hasTH() ? THUtil.getByte(payload) : 0;
    short target = handler.hasTH() ? THUtil.getShort(payload) : 0;
    if (entity.getJoined() == Status.ONLINE) {
      handler.handle(device, entity, messageID, payload);
    } else if (entity.getTarget() == target || handler.isHandleBroadcastEvents()) {
      handler.broadcastHandle(device, entity, messageID, target, payload);
    }
  }

  @Override
  public void onStart(IOEvent event) {
    log.info("Firmata device: " + entity.getTitle() + " communication started");
  }

  @Override
  public void onStop(IOEvent event) {
    log.info("Firmata device: " + entity.getTitle() + " communication stopped");
  }

  @Override
  public void onPinChange(IOEvent event) {
    if (entity.getTarget() != -1) {
      context.event().fireEvent(entity.getTarget() + "-pin-" + event.getPin().getIndex(),
          new DecimalType(event.getPin().getValue()));
    }
  }

  @Override
  public void onMessageReceive(IOEvent event, String message) {
    log.info("Firmata <{}> got message: <{}>", entity.getTitle(), message);
  }

  private void entityUpdated(T entity, boolean remove) {
    if (remove) {
      this.destroy();
    } else {
      this.entity = entity;
    }
  }

  private void updateStatus(T entity, Status status, String statusMessage) {
    if (entity.getStatus() != status) {
      entity.setStatus(status, statusMessage);
    }
  }
}
