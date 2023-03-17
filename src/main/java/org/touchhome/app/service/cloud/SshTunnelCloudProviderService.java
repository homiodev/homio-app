package org.touchhome.app.service.cloud;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.hardware.other.MachineHardwareRepository;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.service.CloudProviderService;
import org.touchhome.bundle.api.ui.UI.Color;
import org.touchhome.bundle.api.ui.field.action.ActionInputParameter;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.exception.NotFoundException;
import org.touchhome.common.util.CommonUtils;

@Log4j2
@Component
@RequiredArgsConstructor
public class SshTunnelCloudProviderService implements CloudProviderService {

    private final MachineHardwareRepository machineHardwareRepository;
    private final EntityContext entityContext;
    private final TouchHomeProperties properties;
    private Status status = Status.UNKNOWN;
    private String statusMessage = null;

    @Override
    public void start() {
        updateNotificationBlock();
        try {
            UserEntity user = entityContext.getUserRequire();
            String passphrase = user.getJsonData().optString("passphrase", null);
            if (passphrase == null) {
                throw new NotFoundException("Cloud not configured");
            }
            JSch j = new JSch();
            j.addIdentity(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome").toString(), passphrase.getBytes(StandardCharsets.UTF_8));
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
            session.setPortForwardingR("touchhome.org", 30003, "localhost", 9119);*/
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
                ssh.startLocalForwarding("127.0.0.1", 9119, "www.touchhome.org", 2222);

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
                updateNotificationBlock();
                ssh.getConnection().getDisconnectFuture().waitForever();
                updateNotificationBlock();
            }*/
        } catch (Exception ex) {
            status = Status.ERROR;
            statusMessage = CommonUtils.getErrorMessage(ex);
            updateNotificationBlock();
        }
    }

    @Override
    public void stop() {

    }

   /* @Override
    public void assembleBellNotifications(BellNotificationBuilder bellNotificationBuilder) {
        if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome"))) {
            bellNotificationBuilder.danger("private-key", "Cloud", "Private Key not found");
        }
        if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome.pub"))) {
            bellNotificationBuilder.danger("public-key", "Cloud", "Public Key not found");
        }
        int serviceStatus = machineHardwareRepository.getServiceStatus("touchhome-tunnel");
        if (serviceStatus == 0) {
            bellNotificationBuilder.info("cloud-status", "Cloud", "Connected");
        } else {
            bellNotificationBuilder.warn("cloud-status", "Cloud", "Connection status not active " + serviceStatus);
        }
    }*/

    public void updateNotificationBlock() {
        // login if no private key found
        boolean hasPrivateKey = false;
        String name = format("Cloud: ${selection.%s}", getClass().getSimpleName());
        entityContext.ui().addNotificationBlock("cloud", name, "fas fa-cloud", "#5C7DAC", builder -> {
            builder.setStatus(status);
            if (!status.isOnline()) {
                builder.addInfo(StringUtils.defaultIfEmpty(statusMessage, "Unknown status"),
                    Color.RED, "fas fa-exclamation", null);
            }

            if (!Files.exists(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome"))) {
                builder.addButtonInfo("cloud.not_sync", Color.RED, null, null,
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
        });
    }

    @Override
    public String getName() {
        return "ssh-tunnel";
    }

    @Override
    public Status getStatus() {
        return Status.ERROR;
        /*int serviceStatus = machineHardwareRepository.getServiceStatus("touchhome-tunnel");
        return serviceStatus == 0 ? ServerConnectionStatus.CONNECTED.name() : ServerConnectionStatus.DISCONNECTED_WIDTH_ERRORS.name();*/
    }

    @Override
    public String getStatusMessage() {
        return null;
    }

    private void handleSync(EntityContext entityContext, ObjectNode parameters) {
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
            TouchHomeUtils.writeToFile(TouchHomeUtils.getSshPath().resolve("id_rsa_touchhome"),
                loginResponse.getPrivateKey(), false);
            UserEntity user = entityContext.getUserRequire();
            user.getJsonData().put("passphrase", loginBody.passphrase);
            entityContext.save(user);
            this.start();
        } else {
            log.error("Wrong status response from cloud server: {}", response.getStatusCode());
        }
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
}
