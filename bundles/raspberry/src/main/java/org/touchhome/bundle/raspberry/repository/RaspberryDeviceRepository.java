package org.touchhome.bundle.raspberry.repository;

import com.pi4j.io.gpio.PinMode;
import org.json.JSONObject;
import org.springframework.stereotype.Repository;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.Option;
import org.touchhome.bundle.api.link.HasWorkspaceVariableLinkAbility;
import org.touchhome.bundle.api.model.workspace.bool.WorkspaceBooleanEntity;
import org.touchhome.bundle.api.repository.AbstractDeviceRepository;
import org.touchhome.bundle.api.util.RaspberryGpioPin;
import org.touchhome.bundle.raspberry.RaspberryGPIOService;
import org.touchhome.bundle.raspberry.model.RaspberryDeviceEntity;
import org.touchhome.bundle.raspberry.workspace.Scratch3RaspberryBlocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class RaspberryDeviceRepository extends AbstractDeviceRepository<RaspberryDeviceEntity> implements HasWorkspaceVariableLinkAbility {

    public static final String PREFIX = "rd_";
    private final Scratch3RaspberryBlocks scratch3RaspberryBlocks;
    private final RaspberryGPIOService raspberryGPIOService;
    private final EntityContext entityContext;

    public RaspberryDeviceRepository(Scratch3RaspberryBlocks scratch3RaspberryBlocks, RaspberryGPIOService raspberryGPIOService, EntityContext entityContext) {
        super(RaspberryDeviceEntity.class, PREFIX);
        this.scratch3RaspberryBlocks = scratch3RaspberryBlocks;
        this.raspberryGPIOService = raspberryGPIOService;
        this.entityContext = entityContext;
    }

    @Override
    public void updateEntityAfterFetch(RaspberryDeviceEntity entity) {
        gatherAvailableLinks(entity);
    }

    private void gatherAvailableLinks(RaspberryDeviceEntity entity) {
        List<Map<Option, String>> links = new ArrayList<>();
        for (RaspberryGpioPin gpioPin : RaspberryGpioPin.values(PinMode.DIGITAL_INPUT)) {
            Map<Option, String> map = new HashMap<>();
            map.put(Option.key(gpioPin.name()).setType("boolean"), getLinkedWorkspaceBooleanVariable(gpioPin));
            links.add(map);
        }
        entity.setAvailableLinks(links);
    }

    private String getLinkedWorkspaceBooleanVariable(RaspberryGpioPin gpioPin) {
        List<RaspberryGPIOService.PinListener> pinListeners = raspberryGPIOService.getDigitalListeners().get(gpioPin);
        if (pinListeners != null) {
            for (RaspberryGPIOService.PinListener pinListener : pinListeners) {
                if (pinListener.getName().startsWith(WorkspaceBooleanEntity.PREFIX)) {
                    WorkspaceBooleanEntity variableEntity = entityContext.getEntity(pinListener.getName());
                    if (variableEntity != null) {
                        return variableEntity.getTitle();
                    }
                }
            }
        }
        return "";
    }

    @Override
    public void createVariable(String entityID, String varGroup, String varName, String key) {
        RaspberryGpioPin raspberryGpioPin = RaspberryGpioPin.values(PinMode.DIGITAL_INPUT).stream().filter(p -> p.name().equals(key)).findAny().orElse(null);
        if (raspberryGpioPin != null) {
            JSONObject parameter = new JSONObject().put("pin", raspberryGpioPin).put("entityID", entityID);
            scratch3RaspberryBlocks.getIsGpioInState().getLinkGenerator().handle(varGroup, varName, parameter);
        }
    }
}
