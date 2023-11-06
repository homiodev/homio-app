package org.homio.addon.firmata.provider.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.homio.addon.firmata.model.FirmataBaseEntity;

@Getter
@Setter
@AllArgsConstructor
public class PendingRegistrationContext {

  private FirmataBaseEntity entity;
  private short target;
  private String test;
}
