package org.homio.app.service.cloud;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.homio.app.config.AppProperties;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.EntityContextUI.NotificationBlockBuilder;
import org.homio.bundle.api.entity.UserEntity;
import org.homio.bundle.api.exception.NotFoundException;
import org.homio.bundle.api.model.Status;
import org.homio.bundle.api.service.CloudProviderService;
import org.homio.bundle.api.ui.UI.Color;
import org.homio.bundle.api.ui.field.action.ActionInputParameter;
import org.homio.bundle.api.util.CommonUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Log4j2
@Component
@RequiredArgsConstructor
public class SshTunnelCloudProviderService implements CloudProviderService {

    private final EntityContext entityContext;
    private final AppProperties properties;
    private Status status = Status.UNKNOWN;
    private String statusMessage = null;

    private final Consumer<NotificationBlockBuilder> NOT_SYNC_HANDLER = buildNotSyncHandler();
    private final Consumer<NotificationBlockBuilder> TRY_CONNECT_HANDLER = tryConnectHandler();

    @Override
    public void start() throws Exception {
        try {
            UserEntity user = Objects.requireNonNull(entityContext.getEntity(UserEntity.PREFIX + "primary"));
            String passphrase = user.getJsonData().optString("passphrase", null);
            if (passphrase == null) {
                throw new NotFoundException("Passphrase not configured");
            }
            JSch j = new JSch();
            j.addIdentity(CommonUtils.getSshPath().resolve("id_rsa_homio").toString(), passphrase.getBytes(StandardCharsets.UTF_8));
            Session session = j.getSession(user.getName(), properties.getCloud().getHostname(), 22);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();

            /*JSch j = new JSch();
            Session session = null;
            j.addIdentity("/home/reporting/.ssh/id_rsa");
            j.setKnownHosts("/home/reporting/.ssh/known_hosts");
            session = j.getSession("reporting", "app", 2222);
            session.connect();
            session.setPortForwardingR("homio.org", 30003, "localhost", 9119);*/
            /*try (SshClient ssh = new SshClient("localhost", 22, "username", "password".toCharArray())) {
             *//**
             * First we must allow forwarding. Without this no forwarding is possible. This
             * will allow us to forward from localhost and accept remote forwarding from the
             * remote server.
             *//*
                ssh.getContext().getForwardingPolicy().allowForwarding();

                *//**
             * A local forward allows the ssh client user to connect to a resource
             * on the remote network
             *//*
                ssh.startLocalForwarding("127.0.0.1", 9119, "www.homio.org", 2222);

                *//**
             * A remote forward allows a user to connect from the remote computer to
             * a resource on the client's network
             *//*
                //  ssh.startRemoteForwarding("127.0.0.1", 8080, "service.local", 80);

                *//**
             * If we want to allow other local computers to connect to our forwarding we can
             * allow gateway forwarding. This allows a local forwarding to be started on a
             * wildcard or IP address of the client that can accept connections from external
             * computers. With this enabled, we have to start the forwarding so that we are
             * listening on a publicly accessible interface of the client.
             *//*

                //   ssh.getContext().getForwardingPolicy().allowGatewayForwarding();

                *//**
             * We we start a local forwarding that is accessible by any IP on the clients
             * network. This is called "Gateway Forwarding"
             *//*
                //      ssh.startLocalForwarding("::", 9443, "www.jadaptive.com", 443);

                *//**
             * Wait for the connection to be disconnected.
             *//*
                status = Status.ONLINE;
                ssh.getConnection().getDisconnectFuture().waitForever();


                <dependency>
                  <artifactId>maverick-synergy-client</artifactId>
                  <groupId>com.sshtools</groupId>
                  <version>3.0.10</version>
                </dependency>
            }*/
        } catch (Exception ex) {
            status = Status.ERROR;
            statusMessage = CommonUtils.getErrorMessage(ex);
            throw ex;
        }
    }

    @Override
    public void stop() {

    }

   /* @Override
    public void assembleBellNotifications(BellNotificationBuilder bellNotificationBuilder) {
        if (!Files.exists(CommonUtils.getSshPath().resolve("id_rsa_homio"))) {
            bellNotificationBuilder.danger("private-key", "Cloud", "Private Key not found");
        }
        if (!Files.exists(CommonUtils.getSshPath().resolve("id_rsa_homio.pub"))) {
            bellNotificationBuilder.danger("public-key", "Cloud", "Public Key not found");
        }
        int serviceStatus = machineHardwareRepository.getServiceStatus("homio-tunnel");
        if (serviceStatus == 0) {
            bellNotificationBuilder.info("cloud-status", "Cloud", "Connected");
        } else {
            bellNotificationBuilder.warn("cloud-status", "Cloud", "Connection status not active " + serviceStatus);
        }
    }*/

    @Override
    public void updateNotificationBlock(@Nullable Exception ex) {
        // login if no private key found
        boolean hasPrivateKey = false;
        String name = format("Cloud: ${selection.%s}", getClass().getSimpleName());
        entityContext.ui().addNotificationBlock("cloud", name, "fas fa-cloud", "#5C7DAC", builder -> {
            builder.setStatus(status);
            if (!status.isOnline()) {
                builder.addInfo(defaultIfEmpty(statusMessage, defaultIfEmpty(CommonUtils.getErrorMessage(ex), "Unknown error")),
                    Color.RED, "fas fa-exclamation", null);
            }

            Consumer<NotificationBlockBuilder> syncHandler = getSyncHandler(ex);
            if (syncHandler != null) {
                syncHandler.accept(builder);
            }
        });
    }

    @Override
    public Status getStatus() {
        return Status.ERROR;
        /*int serviceStatus = machineHardwareRepository.getServiceStatus("homio-tunnel");
        return serviceStatus == 0 ? ServerConnectionStatus.CONNECTED.name() : ServerConnectionStatus.DISCONNECTED_WIDTH_ERRORS.name();*/
    }

    @Override
    public String getName() {
        return "ssh-tunnel";
    }

    private Consumer<NotificationBlockBuilder> getSyncHandler(@Nullable Exception ex) {
        if (!Files.exists(CommonUtils.getSshPath().resolve("id_rsa_homio"))) {
            return NOT_SYNC_HANDLER;
        }
        if (ex != null) {
            // Uses private key not valid. Need recreate new one
            if ("Auth fail".equals(ex.getMessage())) {
                return NOT_SYNC_HANDLER;
            }
            if (ex.getCause() instanceof UnknownHostException) {
                return TRY_CONNECT_HANDLER;
            }
        }
        return null;
    }

    @Override
    public String getStatusMessage() {
        return null;
    }

    @Getter
    @RequiredArgsConstructor
    private static class LoginBody {

        private final String user;
        private final String password;
        private final String passphrase;
        private final boolean recreate;
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class LoginResponse {

        private byte[] privateKey;
    }

    private Consumer<NotificationBlockBuilder> buildNotSyncHandler() {
        return builder -> builder.addButtonInfo("cloud.not_sync", Color.RED, null, null,
            "fas fa-right-to-bracket", "Sync", null, (entityContext, params) -> {
                entityContext.ui().sendDialogRequest("cloud_sync", "cloud.sync_title", (responseType, pressedButton, parameters) ->
                        handleSync(entityContext, parameters),
                    dialogModel -> {
                        dialogModel.disableKeepOnUi();
                        List<ActionInputParameter> inputs = new ArrayList<>();
                        inputs.add(ActionInputParameter.text("field.email",
                            entityContext.getUserRequire().getName()));
                        inputs.add(ActionInputParameter.text("field.password", ""));
                        dialogModel.submitButton("Login", button -> {
                        }).group("General", inputs);
                    });
                return null;
            });
    }

    private Consumer<NotificationBlockBuilder> tryConnectHandler() {
        return builder -> builder.addButtonInfo("cloud.not_sync", Color.RED, null, null,
            "fas fa-rss", "Connect", null, (entityContext, params) -> {
                entityContext.getBean(CloudService.class).restart();
                return null;
            });
    }

    private void handleSync(EntityContext entityContext, ObjectNode parameters) {
        if (parameters == null) {
            return;
        }
        RestTemplate restTemplate = new RestTemplate();
        LoginBody loginBody = new LoginBody(
            parameters.get("field.email").asText(),
            parameters.get("field.password").asText(),
            parameters.get("field.password").asText(),
            false);
        String url = properties.getCloud().getLoginUrl();
        LoginBody body = new LoginBody(loginBody.user, loginBody.password, loginBody.passphrase, false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<LoginBody> request = new HttpEntity<>(body, headers);
        ResponseEntity<LoginResponse> response = restTemplate.postForEntity(url, request, LoginResponse.class);
        LoginResponse loginResponse = response.getBody();
        if (response.getStatusCode() == HttpStatus.OK && loginResponse != null) {
            CommonUtils.writeToFile(CommonUtils.getSshPath().resolve("id_rsa_homio"),
                loginResponse.getPrivateKey(), false);
            UserEntity user = entityContext.getUserRequire();
            user.getJsonData().put("passphrase", loginBody.passphrase);
            entityContext.save(user);
            entityContext.getBean(CloudService.class).restart();
        } else {
            log.error("Wrong status response from cloud server: {}", response.getStatusCode());
        }
    }
}
