package org.homio.addon.firmata.provider.command;

import static org.homio.addon.firmata.provider.command.FirmataCommand.SYSEX_REGISTER;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.model.Status;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.provider.IODeviceWrapper;
import org.homio.addon.firmata.provider.util.THUtil;
import org.homio.api.state.OnOffType;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class FirmataRegisterCommand implements FirmataCommandPlugin {

  private final Context context;

  @Getter
  private final Map<Short, PendingRegistrationContext> pendingRegistrations = new HashMap<>();

  public FirmataRegisterCommand(Context context) {
    this.context = context;
  }

  @Override
  public FirmataCommand getCommand() {
    return SYSEX_REGISTER;
  }

  @Override
  public boolean isHandleBroadcastEvents() {
    return true;
  }

  @Override
  public boolean hasTH() {
    return true;
  }

  @Override
  public void handle(IODeviceWrapper device, FirmataBaseEntity entity, byte messageID, ByteBuffer payload) {
    log.warn("Found firmata device with target <{}>", entity.getTarget());
    long uniqueID = entity.getUniqueID() == 0 ? device.generateUniqueIDOnRegistrationSuccess() : entity.getUniqueID();
    String boardType = THUtil.getString(payload, null);

    entity.setStatus(Status.ONLINE);
    entity.setJoined(Status.ONLINE);

    context.db().updateDelayed(entity, t -> {
      t.setBoardType(boardType);
      t.setUniqueID(uniqueID);
    });

    context.event().fireEvent("firmata-ready-" + entity.getTarget(), OnOffType.ON);
    device.sendMessage(SYSEX_REGISTER, uniqueID);
  }

  @Override
  public void broadcastHandle(IODeviceWrapper device, FirmataBaseEntity entity, byte messageID, short target,
      ByteBuffer payload) {
    if (entity.getTarget() == target) {
      handle(device, entity, messageID, payload);
    } else {
      log.info("Got registering slave device: <{}>", target);
      pendingRegistrations.put(target, new PendingRegistrationContext(entity, target, THUtil.getString(payload, null)));
    }
  }
}
