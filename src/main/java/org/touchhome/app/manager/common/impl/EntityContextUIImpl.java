package org.touchhome.app.manager.common.impl;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.Level;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.touchhome.app.config.WebSocketConfig;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.json.AlwaysOnTopNotificationEntityJSON;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.setting.BundleSettingPluginStatus;
import org.touchhome.bundle.api.util.NotificationType;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Log4j2
@RequiredArgsConstructor
public class EntityContextUIImpl implements EntityContextUI {
    public static Map<String, ConfirmationRequestModel> confirmationRequest = new ConcurrentHashMap<>();
    private final Set<NotificationEntityJSON> notifications = new ConcurrentSkipListSet<>();
    private final SimpMessagingTemplate messagingTemplate;
    private final EntityContextImpl entityContextImpl;

    @Override
    public void addHeaderNotification(NotificationEntityJSON notificationEntityJSON) {
        notifications.add(notificationEntityJSON);
        sendNotification(notificationEntityJSON);
    }

    @Override
    public void removeHeaderNotification(NotificationEntityJSON notificationEntityJSON) {
        notifications.remove(notificationEntityJSON);
    }

    @Override
    public void sendConfirmation(String key, String title, Runnable confirmHandler, String... messages) {
        if (!confirmationRequest.containsKey(key) || !confirmationRequest.get(key).handled) {
            confirmationRequest.put(key, new ConfirmationRequestModel(key, confirmHandler));
            sendNotification("-confirmation", NotificationEntityJSON.info(key).setName(title)
                    .setValue(String.join("~~", messages)));
        }
    }

    @Override
    public void sendNotification(String destination, Object param) {
        if (param instanceof NotificationEntityJSON) {
            NotificationType type = ((NotificationEntityJSON) param).getNotificationType();
            log.log(type == NotificationType.error ? Level.ERROR : (type == NotificationType.warning ? Level.WARN : Level.INFO),
                    param.toString());
        }
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, param);
    }

    @Override
    public void hideAlwaysOnViewNotification(String key) {
        for (NotificationEntityJSON notification : notifications) {
            if (notification.getEntityID().equals(key)) {
                notifications.remove(notification);
                ((AlwaysOnTopNotificationEntityJSON) notification).setRemove(true);
                sendNotification(notification);
            }
        }
    }

    public Set<NotificationEntityJSON> getNotifications() {
        long time = System.currentTimeMillis();
        notifications.removeIf(entity -> {
            if (entity instanceof AlwaysOnTopNotificationEntityJSON) {
                AlwaysOnTopNotificationEntityJSON json = (AlwaysOnTopNotificationEntityJSON) entity;
                return json.getDuration() != null && time - entity.getCreationTime().getTime() > json.getDuration() * 1000;
            }
            return false;
        });

        Set<NotificationEntityJSON> set = new TreeSet<>(notifications);
        for (EntityContextImpl.InternalBundleContext bundleContext : entityContextImpl.getBundles().values()) {
            Set<NotificationEntityJSON> notifications = bundleContext.getBundleEntrypoint().getNotifications();
            if (notifications != null) {
                set.addAll(notifications);
            }
            Class<? extends BundleSettingPluginStatus> statusSettingClass = bundleContext.getBundleEntrypoint().getBundleStatusSetting();
            if (statusSettingClass != null) {
                set.add(entityContextImpl.setting().getValue(statusSettingClass)
                        .toNotification(SettingRepository.getSettingBundleName(entityContextImpl, statusSettingClass)));
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
