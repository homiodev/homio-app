package org.touchhome.app.manager.common.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.touchhome.app.config.WebSocketConfig;
import org.touchhome.app.json.HeaderButtonNotification;
import org.touchhome.app.json.ProgressNotification;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.setting.SettingPluginButton;
import org.touchhome.bundle.api.setting.SettingPluginStatus;
import org.touchhome.bundle.api.ui.BellNotification;
import org.touchhome.bundle.api.util.NotificationLevel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Log4j2
@RequiredArgsConstructor
public class EntityContextUIImpl implements EntityContextUI {
    private final Map<String, ConfirmationRequestModel> confirmationRequest = new ConcurrentHashMap<>();
    private final Map<String, BellNotification> bellNotifications = new ConcurrentHashMap<>();
    private final Map<String, HeaderButtonNotification> headerButtonNotifications = new ConcurrentHashMap<>();
    private final Map<String, ProgressNotification> progressMap = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;
    @Getter
    private final EntityContextImpl entityContext;

    @Override
    public void addBellNotification(@NotNull String entityID, @NotNull String name, @NotNull String value, @NotNull NotificationLevel level) {
        bellNotifications.put(entityID, new BellNotification(entityID).setValue(value).setTitle(name).setLevel(level));
        sendGlobal(GlobalSendType.bell, entityID, value, name, new JSONObject().put("level", level.name()));
    }

    @Override
    public void removeBellNotification(@NotNull String entityID) {
        bellNotifications.remove(entityID);
        sendGlobal(GlobalSendType.bell, entityID, null, null, new JSONObject().put("remove", true));
    }

    @Override
    public <T extends BaseEntity> void sendEntityUpdated(T entity) {
        entityContext.sendEntityUpdateNotification(entity, EntityContextImpl.ItemAction.Update);
    }

    @Override
    public void progress(@NotNull String key, double progress, @Nullable String message, boolean cancellable) {
        if (progress >= 100) {
            progressMap.remove(key);
        } else {
            progressMap.put(key, new ProgressNotification(key, progress, message, cancellable));
        }
        sendGlobal(GlobalSendType.progress, key, progress, message, cancellable ? new JSONObject().put("cancellable", true) : null);
    }

    @Override
    public void sendConfirmation(@NotNull String key, @NotNull String title, @NotNull Runnable confirmHandler,
                                 @NotNull Collection<String> messages, String headerButtonAttachTo) {
        if (!confirmationRequest.containsKey(key)) {
            JSONObject params = null;
            ConfirmationRequestModel confirmationRequestModel = new ConfirmationRequestModel(title, key, confirmHandler, messages);
            if (StringUtils.isNotEmpty(headerButtonAttachTo)) {
                HeaderButtonNotification notificationModel = headerButtonNotifications.get(headerButtonAttachTo);
                if (notificationModel != null) {
                    notificationModel.getConfirmations().add(confirmationRequestModel);
                    params = new JSONObject().put("attachTo", headerButtonAttachTo);
                }
            }

            confirmationRequest.put(key, confirmationRequestModel);
            sendGlobal(GlobalSendType.confirmation, key, String.join("~~", messages), title, params);
        }
    }

    @Override
    public void sendNotification(@NotNull String destination, @NotNull String value) {
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, value);
    }

    @Override
    public void sendNotification(@NotNull String destination, @NotNull JSONObject param) {
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, param);
    }

    @Override
    public void addHeaderButton(@NotNull String entityID, String title, @NotNull String icon, @NotNull String color,
                                boolean rotate, Class<? extends SettingPluginButton> stopAction) {
        addHeaderButton(entityID, title, icon, color, rotate, null, stopAction);
    }

    @Override
    public void addHeaderButton(@NotNull String entityID, String title, @NotNull String color, int duration, Class<? extends SettingPluginButton> stopAction) {
        addHeaderButton(entityID, title, null, color, false, duration, stopAction);
    }

    private void addHeaderButton(String entityID, String title, String icon, String color,
                                 boolean rotate, Integer duration, Class<? extends SettingPluginButton> stopAction) {
        HeaderButtonNotification topJson = new HeaderButtonNotification(entityID).setIcon(icon).setColor(color)
                .setTitle(title).setColor(color).setDuration(duration).setIconRotate(rotate);
        if (stopAction != null) {
            topJson.setStopAction("st_" + stopAction.getSimpleName());
        }
        HeaderButtonNotification existedModel = headerButtonNotifications.get(entityID);
        // preserve confirmations
        if (existedModel != null) {
            topJson.getConfirmations().addAll(existedModel.getConfirmations());
        }
        headerButtonNotifications.put(entityID, topJson);
        sendHeaderButtonToUI(topJson, null);
    }

    @Override
    public void removeHeaderButton(@NotNull String entityID, @Nullable String icon, boolean forceRemove) {
        HeaderButtonNotification notification = headerButtonNotifications.get(entityID);
        if (notification != null) {
            if (notification.getConfirmations().isEmpty()) {
                headerButtonNotifications.remove(entityID);
            } else {
                notification.setIconRotate(false);
                notification.setIcon(icon == null ? notification.getIcon() : icon);
            }
            sendHeaderButtonToUI(notification, jsonObject -> jsonObject.put("remove", true).put("forceRemove", forceRemove));
        }
    }

    private void sendHeaderButtonToUI(HeaderButtonNotification notification, Consumer<JSONObject> additionalSupplier) {
        JSONObject jsonObject = new JSONObject().put("icon", notification.getIcon())
                .put("creationTime", notification.getCreationTime())
                .put("duration", notification.getDuration())
                .put("color", notification.getColor())
                .put("iconRotate", notification.isIconRotate())
                .put("confirmations", notification.getConfirmations())
                .put("stopAction", notification.getStopAction());
        if (additionalSupplier != null) {
            additionalSupplier.accept(jsonObject);
        }
        sendGlobal(GlobalSendType.headerButton, notification.getEntityID(), null, notification.getTitle(), jsonObject);
    }

    public NotificationResponse getNotifications() {
        long time = System.currentTimeMillis();
        headerButtonNotifications.entrySet().removeIf(item -> {
            HeaderButtonNotification json = item.getValue();
            return json.getDuration() != null && time - item.getValue().getCreationTime().getTime() > json.getDuration() * 1000;
        });

        NotificationResponse notificationResponse = new NotificationResponse();
        notificationResponse.bellNotifications.addAll(bellNotifications.values());
        notificationResponse.headerButtonNotifications = headerButtonNotifications.values();
        notificationResponse.progress = progressMap.values();

        for (EntityContextImpl.InternalBundleContext bundleContext : entityContext.getBundles().values()) {
            Set<BellNotification> notifications = bundleContext.getBundleEntrypoint().getBellNotifications();
            if (notifications != null) {
                notificationResponse.bellNotifications.addAll(notifications);
            }
            Class<? extends SettingPluginStatus> statusSettingClass = bundleContext.getBundleEntrypoint().getBundleStatusSetting();
            if (statusSettingClass != null) {
                String bundleID = SettingRepository.getSettingBundleName(entityContext, statusSettingClass);
                SettingPluginStatus.BundleStatusInfo bundleStatusInfo = entityContext.setting().getValue(statusSettingClass);
                notificationResponse.bellNotifications.add(new BellNotification(bundleID + "-status")
                        .setLevel(bundleStatusInfo.getLevel())
                        .setTitle(bundleID).setValue(defaultIfEmpty(bundleStatusInfo.getMessage(), bundleStatusInfo.getStatus().name())));
            }
        }
        return notificationResponse;
    }

    public ConfirmationRequestModel removeConfirmation(String entityID) {
        ConfirmationRequestModel confirmationRequestModel = confirmationRequest.remove(entityID);
        if (confirmationRequestModel != null) {
            for (HeaderButtonNotification notificationModel : headerButtonNotifications.values()) {
                if (notificationModel.getConfirmations().remove(confirmationRequestModel) && notificationModel.getConfirmations().isEmpty()) {
                    this.removeHeaderButton(notificationModel.getEntityID()); // request to remove header button if no confirmation exists
                }
            }
        }
        return confirmationRequestModel;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ConfirmationRequestModel {
        final String title;
        final String key;
        @JsonIgnore
        final Runnable handler;
        final Collection<String> messages;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConfirmationRequestModel that = (ConfirmationRequestModel) o;
            return key.equals(that.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }

    @Getter
    public static class NotificationResponse {
        private final Set<BellNotification> bellNotifications = new TreeSet<>();
        private Collection<HeaderButtonNotification> headerButtonNotifications;
        private Collection<ProgressNotification> progress;
    }
}
