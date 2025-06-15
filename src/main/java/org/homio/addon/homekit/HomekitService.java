package org.homio.addon.homekit;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.github.hapjava.server.HomekitAccessoryCategories;
import io.github.hapjava.server.HomekitAuthInfo;
import io.github.hapjava.server.impl.HomekitRoot;
import io.github.hapjava.server.impl.HomekitServer;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.homekit.accessories.HomekitAccessoryFactory;
import org.homio.api.Context;
import org.homio.api.service.EntityService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.homio.api.util.HardwareUtils.MACHINE_IP_ADDRESS;

@Log4j2
public class HomekitService extends EntityService.ServiceInstance<HomekitEntity> {
    @Getter
    private final @NotNull Map<Long, HomekitEndpointContext> endpoints = new ConcurrentHashMap<>();
    private int configurationRevision = 1;
    private HomekitServer homekitServer;
    private HomekitAuthInfoImpl authInfo;
    private @Nullable InetAddress networkInterface;
    private HomekitRoot bridge;

    public HomekitService(@NotNull Context context, @NotNull HomekitEntity entity) {
        super(context, entity, true, "homekit");
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
        if (bridge != null) {
            bridge.stop();
        }
        if (homekitServer != null) {
            homekitServer.stop();
        }
        homekitServer = null;
        bridge = null;
    }

    @SneakyThrows
    @Override
    protected void initialize() {
        endpoints.clear();
        networkInterface = InetAddress.getByName(MACHINE_IP_ADDRESS);
        startHomekitServer();
        bridge.batchUpdate();

        for (HomekitEndpointEntity endpoint : entity.getSeries()) {
            addEndpoint(endpoint);
        }
        bridge.setConfigurationIndex(makeNewConfigurationRevision());
        bridge.completeUpdateBatch();
    }

    @SneakyThrows
    public void clearHomekitPairings() {
        entity.setMac(HomekitServer.generateMac());
        entity.setPrivateKey(Base64.getEncoder().encodeToString(HomekitServer.generateKey()));
        entity.setSalt(HomekitServer.generateSalt().toString());
        entity.setJsonData("users", null);
        if (bridge != null) {
            bridge.refreshAuthInfo();
        }
        context.db().save(entity);
    }

    @SneakyThrows
    @JsonIgnore
    private void startHomekitServer() {
        this.authInfo = new HomekitAuthInfoImpl();
        this.homekitServer = createHomekitServer();
        startBridge();
    }

    private void startBridge() throws IOException {
        this.bridge = homekitServer.createBridge(
                authInfo,
                entity.getName(),
                HomekitAccessoryCategories.BRIDGES,
                entity.getManufacturer(),
                entity.getModel(),
                entity.getSerialNumber(),
                "1.0",
                "1.0");
        makeNewConfigurationRevision();
        bridge.start();
    }

    private @NotNull HomekitServer createHomekitServer() throws IOException {
        /*var mdns = ((ContextNetworkImpl) context.network()).getPrimaryMDNS(null);
        if (mdns != null) {
            return new HomekitServer(mdns, entity.getPort());
        } else {*/
        return new HomekitServer(networkInterface, entity.getPort());
        //}
    }

    public void addEndpoint(HomekitEndpointEntity endpoint) {
        long code = endpoint.getEntityHashCode();
        if (!endpoints.containsKey(code)) {
            var ctx = new HomekitEndpointContext(endpoint, entity, this);
            endpoints.put(code, ctx);
            String group = endpoint.getGroup();
            if (group.isEmpty()) {
                var accessory = HomekitAccessoryFactory.create(ctx);
                bridge.addAccessory(accessory);
            } else {
                var accessoryGroup = findAccessoryGroup(endpoint);
                if (accessoryGroup == null) {
                    accessoryGroup = new HomekitAccessoryFactory.HomekitGroup(ctx);
                    bridge.addAccessory(accessoryGroup);
                }
                accessoryGroup.addService(ctx);
            }
        }
    }

    public int makeNewConfigurationRevision() {
        configurationRevision = (configurationRevision + 1) % 65535;
        try {
            if (this.bridge != null) {
                this.bridge.setConfigurationIndex(configurationRevision);
            }
        } catch (IOException e) {
            log.warn("Could not update configuration revision number", e);
        }
        return configurationRevision;
    }

    public @NotNull Context getContext() {
        return context;
    }

    public void stateUpdated() {
        context.ui().updateItem(entity);
    }

    private HomekitAccessoryFactory.HomekitGroup findAccessoryGroup(HomekitEndpointEntity endpoint) {
        for (HomekitEndpointContext c : endpoints.values()) {
            if (c.group() instanceof HomekitAccessoryFactory.HomekitGroup hg
                && hg.getGroupName().equals(endpoint.getGroup())) {
                return hg;
            }
        }
        return null;
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
    }
}
