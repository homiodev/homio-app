package org.touchhome.bundle.raspberry.workspace;

import com.pi4j.io.gpio.PinMode;
import com.pi4j.io.gpio.PinPullResistance;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.Hilo;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.util.RaspberryGpioPin;
import org.touchhome.bundle.api.workspace.BroadcastLock;
import org.touchhome.bundle.api.workspace.BroadcastLockManager;
import org.touchhome.bundle.raspberry.RaspberryGPIOService;

@Getter
@Component
@Scratch3Extension("raspberry")
public class Scratch3RaspberryBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock hiloMenu;
    private final MenuBlock.ServerMenuBlock rpiIdMenu;
    private final MenuBlock.StaticMenuBlock allPinMenu;
    private final MenuBlock.StaticMenuBlock pwmPinMenu;
    private final MenuBlock pullMenu;
    private final MenuBlock ds18b20Menu;

    private final Scratch3Block isGpioInState;
    private final Scratch3Block writePin;
    private final Scratch3Block whenGpioInState;
    private final Scratch3Block set_pull;
    private final Scratch3Block ds18b20Value;
    private Scratch3Block writePwmPin;

    private final RaspberryGPIOService raspberryGPIOService;
    private final BroadcastLockManager broadcastLockManager;

    public Scratch3RaspberryBlocks(RaspberryGPIOService raspberryGPIOService, BroadcastLockManager broadcastLockManager, EntityContext entityContext) {
        super("#83be41", entityContext);
        this.raspberryGPIOService = raspberryGPIOService;
        this.broadcastLockManager = broadcastLockManager;

        this.allPinMenu = MenuBlock.ofStatic("allPinMenu", RaspberryGpioPin.class, p ->
                p.getPin().getSupportedPinModes().contains(PinMode.DIGITAL_INPUT));
        this.pwmPinMenu = MenuBlock.ofStatic("pwmPinMenu", RaspberryGpioPin.class, p -> p.name().equals("PIN12"));

        this.rpiIdMenu = MenuBlock.ofServer("rpiIdMenu", "rest/item/type/RaspberryDeviceEntity",
                "Select RPI", "-");
        this.hiloMenu = MenuBlock.ofStatic("hiloMenu", Hilo.class);

        this.pullMenu = MenuBlock.ofStatic("pullMenu", PinPullResistance.class);

        this.ds18b20Menu = MenuBlock.ofServer("ds18b20Menu", "rest/item/raspberry/DS18B20", "Select DS18B20", "-");

        String pin = this.allPinMenu.getFirstValue();

        this.writePin = Scratch3Block.ofHandler(0, "set_gpio", BlockType.command, "Set [HILO] to pin [PIN] of [RPI]", this::writePin);
        this.writePin.addArgument("PIN", ArgumentType.string, pin, this.allPinMenu);
        this.writePin.addArgument("HILO", ArgumentType.string, Hilo.low.name(), this.hiloMenu);
        this.writePin.addArgumentServerSelection("RPI", this.rpiIdMenu);

        String pwmPin = this.pwmPinMenu.getFirstValue();
        if (pwmPin != null) {
            this.writePwmPin = Scratch3Block.ofHandler(0, "set_pwm_gpio", BlockType.command, "Set  pwm [VALUE] to pin [PIN] of [RPI]", this::writePwmPin);
            this.writePwmPin.addArgument("PIN", ArgumentType.string, pwmPin, this.pwmPinMenu);
            this.writePwmPin.addArgument("VALUE", ArgumentType.number, "255");
            this.writePwmPin.addArgumentServerSelection("RPI", this.rpiIdMenu);
        }

        this.isGpioInState = Scratch3Block.ofEvaluate(2, "get_gpio", BlockType.reporter, "[PIN] of [RPI]", this::isGpioInStateHandler);
        this.isGpioInState.addArgument("PIN", ArgumentType.string, pin, this.allPinMenu);
        this.isGpioInState.addArgumentServerSelection("RPI", this.rpiIdMenu);

        this.whenGpioInState = Scratch3Block.ofHandler(3, "when_gpio", BlockType.hat, "when [PIN] of [RPI] is [HILO]", this::whenGpioInState);
        this.whenGpioInState.addArgument("PIN", ArgumentType.string, pin, this.allPinMenu);
        this.whenGpioInState.addArgumentServerSelection("RPI", this.rpiIdMenu);
        this.whenGpioInState.addArgument("HILO", ArgumentType.string, Hilo.low.name(), this.hiloMenu);

        this.set_pull = Scratch3Block.ofHandler(4, "set_pull", BlockType.command, "set [PULL] to [PIN] of [RPI]", this::setPullStateHandler);
        this.set_pull.addArgument("PIN", ArgumentType.string, pin, this.allPinMenu);
        this.set_pull.addArgumentServerSelection("RPI", this.rpiIdMenu);
        this.set_pull.addArgument("PULL", ArgumentType.string, PinPullResistance.PULL_DOWN.name(), this.pullMenu);

        this.ds18b20Value = Scratch3Block.ofEvaluate(4, "ds18B20_status", BlockType.reporter, "DS18B20 [DS18B20] of [RPI]", this::getDS18B20ValueHandler);
        this.ds18b20Value.addArgument("DS18B20", ArgumentType.string, "-", this.ds18b20Menu);
        this.ds18b20Value.addArgumentServerSelection("RPI", this.rpiIdMenu);

        this.postConstruct();
    }

    private Float getDS18B20ValueHandler(WorkspaceBlock workspaceBlock) {
        String ds18b20Id = workspaceBlock.getMenuValue("DS18B20", ds18b20Menu, String.class);
        return raspberryGPIOService.getDS18B20Value(ds18b20Id);
    }

    private void setPullStateHandler(WorkspaceBlock workspaceBlock) {
        PinPullResistance pullResistence = workspaceBlock.getMenuValue("PULL", pullMenu, PinPullResistance.class);
        RaspberryGpioPin pin = getPin(workspaceBlock);

        raspberryGPIOService.setPullResistance(pin, pullResistence);
    }

    private void whenGpioInState(WorkspaceBlock workspaceBlock) {
        RaspberryGpioPin pin = getPin(workspaceBlock);
        Hilo state = getHilo(workspaceBlock);
        BroadcastLock lock = broadcastLockManager.getOrCreateLock(workspaceBlock.getId());

        raspberryGPIOService.addGpioListener(pin, state.getPinState(), lock::signalAll);

        WorkspaceBlock substack = workspaceBlock.getNext();
        if (substack != null) {
            while (!Thread.currentThread().isInterrupted()) {
                if (lock.await(workspaceBlock)) {
                    substack.handle();
                }
            }
        }
    }

    private boolean isGpioInStateHandler(WorkspaceBlock workspaceBlock) {
        RaspberryGpioPin pin = getPin(workspaceBlock);
        return raspberryGPIOService.getValue(pin);
    }

    private void writePwmPin(WorkspaceBlock workspaceBlock) {
        RaspberryGpioPin pin = getPin(workspaceBlock);
        Integer value = workspaceBlock.getInputInteger("VALUE");
        raspberryGPIOService.setPwmValue(pin, value);
    }

    private void writePin(WorkspaceBlock workspaceBlock) {
        RaspberryGpioPin pin = getPin(workspaceBlock);
        Hilo state = getHilo(workspaceBlock);
        raspberryGPIOService.setValue(pin, state.getPinState());
    }

    private Hilo getHilo(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getMenuValue("HILO", this.hiloMenu, Hilo.class);
    }

    private RaspberryGpioPin getPin(WorkspaceBlock workspaceBlock) {
        return workspaceBlock.getMenuValue("PIN", allPinMenu, RaspberryGpioPin.class);
    }
}
