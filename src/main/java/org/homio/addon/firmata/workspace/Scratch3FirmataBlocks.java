package org.homio.addon.firmata.workspace;

import java.util.function.BiPredicate;
import java.util.function.Function;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.firmata4j.Pin;
import org.firmata4j.firmata.FirmataMessageFactory;
import org.homio.addon.firmata.FirmataBundleEntrypoint;
import org.homio.addon.firmata.model.FirmataBaseEntity;
import org.homio.addon.firmata.provider.command.FirmataCommand;
import org.homio.addon.firmata.provider.command.FirmataGetTimeValueCommand;
import org.homio.api.Context;
import org.homio.api.model.OptionModel.KeyValueEnum;
import org.homio.api.model.Status;
import org.homio.api.state.DecimalType;
import org.homio.api.state.OnOffType;
import org.homio.api.state.State;
import org.homio.api.state.StringType;
import org.homio.api.workspace.Lock;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.MenuBlock;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3FirmataBlocks extends Scratch3FirmataBaseBlock {

  private final FirmataGetTimeValueCommand getTimeValueCommand;

  private final MenuBlock.StaticMenuBlock<OnOffType.OnOffTypeEnum> menuOnOff;
  private final MenuBlock.StaticMenuBlock<CompareType> menuOp;
  private final MenuBlock.StaticMenuBlock<PinMode> menuPinMode;

  private final MenuBlock.ServerMenuBlock menuPinDigital;
  private final MenuBlock.ServerMenuBlock menuPinPwm;
  private final MenuBlock.ServerMenuBlock menuPinAnalog;
  private final MenuBlock.ServerMenuBlock menuPinAll;
  private final MenuBlock.ServerMenuBlock menuPinServo;

  public Scratch3FirmataBlocks(Context context, FirmataBundleEntrypoint firmataBundleEntrypoint,
      FirmataGetTimeValueCommand getTimeValueCommand) {
    super("#C9BF43", context, firmataBundleEntrypoint, null);
    this.getTimeValueCommand = getTimeValueCommand;

    // Menu
    this.menuPinDigital = menuServer("pinMenuDigital", REST_PIN + Pin.Mode.OUTPUT, "Digital pin").setDependency(this.firmataIdMenu);
    this.menuPinPwm = menuServer("pinMenuPwm", REST_PIN + Pin.Mode.PWM, "Pwm pin").setDependency(this.firmataIdMenu);
    this.menuPinAnalog = menuServer("pinMenuAnalog", REST_PIN + Pin.Mode.ANALOG, "Analog pin").setDependency(this.firmataIdMenu);
    this.menuPinServo = menuServer("pinMenuAnalog", REST_PIN + Pin.Mode.SERVO, "Servo pin").setDependency(this.firmataIdMenu);
    this.menuPinAll = menuServer("pinMenuAll", REST_PIN, "Pin").setDependency(this.firmataIdMenu);

    this.menuOnOff = menuStatic("onOffMenu", OnOffType.OnOffTypeEnum.class, OnOffType.OnOffTypeEnum.Off);
    this.menuOp = menuStatic("opMenu", CompareType.class, CompareType.GREATER);
    this.menuPinMode = menuStatic("pinModeMenu", PinMode.class, PinMode.PULL_UP);

    // Blocks
    blockReporter(5, "pin_read", "Get [PIN] of [FIRMATA]", this::readPinEvaluate, block ->
        addPinMenu(block, this.menuPinAll, null));

    blockCommand(10, "digital_write", "Set(D) Pin [PIN] [ON_OFF] to [FIRMATA]", this::digitalWriteHandler, block -> {
      block.addArgument("ON_OFF", this.menuOnOff);
      addPinMenu(block, this.menuPinDigital, null);
    });

    blockCommand(15, "analog_write", "Set(A) Pin [PIN] [VALUE] to [FIRMATA]", this::pwmWriteHandler, block -> {
      block.addArgument(VALUE, 50);
      addPinMenu(block, this.menuPinAnalog, null);
    });

    blockCommand(20, "pwm_write", "Set(PWM) Pin [PIN] [VALUE] to [FIRMATA]", this::pwmWriteHandler, block -> {
      block.addArgument(VALUE, 50);
      addPinMenu(block, this.menuPinPwm, null);
    });

    blockCommand(25, "invert_pin", "Invert Pin(D) [PIN] of [FIRMATA]", this::invertPinHandler, block ->
        addPinMenu(block, this.menuPinDigital, null));

    blockBoolean(40, "ready", "[FIRMATA] ready", this::isReadyEvaluate, block ->
        block.addArgument(FIRMATA, this.firmataIdMenu));

    blockHat(45, "when_pin_changed", "When Pin [PIN] changed of [FIRMATA]", this::whenPinChangedHandler, block ->
        addPinMenu(block, this.menuPinAll, "#CCD247"));

    blockHat(50, "when_pin_op_value", "When Pin [PIN] [OP] value [VALUE] of [FIRMATA]", this::whenPinOpValueHandler, block -> {
      block.addArgument("OP", this.menuOp);
      block.addArgument(VALUE, 0);
      addPinMenu(block, this.menuPinAll, "#CCD247");
    });

    blockHat(55, "when_ready", "When [FIRMATA] ready", this::whenDeviceReady, block -> {
      block.addArgument(FIRMATA, this.firmataIdMenu);
      block.overrideColor("#CCD247");
    });

    blockCommand(60, "set_mode", "Set Pin [PIN] mode [MODE] to [FIRMATA]", this::setPinModeHandler, block -> {
      block.addArgument("MODE", this.menuPinMode);
      addPinMenu(block, this.menuPinPwm, "#939844");
    });

    blockCommand(65, "set_sampling_interval", "Set Sampling [INTERVAL] of [FIRMATA]", this::setSamplingIntervalHandler, block -> {
      block.addArgument(FIRMATA, this.firmataIdMenu);
      block.addArgument("INTERVAL", 19);
      block.overrideColor("#939844");
    });

    blockCommand(70, "set_servo_config", "Servo pulse min/max [MIN]/[MAX] of [FIRMATA]", this::setServoConfigHandler, block -> {
      block.addArgument("MIN", 0);
      block.addArgument("MAX", 100);
      addPinMenu(block, this.menuPinServo, "#939844");
    });

    blockCommand(70, "delay", "Delay [VALUE] of [FIRMATA]", this::delayHandler, block -> {
      block.addArgument(FIRMATA, this.firmataIdMenu);
      block.addArgument(VALUE, 3);
      block.overrideColor("#939844");
    });

    blockReporter(80, "time", "time of [FIRMATA]", this::getTimeEvaluate, block -> {
      block.addArgument(FIRMATA, this.firmataIdMenu);
      block.overrideColor("#939844");
    });

    blockReporter(90, "protocol", "protocol of [FIRMATA]", this::getProtocolEvaluate, block -> {
      block.addArgument(FIRMATA, this.firmataIdMenu);
      block.overrideColor("#939844");
    });
  }

  private void delayHandler(WorkspaceBlock workspaceBlock) {
    int value = workspaceBlock.getInputInteger(VALUE);
    execute(workspaceBlock, false, entity -> {
      entity.getDevice().getIoOneWire().sendOneWireDelay((byte) 0, value);
    });
  }

  private void whenDeviceReady(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleNextOptional(next -> {
      String firmataId = workspaceBlock.getMenuValue(FIRMATA, this.firmataIdMenu);
      FirmataBaseEntity entity = context.db().getEntity(firmataId);
      if (entity == null || entity.getTarget() == -1) {
        return;
      }

      if (entity.getJoined() == Status.ONLINE) {
        next.handle();
      } else {
        Lock readyLock = workspaceBlock.getLockManager().getLock(workspaceBlock, "firmata-ready-" + entity.getTarget());
        if (readyLock.await(workspaceBlock)) {
          next.handle();
        }
      }
    });
  }

  private void setServoConfigHandler(WorkspaceBlock workspaceBlock) {
    executeNoResponse(workspaceBlock, false, this.menuPinAll, (entity, pin) -> {
      int minPulse = workspaceBlock.getInputInteger("MIN");
      int maxPulse = workspaceBlock.getInputInteger("MAX");
      pin.setServoMode(minPulse, maxPulse);
    });
  }

  private void setSamplingIntervalHandler(WorkspaceBlock workspaceBlock) {
    int interval = workspaceBlock.getInputInteger("INTERVAL");
    execute(workspaceBlock, false, entity -> {
      entity.getDevice().sendMessage(FirmataMessageFactory.setSamplingInterval(interval));
    });
  }

  private void setPinModeHandler(WorkspaceBlock workspaceBlock) {
    PinMode mode = workspaceBlock.getMenuValue("MODE", this.menuPinMode);
    executeNoResponse(workspaceBlock, false, this.menuPinAll, (entity, pin) -> pin.setMode(mode.value));
  }

  private void whenPinChangedHandler(WorkspaceBlock workspaceBlock) {
    whenPinChangedHandler(workspaceBlock, value -> true);
  }

  private void whenPinOpValueHandler(WorkspaceBlock workspaceBlock) {
    CompareType compareType = workspaceBlock.getMenuValue("OP", this.menuOp);
    Integer compareValue = workspaceBlock.getInputInteger(VALUE);
    whenPinChangedHandler(workspaceBlock, value -> compareType.match((long) value, compareValue));
  }

  private void whenPinChangedHandler(WorkspaceBlock workspaceBlock, Function<Object, Boolean> checkFn) {
    workspaceBlock.handleNextOptional(next -> {
      Integer pinNum = getPin(workspaceBlock, this.menuPinAll);
      execute(workspaceBlock, true, entity -> {
        Pin pin = entity.getDevice().getIoDevice().getPin(pinNum);
        Lock lock = workspaceBlock.getLockManager().getLock(workspaceBlock, entity.getTarget() + "-pin-" + pin.getIndex());
        workspaceBlock.subscribeToLock(lock, checkFn, next::handle);
      });
    });
  }

  private State isReadyEvaluate(WorkspaceBlock workspaceBlock) {
    Boolean value = execute(workspaceBlock, false, null, (entity, pin) -> entity.getDevice().getIoDevice().isReady());
    return OnOffType.of(Boolean.TRUE.equals(value));
  }

  private State getProtocolEvaluate(WorkspaceBlock workspaceBlock) {
    return execute(workspaceBlock, false, null, (entity, pin) ->
        new StringType(entity.getDevice().getIoDevice().getProtocol()));
  }

  private State getTimeEvaluate(WorkspaceBlock workspaceBlock) {
    return execute(workspaceBlock, false, entity -> {
      byte messageID = entity.getDevice().sendMessage(FirmataCommand.SYSEX_GET_TIME_COMMAND);
      return new DecimalType(getTimeValueCommand.waitForValue(entity, messageID));
    });
  }

  private State readPinEvaluate(WorkspaceBlock workspaceBlock) {
    Long value = execute(workspaceBlock, false, menuPinAll, (entity, pin) -> pin.getValue());
    return value == null ? null : new DecimalType(value);
  }

  private void digitalWriteHandler(WorkspaceBlock workspaceBlock) {
    updatePinValue(workspaceBlock, Pin.Mode.OUTPUT, pin ->
        (long) workspaceBlock.getMenuValue("ON_OFF", this.menuOnOff).ordinal());
  }

  private void pwmWriteHandler(WorkspaceBlock workspaceBlock) {
    updatePinValue(workspaceBlock, Pin.Mode.PWM, pin -> workspaceBlock.getInputInteger(VALUE).longValue());
  }

  private void invertPinHandler(WorkspaceBlock workspaceBlock) {
    updatePinValue(workspaceBlock, Pin.Mode.OUTPUT, pin -> pin.getValue() == 1 ? 0L : 1L);
  }

  private void updatePinValue(WorkspaceBlock workspaceBlock, Pin.Mode mode, Function<Pin, Long> pinValueProducer) {
    executeNoResponse(workspaceBlock, false, this.menuPinDigital, (entity, pin) -> {
      pin.setMode(mode);
      Long value = pinValueProducer.apply(pin);
      pin.setValue(value);
    });
  }

  @AllArgsConstructor
  public enum PinMode {
    ENCODER(Pin.Mode.ENCODER),
    SERVO(Pin.Mode.SERVO),
    SERIAL(Pin.Mode.SERIAL),
    PULL_UP(Pin.Mode.PULLUP);
    private final Pin.Mode value;
  }

  @AllArgsConstructor
  public enum CompareType implements KeyValueEnum {
    GREATER(">", (a, b) -> a > b),
    LESS("<", (a, b) -> a < b),
    GREATER_EQUAL(">=", (a, b) -> a >= b),
    LESS_EQUAL("<=", (a, b) -> a <= b),
    EQUAL("=", (a, b) -> Double.compare(a, b) == 0);

    @Getter
    private final String shortName;

    private final BiPredicate<Double, Double> matchFn;

    public boolean match(double a, double b) {
      return matchFn.test(a, b);
    }

    @Override
    public String toString() {
      return shortName;
    }
  }
}
