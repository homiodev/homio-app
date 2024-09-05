package org.homio.app.service.cloud;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sshtools.client.SshClient;
import com.sshtools.client.SshClientContext;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.ContextUI.NotificationBlockBuilder;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.service.CloudProviderService;
import org.homio.api.ui.UI.Color;
import org.homio.api.ui.field.action.ActionInputParameter;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.HardwareUtils;
import org.homio.api.util.Lang;
import org.homio.app.ssh.SshCloudEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.api.ContextSetting.SERVER_PORT;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;
import static org.homio.app.ssh.SshGenericEntity.buildSshKeyPair;

@Log4j2
@Component
@RequiredArgsConstructor
public class SshTunnelCloudProviderService implements CloudProviderService<SshCloudEntity> {

    private final Context context;
    private final Consumer<NotificationBlockBuilder> TRY_CONNECT_HANDLER = tryConnectHandler();
    private SshCloudEntity entity;
    private final Consumer<NotificationBlockBuilder> NOT_SYNC_HANDLER = buildNotSyncHandler();
    private SshClient ssh;

    @Override
    public void start(Runnable onSuccess) throws Exception {
        if (!entity.isHasPrivateKey()) {
            throw new ServerException("ERROR.PRIVATE_KEY_NOT_FOUND").setLog(false);
        }
        log.info("SSH cloud: create client context");
        SshClientContext sshClientContext = new SshClientContext();
        log.info("SSH cloud: create client context successfully");
        log.info("SSH cloud: creating ssh client: {}@{}:{}. Thread: {}", entity.getUser(), entity.getHostname(), entity.getPort(),
                Thread.currentThread().getName());
        try {
            ssh = new SshClient(
                    entity.getHostname(),
                    entity.getPort(),
                    entity.getUser() + "-" + HardwareUtils.APP_ID,
                    sshClientContext,
                    entity.getConnectionTimeout() * 1000L,
                    buildSshKeyPair(entity));
            log.info("SSH cloud: allow forwarding");
            ssh.getContext().getForwardingPolicy().allowForwarding();
            log.info("SSH cloud: start remote forwarding");
            entity.setStatusOnline();
            onSuccess.run();
            ssh.startRemoteForwarding("homio.org", 80, "127.0.0.1", SERVER_PORT);
            log.info("SSH cloud: wait for disconnect");
            ssh.getConnection().getDisconnectFuture().waitForever();
            log.warn("Ssh connection finished: {}", entity);
        } catch (ConnectException ex) {
            throw new ConnectException(Lang.getServerMessage("ERROR.CLOUD_CONNECTION", entity.getHostname()));
        } finally {
            if (ssh != null) {
                ssh.close();
            }
        }
        throw new IllegalStateException("SSH closed abnormally");
    }

    @Override
    @SneakyThrows
    public void stop() {
        if (ssh != null) {
            ssh.close();
        }
    }

    @Override
    public ActionResponseModel sync() {
        return openSyncDialog();
    }

    @Override
    public void updateNotificationBlock(@Nullable Exception ex) {
        // login if no private key found
        String name = format("Cloud: ${SELECTION.%s}", StringUtils.uncapitalize(getClass().getSimpleName()));
        context.ui().notification().addBlock("cloud", name, new Icon("fas fa-cloud", "#5C7DAC"), builder -> {
            builder.setStatus(entity.getStatus()).linkToEntity(entity);
            if (!entity.getStatus().isOnline()) {
                String info = defaultIfEmpty(entity.getStatusMessage(), defaultIfEmpty(CommonUtils.getErrorMessage(ex), "Unknown error"));
                builder.addInfo(info, new Icon("fas fa-exclamation")).setTextColor(Color.RED);
            }

            Consumer<NotificationBlockBuilder> syncHandler = getSyncHandler(ex);
            if (syncHandler != null) {
                syncHandler.accept(builder);
            }
        });
    }

    @Override
    public void setCurrentEntity(@NotNull SshCloudEntity sshEntity) {
        this.entity = sshEntity;
    }

    public void handleSync(Context context, ObjectNode params) {
        context.user().assertAdminAccess();
        if (params == null) {
            return;
        }
        RestTemplate restTemplate = new RestTemplate();
        SyncRequest syncRequest = new SyncRequest(
                params.get("email").asText(),
                params.get("password").asText(),
                params.get("passphrase").asText(),
                HardwareUtils.APP_ID,
                true);
        String url = entity.getSyncUrl();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<SyncRequest> request = new HttpEntity<>(syncRequest, headers);

        try {
            ResponseEntity<LoginResponse> response = restTemplate.exchange(url, HttpMethod.POST, request, LoginResponse.class);
            LoginResponse loginResponse = response.getBody();
            if (response.getStatusCode() == HttpStatus.OK && loginResponse != null) {
                entity.setUser(syncRequest.email);
                entity.uploadAndSavePrivateKey(context, loginResponse.getPrivateKey(), syncRequest.passphrase);
            } else {
                log.error("Wrong status response from cloud server: {}", response.getStatusCode());
            }
        } catch (RestClientResponseException ce) {
            log.error("Unable to call cloud sync: {}", CommonUtils.getErrorMessage(ce));
            context.ui().toastr().error(getClientError(ce));
        } catch (Exception ex) {
            log.error("Unable to call cloud sync: {}", CommonUtils.getErrorMessage(ex));
            if (ex.getCause() instanceof UnknownHostException) {
                context.ui().toastr().error(Lang.getServerMessage("ERROR.CLOUD_UNKNOWN_HOST", ex.getCause().getMessage()));
            } else {
                context.ui().toastr().error("W.ERROR.SYNC");
            }
        }
    }

    private Consumer<NotificationBlockBuilder> getSyncHandler(@Nullable Exception ex) {
        if (!entity.isHasPrivateKey()) {
            return NOT_SYNC_HANDLER;
        }
        if (ex != null) {
            // Uses private key not valid. Need recreate new one
            if ("Auth fail".equals(ex.getMessage())) {
                return NOT_SYNC_HANDLER;
            }
        }
        return TRY_CONNECT_HANDLER;
    }

    public ActionResponseModel openSyncDialog() {
        context.ui().dialog().sendDialogRequest("cloud_sync", "CLOUD.SYNC_TITLE",
                (responseType, pressedButton, parameters) ->
                        handleSync(context, parameters),
                dialogModel -> {
                    dialogModel.disableKeepOnUi();
                    List<ActionInputParameter> inputs = new ArrayList<>();
                    inputs.add(ActionInputParameter.text("email", context.user().getLoggedInUserRequire().getEmail()));
                    inputs.add(ActionInputParameter.textRequired("password", "", 8, 40));
                    inputs.add(ActionInputParameter.text("passphrase", ""));
                    dialogModel.submitButton("Login", button -> {
                    }).group("General", inputs);
                });
        return null;
    }

    private Consumer<NotificationBlockBuilder> buildNotSyncHandler() {
        return builder ->
                builder.addInfo("sync", null, "CLOUD.NOT_SYNC").setTextColor(Color.RED)
                        .setRightButton(new Icon("fas fa-right-to-bracket"), "Sync",
                                (context, params) -> openSyncDialog());
    }

    private Consumer<NotificationBlockBuilder> tryConnectHandler() {
        return builder -> builder.addInfo("sync", null, "CLOUD.NOT_SYNC")
                .setTextColor(Color.RED)
                .setRightButton(new Icon("fas fa-rss"), "Connect",
                        (context, params) -> {
                            context.getBean(CloudService.class).start();
                            return null;
                        });
    }

    private String getClientError(RestClientResponseException ce) {
        String errorMessage = null;
        try {
            ObjectNode error = OBJECT_MAPPER.readValue(ce.getResponseBodyAsString(), ObjectNode.class);
            errorMessage = error.path("message").asText("W.ERROR.SYNC");
        } catch (Exception ignore) {
        }
        return Objects.toString(errorMessage, "W.ERROR.SYNC");
    }

    private record SyncRequest(String email, String password, String passphrase, String uuid, boolean recreate) {
    }

    @Getter
    @Setter
    @RequiredArgsConstructor
    private static class LoginResponse {

        private String privateKey;
    }
}
