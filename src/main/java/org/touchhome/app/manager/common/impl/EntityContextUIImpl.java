package org.touchhome.app.manager.common.impl;

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
import org.touchhome.bundle.api.ui.DialogModel;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.util.NotificationLevel;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Log4j2
@RequiredArgsConstructor
public class EntityContextUIImpl implements EntityContextUI {
    private final Map<String, DialogModel> dialogRequest = new ConcurrentHashMap<>();
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
    public void sendDialogRequest(@NotNull DialogModel dialogModel) {
        dialogRequest.computeIfAbsent(dialogModel.getEntityID(), key -> {
            if (StringUtils.isNotEmpty(dialogModel.getHeaderButtonAttachTo())) {
                HeaderButtonNotification notificationModel = headerButtonNotifications.get(dialogModel.getHeaderButtonAttachTo());
                if (notificationModel != null) {
                    notificationModel.getDialogs().add(dialogModel);
                }
            }

            if (dialogModel.getMaxTimeoutInSec() > 0) {
                entityContext.bgp().run(key + "-dialog-timeout", dialogModel.getMaxTimeoutInSec() * 1000, () ->
                        handleDialog(key, DialogResponseType.Timeout, null, null), true);
            }

            sendGlobal(GlobalSendType.dialog, key, dialogModel, null, null);

            return dialogModel;
        });
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
    public void addHeaderButton(@NotNull String entityID, @NotNull String color, @Nullable String title,
                                @Nullable String icon, boolean rotate, boolean border,
                                @Nullable Integer duration, @Nullable Class<? extends BaseEntity> page,
                                @Nullable Class<? extends SettingPluginButton> hideAction) {
        HeaderButtonNotification topJson = new HeaderButtonNotification(entityID).setIcon(icon).setColor(color)
                .setTitle(title).setColor(color).setDuration(duration).setIconRotate(rotate).setBorder(border);
        if (page != null) {
            if (!page.isAnnotationPresent(UISidebarMenu.class)) {
                throw new IllegalArgumentException("Trying add header button to page without annotation UISidebarMenu");
            }
            page.getDeclaredAnnotation(UISidebarMenu.class);
            topJson.setPage(StringUtils.defaultIfEmpty(page.getDeclaredAnnotation(UISidebarMenu.class).overridePath(), page.getSimpleName()));
        }
        if (hideAction != null) {
            topJson.setStopAction("st_" + hideAction.getSimpleName());
        }
        HeaderButtonNotification existedModel = headerButtonNotifications.get(entityID);
        // preserve confirmations
        if (existedModel != null) {
            topJson.getDialogs().addAll(existedModel.getDialogs());
        }
        headerButtonNotifications.put(entityID, topJson);
        sendHeaderButtonToUI(topJson, null);
    }

    @Override
    public void removeHeaderButton(@NotNull String entityID, @Nullable String icon, boolean forceRemove) {
        HeaderButtonNotification notification = headerButtonNotifications.get(entityID);
        if (notification != null) {
            if (notification.getDialogs().isEmpty()) {
                headerButtonNotifications.remove(entityID);
            } else {
                notification.setIconRotate(false);
                notification.setIcon(icon == null ? notification.getIcon() : icon);
            }
            sendHeaderButtonToUI(notification, jsonObject -> jsonObject.put("action", forceRemove ? "forceRemove" : "remove"));
        }
    }

    public void disableHeaderButton(String entityID, boolean disable) {
        sendGlobal(GlobalSendType.headerButton, entityID, null, null, new JSONObject().put("action", "toggle").put("disable", disable));
    }

    private void sendHeaderButtonToUI(HeaderButtonNotification notification, Consumer<JSONObject> additionalSupplier) {
        JSONObject jsonObject = new JSONObject().put("icon", notification.getIcon())
                .put("creationTime", notification.getCreationTime())
                .put("duration", notification.getDuration())
                .put("color", notification.getColor())
                .put("iconRotate", notification.getIconRotate())
                .put("confirmations", notification.getDialogs())
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
        notificationResponse.dialogs = dialogRequest.values();
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

    public void handleDialog(String entityID, DialogResponseType dialogResponseType, String pressedButton, JSONObject params) {
        DialogModel model = dialogRequest.remove(entityID);
        if (model != null) {
            model.getActionHandler().handle(dialogResponseType, pressedButton, params);
            if (dialogResponseType != DialogResponseType.Timeout && model.getMaxTimeoutInSec() > 0) {
                entityContext.bgp().cancelThread(entityID + "dialog-timeout");
            }

            for (HeaderButtonNotification notificationModel : headerButtonNotifications.values()) {
                if (notificationModel.getDialogs().remove(model) && notificationModel.getDialogs().isEmpty()) {
                    this.removeHeaderButton(notificationModel.getEntityID()); // request to remove header button if no confirmation exists
                }
            }
        }
    }

    @Getter
    public static class NotificationResponse {
        private final Set<BellNotification> bellNotifications = new TreeSet<>();
        private Collection<HeaderButtonNotification> headerButtonNotifications;
        private Collection<ProgressNotification> progress;
        private Collection<DialogModel> dialogs;
    }
}
