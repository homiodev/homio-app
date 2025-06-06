package org.homio.addon.homekit.accessories;

import io.github.hapjava.accessories.SwitchAccessory;
import io.github.hapjava.characteristics.HomekitCharacteristicChangeCallback;
import io.github.hapjava.services.impl.SwitchService;
import org.homio.addon.homekit.HomekitEndpointEntity;
import org.homio.addon.homekit.enums.HomekitCharacteristicType;
import org.homio.api.ContextVar;
import org.homio.api.state.OnOffType;

import java.util.concurrent.CompletableFuture;

public class HomekitSwitchImpl extends AbstractHomekitAccessoryImpl implements SwitchAccessory {

    private final ContextVar.Variable variable;

    public HomekitSwitchImpl(HomekitEndpointEntity endpoint) {
        super(endpoint);
        this.variable = endpoint.getVariable(HomekitCharacteristicType.ON_STATE);
        addService(new SwitchService(this));
    }

    @Override
    public CompletableFuture<Boolean> getSwitchState() {
        return CompletableFuture.completedFuture(variable.getValue().boolValue());
    }

    @Override
    public CompletableFuture<Void> setSwitchState(boolean state) {
        variable.set(OnOffType.of(state));
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void subscribeSwitchState(HomekitCharacteristicChangeCallback callback) {
        variable.setListener("homekit-switch-" + endpoint.getId(),
                state -> callback.changed());
    }

    @Override
    public void unsubscribeSwitchState() {
        variable.setListener("homekit-switch-" + endpoint.getId(), null);
    }
}
