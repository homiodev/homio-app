package org.touchhome.app.manager.common.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.json.JSONObject;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.touchhome.app.config.WebSocketConfig;
import org.touchhome.app.json.AlwaysOnTopNotificationModel;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.model.NotificationModel;
import org.touchhome.bundle.api.setting.SettingPluginButton;
import org.touchhome.bundle.api.setting.SettingPluginStatus;
import org.touchhome.bundle.api.util.NotificationLevel;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Log4j2
@RequiredArgsConstructor
public class EntityContextUIImpl implements EntityContextUI {
    public static Map<String, ConfirmationRequestModel> confirmationRequest = new ConcurrentHashMap<>();
    private final Set<NotificationModel> notifications = new ConcurrentSkipListSet<>();

    private final SimpMessagingTemplate messagingTemplate;
    private final EntityContextImpl entityContextImpl;

    @Override
    public void addHeaderNotification(String entityID, String name, String value, NotificationLevel level) {
        notifications.add(new NotificationModel(entityID).setValue(value).setTitle(name).setLevel(level));
        sendGlobal(GlobalSendType.headerNotification, entityID, value, name, new JSONObject().put("level", level.name()));
    }

    @Override
    public void removeHeaderNotification(String entityID) {
        notifications.removeIf(n -> n.getEntityID().equals(entityID));
    }

    @Override
    public void sendConfirmation(String key, String title, Runnable confirmHandler, String... messages) {
        if (!confirmationRequest.containsKey(key) || !confirmationRequest.get(key).handled) {
            confirmationRequest.put(key, new ConfirmationRequestModel(key, confirmHandler));
            sendGlobal(GlobalSendType.confirmation, key, String.join("~~", messages), title);
        }
    }

    @Override
    public void sendNotification(String destination, Object param) {
        if (param instanceof NotificationModel) {
            NotificationLevel level = ((NotificationModel) param).getLevel();
            log.log(level == NotificationLevel.error ? Level.ERROR : (level == NotificationLevel.warning ? Level.WARN : Level.INFO),
                    param.toString());
        }
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, param);
    }

    @Override
    public void showAlwaysOnViewNotification(String entityID, String title, String icon, String color, Integer duration, Class<? extends SettingPluginButton> stopAction) {
        AlwaysOnTopNotificationModel topJson = new AlwaysOnTopNotificationModel(entityID).setIcon(icon).setColor(color)
                .setTitle(title).setColor(color).setDuration(duration);
        if (stopAction != null) {
            topJson.setStopAction("st_" + stopAction.getSimpleName());
        }
        notifications.add(topJson);
        sendGlobal(GlobalSendType.headerButton, entityID, null, title,
                new JSONObject().put("icon", icon).put("creationTime", topJson.getCreationTime()).put("duration", duration)
                        .put("color", color).put("stopAction", topJson.getStopAction()));
    }

    @Override
    public void hideAlwaysOnViewNotification(String key) {
        for (NotificationModel notification : notifications) {
            if (notification.getEntityID().equals(key)) {
                notifications.remove(notification);
                sendGlobal(GlobalSendType.headerButton, key, null, null, new JSONObject().put("remove", true));
            }
        }
    }

    public Set<NotificationModel> getNotifications() {
        long time = System.currentTimeMillis();
        notifications.removeIf(entity -> {
            if (entity instanceof AlwaysOnTopNotificationModel) {
                AlwaysOnTopNotificationModel json = (AlwaysOnTopNotificationModel) entity;
                return json.getDuration() != null && time - entity.getCreationTime().getTime() > json.getDuration() * 1000;
            }
            return false;
        });

        Set<NotificationModel> set = new TreeSet<>(notifications);
        for (EntityContextImpl.InternalBundleContext bundleContext : entityContextImpl.getBundles().values()) {
            Set<NotificationModel> notifications = bundleContext.getBundleEntrypoint().getNotifications();
            if (notifications != null) {
                set.addAll(notifications);
            }
            Class<? extends SettingPluginStatus> statusSettingClass = bundleContext.getBundleEntrypoint().getBundleStatusSetting();
            if (statusSettingClass != null) {
                String bundleID = SettingRepository.getSettingBundleName(entityContextImpl, statusSettingClass);
                SettingPluginStatus.BundleStatusInfo bundleStatusInfo = entityContextImpl.setting().getValue(statusSettingClass);
                NotificationModel notificationModel = new NotificationModel(bundleID + "-status").setLevel(bundleStatusInfo.getLevel())
                        .setTitle(bundleID).setValue(defaultIfEmpty(bundleStatusInfo.getMessage(), bundleStatusInfo.getStatus().name()));
                set.add(notificationModel);
            }
        }

        return set;
    }

    @Getter
    @RequiredArgsConstructor
    public static class ConfirmationRequestModel {
        final String key;
        final Runnable handler;
        @Setter
        boolean handled;
    }
}
