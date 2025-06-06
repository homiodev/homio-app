package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.hapjava.accessories.HomekitAccessory;
import io.github.hapjava.server.HomekitAccessoryCategories;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitRoot;
import io.github.hapjava.server.impl.HomekitServer;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextVar;
import org.homio.api.service.EntityService;
import org.homio.api.ui.dialog.DialogModel;
import org.homio.app.manager.common.impl.ContextNetworkImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;

@Log4j2
public class HomekitService extends EntityService.ServiceInstance<HomekitEntity> {
    private final @NotNull Map<String, HomekitAccessory> createdAccessories = new HashMap<>();
    @Getter
    private final @NotNull Map<String, HomekitEndpointEntity> endpoints = new HashMap<>();
    private int configurationRevision = 1;
    private HomekitServer homekitServer;
    private HomekitAuthInfoImpl authInfo;
    private @Nullable InetAddress networkInterface;
    private HomekitRoot bridge;

    public HomekitService(@NotNull Context context, @NotNull HomekitEntity entity) {
        super(context, entity, true, "homekit");
        context
                .network()
                .addNetworkAddressChanged(
                        "homekit",
                        (added, removed) -> {
                            if (!entity.getStatus().isOnline()) {
                                return;
                            }
                            removed.forEach(i -> {
                                if (i.address().equals(networkInterface)) {
                                    stopHomekitServer();
                                }
                            });
                            if (bridge == null && !added.isEmpty()) {
                                try {
                                    startHomekitServer();
                                } catch (Exception e) {
                                    log.warn("could not initialize HomeKit bridge: {}", e.getMessage());
                                }
                            }
                        });
    }

    @Override
    public boolean isInternetRequiredForService() {
        return true;
    }

    @Override
    public void destroy(boolean forRestart, @Nullable Exception ex) throws Exception {
        stopHomekitServer();
    }

    private void stopHomekitServer() {
        bridge.stop();
        if (homekitServer != null) {
            homekitServer.stop();
        }
        bridge = null;
    }

    @SneakyThrows
    @Override
    protected void initialize() {
        // destroy(true, null);
        endpoints.clear();
        networkInterface = InetAddress.getByName(MACHINE_IP_ADDRESS);
        startHomekitServer();
        bridge.batchUpdate();
        for (HomekitEndpointEntity endpoint : entity.getItems()) {
            addEndpoint(endpoint);
        }
        bridge.completeUpdateBatch();
    }

    @SneakyThrows
    @JsonIgnore
    private void startHomekitServer() {
        this.authInfo = new HomekitAuthInfoImpl();
        this.homekitServer = createHomekitServer();
        startBridge();
    }

    private void startBridge() throws IOException {
        this.bridge =
                homekitServer.createBridge(
                        authInfo,
                        entity.getName(),
                        HomekitAccessoryCategories.BRIDGES,
                        "Homio",
                        "Homio",
                        "none",
                        "1.0",
                        "3.0");
        createdAccessories.values().forEach(bridge::addAccessory);
        makeNewConfigurationRevision();
        bridge.start();
    }

    private @NotNull HomekitServer createHomekitServer() throws IOException {
        var mdns = ((ContextNetworkImpl) context.network()).getPrimaryMDNS(null);
        if (mdns != null) {
            return new HomekitServer(mdns, entity.getPort());
        } else {
            return new HomekitServer(networkInterface, entity.getPort());
        }
    }

/*
    public void clearHomekitPairings() {
        for (int i = 1; i <= authInfos.size(); ++i) {
            clearHomekitPairings(i);
        }
    }
*/

    /*public void clearHomekitPairings(int instance) {
        if (instance < 1 || instance > authInfos.size()) {
            log.warn("Instance {} is out of range 1..{}.", instance, authInfos.size());
            return;
        }

        try {
            authInfos.get(instance - 1).clear();
            bridges.get(instance - 1).refreshAuthInfo();
        } catch (Exception e) {
            log.warn("could not clear HomeKit pairings", e);
        }
    }*/

    public void addEndpoint(HomekitEndpointEntity endpoint) {
        endpoints.put(endpoint.getName(), endpoint);
        var accessory = endpoint.createAccessory(this);
        if (accessory.isLinkedServiceOnly()) {
            /*log.warn("Item '{}' is a '{}' which must be nested another another accessory.", taggedItem.getName(),
                    endpoint.getType());*/
            return;
        }
        createdAccessories.put(endpoint.getName(), accessory);
        bridge.addAccessory(accessory);
        // TODO: we need to call this if we changed endpoint configuration
//        bridge.setConfigurationIndex(configurationRevision);
    }

    public void makeNewConfigurationRevision() {
        configurationRevision = (configurationRevision + 1) % 65535;
        final HomekitRoot bridge = this.bridge;
        try {
            if (bridge != null) {
                bridge.setConfigurationIndex(configurationRevision);
            }
        } catch (IOException e) {
            log.warn("Could not update configuration revision number", e);
        }
    }

    public @NotNull Context getContext() {
        return context;
    }

    private class HomekitAuthInfoImpl implements HomekitAuthInfo {

        @Override
        public String getSetupId() {
            return entity.getSetupId();
        }

        @Override
        public String getPin() {
            return entity.getPin();
        }

        @Override
        public String getMac() {
            return entity.getMac();
        }

        @Override
        public BigInteger getSalt() {
            return new BigInteger(entity.getSalt());
        }

        @Override
        public byte[] getPrivateKey() {
            return Base64.getDecoder().decode(entity.getPrivateKey());
        }

        @Override
        public void createUser(String user, byte[] publicKey, boolean isAdmin) {
            entity.updateJsonDataMap("users", String.class, map ->
                    map.put(user, Base64.getEncoder().encodeToString(publicKey)));
            context.db().save(entity);
        }

        @Override
        public void removeUser(String user) {
            if (entity.getUsersInternal().containsKey(user)) {
                entity.updateJsonDataMap("users", Integer.class, m -> m.remove(user));
                context.db().save(entity);
            }
        }

        @Override
        public byte[] getUserPublicKey(String user) {
            String encodedKey = entity.getUsersInternal().get(user);
            if (encodedKey != null) {
                return Base64.getDecoder().decode(encodedKey);
            } else {
                return null;
            }
        }

        @Override
        public boolean hasUser() {
            return !entity.getUsersInternal().isEmpty();
        }

        @Override
        public Collection<String> listUsers() {
            return entity.getUsersInternal().keySet();
        }

        @Override
        public boolean userIsAdmin(String username) {
            return true;
        }

        public void clear() {

        }
    }
}
