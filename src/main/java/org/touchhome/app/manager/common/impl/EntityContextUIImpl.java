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
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.v1.UIInputBuilderImpl;
import org.touchhome.app.manager.common.v1.item.UIInputEntityActionHandler;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.model.rest.DynamicUpdateRequest;
import org.touchhome.app.notification.BellNotification;
import org.touchhome.app.notification.HeaderButtonNotification;
import org.touchhome.app.notification.ProgressNotification;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.rest.widget.WidgetChartsController;
import org.touchhome.bundle.api.EntityContextUI;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.model.ActionResponseModel;
import org.touchhome.bundle.api.setting.SettingPluginButton;
import org.touchhome.bundle.api.setting.SettingPluginStatus;
import org.touchhome.bundle.api.ui.UISidebarMenu;
import org.touchhome.bundle.api.ui.action.UIActionHandler;
import org.touchhome.bundle.api.ui.dialog.DialogModel;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputEntity;
import org.touchhome.bundle.api.util.NotificationLevel;
import org.touchhome.common.fs.TreeNode;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;

@Log4j2
@RequiredArgsConstructor
public class EntityContextUIImpl implements EntityContextUI {
    public final Map<DynamicUpdateRequest, DynamicUpdateContext> dynamicUpdateRegisters = new HashMap<>();

    private final Map<String, DialogModel> dialogRequest = new ConcurrentHashMap<>();
    private final Map<String, BellNotification> bellNotifications = new ConcurrentHashMap<>();
    private final Map<String, HeaderButtonNotification> headerButtonNotifications = new ConcurrentHashMap<>();
    private final Map<String, ProgressNotification> progressMap = new ConcurrentHashMap<>();
    private Map<String, BellNotification> bundleEntryPointNotifications = new HashMap<>();

    private final SimpMessagingTemplate messagingTemplate;

    @Getter
    private final EntityContextImpl entityContext;

    private static final Set<String> ALLOWED_DYNAMIC_VALUES = new HashSet<>(
            Arrays.asList(
                    WidgetChartsController.SingleValueData.class.getSimpleName(),
                    WidgetChartsController.TimeSeriesChartData.class.getSimpleName(),
                    TreeNode.class.getSimpleName() // console tree
            )
    );

    @Override
    public void addBellNotification(@NotNull String entityID, @NotNull String name, @NotNull String value,
                                    @NotNull NotificationLevel level, @Nullable Consumer<UIInputBuilder> actionSupplier) {
        BellNotification bellNotification = new BellNotification(entityID).setValue(value).setTitle(name).setLevel(level);
        if (actionSupplier != null) {
            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
            actionSupplier.accept(uiInputBuilder);
            bellNotification.setActions(uiInputBuilder.buildAll());
        }
        bellNotifications.put(entityID, bellNotification);
        sendGlobal(GlobalSendType.bell, entityID, value, name, new JSONObject().put("level", level.name()));
    }

    public void registerForUpdates(DynamicUpdateRequest request) {
        DynamicUpdateContext context = dynamicUpdateRegisters.get(request);
        if (context == null) {
            dynamicUpdateRegisters.put(request, new DynamicUpdateContext());
        } else {
            context.timeout = System.currentTimeMillis(); // refresh timer
            context.registerCounter.incrementAndGet();
        }
        entityContext.event().addEventListener(request.getDynamicUpdateId() + "_" + request.getType(), o -> {
            this.sendDynamicUpdate(request, o);
        });
    }

    public void unRegisterForUpdates(DynamicUpdateRequest request) {
        DynamicUpdateContext context = dynamicUpdateRegisters.get(request);
        if (context != null && context.registerCounter.decrementAndGet() == 0) {
            dynamicUpdateRegisters.remove(request);
        }
    }

    public void sendDynamicUpdate(@NotNull DynamicUpdateRequest request, @Nullable Object value) {
        if (value != null) {
            String type = calcDynamicTypeFromValue(value);
            if (request.getType().equals(type)) {
                DynamicUpdateContext context = dynamicUpdateRegisters.get(request);
                if (context != null) {
                    if (System.currentTimeMillis() - context.timeout > 60000) {
                        dynamicUpdateRegisters.remove(request);
                    } else {
                        sendGlobal(GlobalSendType.dynamicUpdate, null, value, null,
                                new JSONObject().put("dynamicRequest", request));
                    }
                }
            }
        }
    }

    @Override
    public void removeBellNotification(@NotNull String entityID) {
        bellNotifications.remove(entityID);
        sendGlobal(GlobalSendType.bell, entityID, null, null, new JSONObject().put("action", "remove"));
    }

    public void sendAudio(String url) {
        sendGlobal(GlobalSendType.audio, String.valueOf(url.hashCode()), url);
    }

    @Override
    public UIInputBuilder inputBuilder() {
        return new UIInputBuilderImpl(entityContext);
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
        sendGlobal(GlobalSendType.progress, key, progress, message,
                cancellable ? new JSONObject().put("cancellable", true) : null);
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
                entityContext.bgp().run(key + "-dialog-timeout", dialogModel.getMaxTimeoutInSec() * 1000L, () ->
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
                                @Nullable String icon, boolean rotate, Integer border,
                                @Nullable Integer duration, @Nullable Class<? extends BaseEntity> page,
                                @Nullable Class<? extends SettingPluginButton> hideAction) {
        HeaderButtonNotification topJson = new HeaderButtonNotification(entityID).setIcon(icon).setColor(color)
                .setTitle(title).setColor(color).setDuration(duration).setIconRotate(rotate).setBorder(border);
        if (page != null) {
            if (!page.isAnnotationPresent(UISidebarMenu.class)) {
                throw new IllegalArgumentException("Trying add header button to page without annotation UISidebarMenu");
            }
            page.getDeclaredAnnotation(UISidebarMenu.class);
            topJson.setPage(StringUtils.defaultIfEmpty(page.getDeclaredAnnotation(UISidebarMenu.class).overridePath(),
                    page.getSimpleName()));
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
        sendGlobal(GlobalSendType.headerButton, entityID, null, null,
                new JSONObject().put("action", "toggle").put("disable", disable));
    }

    private void sendHeaderButtonToUI(HeaderButtonNotification notification, Consumer<JSONObject> additionalSupplier) {
        JSONObject jsonObject = new JSONObject()
                .put("creationTime", notification.getCreationTime())
                .putOpt("icon", notification.getIcon())
                .putOpt("duration", notification.getDuration())
                .putOpt("color", notification.getColor())
                .putOpt("iconRotate", notification.getIconRotate())
                .putOpt("border", notification.getBorder())
                .putOpt("stopAction", notification.getStopAction());
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

        this.bundleEntryPointNotifications = assembleBundleNotifications();
        notificationResponse.bellNotifications.addAll(this.bundleEntryPointNotifications.values());
        return notificationResponse;
    }

    private void addBellNotification(Map<String, BellNotification> map, BellNotification bellNotification) {
        String entityID = bellNotification.getEntityID();
        if (map.containsKey(entityID)) {
            entityID += "~~~" + bellNotification.getCreationTime();
        }
        bellNotification.setEntityID(entityID);
        map.put(entityID, bellNotification);
    }

    private Map<String, BellNotification> assembleBundleNotifications() {
        Map<String, BellNotification> map = new LinkedHashMap<>();
        for (EntityContextImpl.InternalBundleContext bundleContext : entityContext.getBundles().values()) {

            bundleContext.getBundleEntrypoint().assembleBellNotifications((notificationLevel, entityID, title, value) -> {
                addBellNotification(map,
                        new BellNotification(entityID).setTitle(title).setValue(value).setLevel(notificationLevel));
            });

            Class<? extends SettingPluginStatus> statusSettingClass =
                    bundleContext.getBundleEntrypoint().getBundleStatusSetting();
            if (statusSettingClass != null) {
                String bundleID = SettingRepository.getSettingBundleName(entityContext, statusSettingClass);
                SettingPluginStatus.BundleStatusInfo bundleStatusInfo = entityContext.setting().getValue(statusSettingClass);
                SettingPluginStatus settingPlugin = (SettingPluginStatus) EntityContextSettingImpl.settingPluginsByPluginKey.get(
                        SettingEntity.getKey(statusSettingClass));
                String statusEntityID = bundleID + "-status";
                if (bundleStatusInfo != null) {
                    BellNotification bundleStatusNotification = new BellNotification(statusEntityID)
                            .setLevel(bundleStatusInfo.getLevel())
                            .setTitle(bundleID)
                            .setValue(defaultIfEmpty(bundleStatusInfo.getMessage(), bundleStatusInfo.getStatus().name()));

                    UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
                    settingPlugin.setActions(uiInputBuilder);
                    bundleStatusNotification.setActions(uiInputBuilder.buildAll());

                    addBellNotification(map, bundleStatusNotification);
                }

                List<SettingPluginStatus.BundleStatusInfo> transientStatuses = settingPlugin.getTransientStatuses(entityContext);
                if (transientStatuses != null) {
                    for (SettingPluginStatus.BundleStatusInfo transientStatus : transientStatuses) {
                        BellNotification bundleStatusNotification = new BellNotification(statusEntityID)
                                .setLevel(transientStatus.getLevel())
                                .setTitle(bundleID)
                                .setValue(defaultIfEmpty(transientStatus.getMessage(), transientStatus.getStatus().name()));

                        if (transientStatus.getActionHandler() != null) {
                            UIInputBuilder uiInputBuilder = entityContext.ui().inputBuilder();
                            transientStatus.getActionHandler().accept(uiInputBuilder);
                            bundleStatusNotification.setActions(uiInputBuilder.buildAll());
                        }

                        addBellNotification(map, bundleStatusNotification);
                    }
                }
            }
        }
        return map;
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
                    this.removeHeaderButton(
                            notificationModel.getEntityID()); // request to remove header button if no confirmation exists
                }
            }
        }
    }

    public ActionResponseModel handleNotificationAction(String entityID, String actionEntityID, String value) {
        BellNotification bellNotification = bellNotifications.get(entityID);
        if (bellNotification == null) {
            bellNotification = this.bundleEntryPointNotifications.get(entityID);
        }
        if (bellNotification == null) {
            throw new IllegalArgumentException("Unable to find header notification: <" + entityID + ">");
        }
        UIInputEntity action =
                bellNotification.getActions().stream().filter(a -> a.getEntityID().equals(actionEntityID)).findAny()
                        .orElseThrow(() ->
                                new IllegalStateException("Unable to find action: <" + entityID + ">"));
        if (action instanceof UIInputEntityActionHandler) {
            UIActionHandler actionHandler = ((UIInputEntityActionHandler) action).getActionHandler();
            if (actionHandler != null) {
                return actionHandler.handleAction(entityContext, new JSONObject().put("value", value));
            }
        }
        throw new RuntimeException("Action: " + entityID + " has incorrect format");
    }

    private String calcDynamicTypeFromValue(Object value) {
        String dynamicType = value.getClass().getSimpleName();
        if (ALLOWED_DYNAMIC_VALUES.contains(dynamicType)) {
            return dynamicType;
        }
        throw new IllegalStateException("Unable to evaluate dynamic type from value: " + value.getClass().getSimpleName());
    }

    @Getter
    public static class NotificationResponse {
        private final Set<BellNotification> bellNotifications = new TreeSet<>();
        private Collection<HeaderButtonNotification> headerButtonNotifications;
        private Collection<ProgressNotification> progress;
        private Collection<DialogModel> dialogs;
    }

    private static class DynamicUpdateContext {
        private long timeout = System.currentTimeMillis();
        private final AtomicInteger registerCounter = new AtomicInteger(0);
    }
}
