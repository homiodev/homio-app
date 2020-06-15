package org.touchhome.bundle.arduino.workspace;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.Hilo;
import org.touchhome.bundle.api.scratch.*;

import static com.pi4j.io.gpio.PinMode.DIGITAL_INPUT;

@Getter
@Component
@Scratch3Extension("arduino")
public class Scratch3ArduinoBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock onOffMenu;
    private final MenuBlock.ServerMenuBlock arduinoIdMenu;
    private final MenuBlock digitalPinMenu;
    private final MenuBlock pwmPinMenu;
    private final MenuBlock allPinMenu;

    private final Scratch3Block pinRead;
    private final Scratch3Block pwmWrite;
    private final Scratch3Block invertPin;
    private final Scratch3Block invertPinNSec;
    private final Scratch3Block digitalWrite;

    public Scratch3ArduinoBlocks(EntityContext entityContext) {
        super("#3cb6cd", entityContext);

        // Menu
        this.digitalPinMenu = MenuBlock.ofStatic("digitalPinMenu", ArduinoGpioPin.class, p -> p.getPinModes().contains(DIGITAL_INPUT));
        this.pwmPinMenu = MenuBlock.ofStatic("pwmPinMenu", ArduinoGpioPin.class, p -> p.getPinModes().contains(DIGITAL_INPUT));
        this.allPinMenu = MenuBlock.ofStatic("allPinMenu", ArduinoGpioPin.class);

        this.arduinoIdMenu = MenuBlock.ofServer("arduinoIdMenu", "rest/item/type/ArduinoDeviceEntity",
                "Select Arduino", "-");
        this.onOffMenu = MenuBlock.ofStatic("onOffMenu", Hilo.class);

        // Blocks
        this.invertPin = Scratch3Block.ofHandler(0, "invertPin", BlockType.command, "Invert Pin [PIN] on [ARDUINO]", this::invertPinHandler);
        this.invertPin.addArgument("PIN", ArgumentType.number, "2", this.digitalPinMenu);
        this.invertPin.addArgumentServerSelection("ARDUINO", this.arduinoIdMenu);

        this.invertPinNSec = Scratch3Block.ofHandler(1, "invertPinNSec", BlockType.command, "Invert Pin [PIN] on [SECONDS]s. on [ARDUINO]", this::invertPinNSecHandler);
        this.invertPinNSec.addArgument("PIN", ArgumentType.number, "2", this.digitalPinMenu);
        this.invertPinNSec.addArgument("SECONDS", ArgumentType.number, "10");
        this.invertPinNSec.addArgumentServerSelection("ARDUINO", this.arduinoIdMenu);


        this.digitalWrite = Scratch3Block.ofHandler(2, "digital_write", BlockType.command, "Write(D) Pin [PIN] [ON_OFF] to [ARDUINO]", this::digitalWriteHandler);
        this.digitalWrite.addArgument("PIN", ArgumentType.number, "2", this.digitalPinMenu);
        this.digitalWrite.addArgument("ON_OFF", ArgumentType.number, "0", this.onOffMenu);
        this.digitalWrite.addArgumentServerSelection("ARDUINO", this.arduinoIdMenu);

        this.pwmWrite = Scratch3Block.ofHandler(3, "pwmWrite", BlockType.command, "Write(PWM) Pin [PIN] [VALUE] to [ARDUINO]", this::pwmWriteHandler);
        this.pwmWrite.addArgument("PIN", ArgumentType.number, "3", this.pwmPinMenu);
        this.pwmWrite.addArgument("VALUE", ArgumentType.number, "50");
        this.pwmWrite.addArgumentServerSelection("ARDUINO", this.arduinoIdMenu);

        this.pinRead = Scratch3Block.ofEvaluate(4, "pinRead", BlockType.reporter, "Read Pin [PIN] from [ARDUINO]", this::pinReadHandler);
        this.pinRead.addArgument("PIN", ArgumentType.number, "2", this.allPinMenu);
        this.pinRead.addArgumentServerSelection("ARDUINO", this.arduinoIdMenu);

        this.postConstruct();
    }

    private Object pinReadHandler(WorkspaceBlock workspaceBlock) {
        return null;
    }

    private void pwmWriteHandler(WorkspaceBlock workspaceBlock) {

    }

    private void digitalWriteHandler(WorkspaceBlock workspaceBlock) {

    }

    private void invertPinNSecHandler(WorkspaceBlock workspaceBlock) {

    }

    private void invertPinHandler(WorkspaceBlock workspaceBlock) {

    }
}
