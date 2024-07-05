package org.homio.app.manager.common.impl;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pivovarit.function.ThrowingBiFunction;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.homio.api.ContextNetwork;
import org.homio.api.ContextUI;
import org.homio.api.audio.AudioFormat;
import org.homio.api.audio.stream.ByteArrayAudioStream;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.BaseEntityIdentifier;
import org.homio.api.entity.HasStatusAndMsg;
import org.homio.api.entity.UserEntity;
import org.homio.api.entity.storage.BaseFileSystemEntity;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.exception.ServerException;
import org.homio.api.fs.TreeNode;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.Status;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.dialog.DialogModel;
import org.homio.api.ui.field.action.HasDynamicContextMenuActions;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.action.v1.UIInputEntity;
import org.homio.api.ui.field.action.v1.layout.UIFlexLayoutBuilder;
import org.homio.api.util.CommonUtils;
import org.homio.api.util.FlowMap;
import org.homio.api.util.Lang;
import org.homio.api.util.NotificationLevel;
import org.homio.app.audio.webaudio.WebAudioAudioSink;
import org.homio.app.builder.ui.UIInputBuilderImpl;
import org.homio.app.builder.ui.UIInputEntityActionHandler;
import org.homio.app.config.WebSocketConfig;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.widget.WidgetEntity;
import org.homio.app.model.rest.DynamicUpdateRequest;
import org.homio.app.notification.HeaderButtonNotification;
import org.homio.app.notification.HeaderButtonSelection;
import org.homio.app.notification.NotificationBlock;
import org.homio.app.notification.NotificationBlock.Info;
import org.homio.app.notification.ProgressNotification;
import org.homio.app.utils.UIFieldUtils;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
@RequiredArgsConstructor
public class ContextUIImpl implements ContextUI {

    public static final @NotNull Map<String, ConsolePlugin<?>> consolePluginsMap = new HashMap<>();
    public static final @NotNull Map<String, ConsolePlugin<?>> consoleRemovablePluginsMap = new HashMap<>();

    public static final @NotNull Map<String, String> customConsolePluginNames = new HashMap<>();
    private static final @NotNull Object EMPTY = new Object();
    private final @NotNull Map<DynamicUpdateRequest, DynamicUpdateContext> dynamicUpdateRegisters = new ConcurrentHashMap<>();
    private final @NotNull Map<String, DialogModel> dialogRequest = new ConcurrentHashMap<>();
    private final @NotNull Map<String, DialogModel> frameRequest = new ConcurrentHashMap<>();
    private final @NotNull Map<String, NotificationBlock> blockNotifications = new ConcurrentHashMap<>();
    private final @NotNull Map<String, HeaderButtonNotification> headerButtonNotifications = new ConcurrentHashMap<>();
    private final @NotNull Map<String, HeaderButtonSelection> headerMenuButtons = new ConcurrentHashMap<>();

    // constructor parameters
    private final @Getter
    @Accessors(fluent = true) ContextImpl context;
    private final @NotNull Map<String, ProgressNotification> progressMap = new ConcurrentHashMap<>();
    private final @NotNull SimpMessagingTemplate messagingTemplate;
    private final @NotNull Map<String, SendUpdateContext> sendToUIMap = new ConcurrentHashMap<>();
    private final @NotNull ReentrantLock treeNodeLock = new ReentrantLock();
    private final @NotNull Map<String, TreeNode> treeNodesSendToUIMap = new ConcurrentHashMap<>();

    private final @Getter
    @Accessors(fluent = true) ContextUIToastrImpl toastr = new ContextUIToastrImpl();
    private final @Getter
    @Accessors(fluent = true) ContextUINotificationImpl notification = new ContextUINotificationImpl();
    private final @Getter
    @Accessors(fluent = true) ContextUIConsoleImpl console = new ContextUIConsoleImpl();
    private final @Getter
    @Accessors(fluent = true) ContextUIDialogImpl dialog = new ContextUIDialogImpl();
    private final @Getter
    @Accessors(fluent = true) ContextUIProgressImpl progress = new ContextUIProgressImpl();
    private final @Getter
    @Accessors(fluent = true) ContextUIMediaImpl media = new ContextUIMediaImpl();

    private final @Getter
    @NotNull Map<String, Map<String, ItemsContextMenuAction>> itemsContextMenuActions = new ConcurrentHashMap<>();
    private final @NotNull Map<String, Object> refreshConsolePlugin = new ConcurrentHashMap<>();

    private WebAudioAudioSink webAudioSink;

    public void onContextCreated() {
        webAudioSink = context.getBean(WebAudioAudioSink.class);
        // run hourly script to drop not used dynamicUpdateRegisters
        context
                .bgp()
                .builder("drop-outdated-dynamicContext")
                .intervalWithDelay(Duration.ofHours(1))
                .execute(() ->
                        this.dynamicUpdateRegisters.values().removeIf(v -> TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - v.timeout) > 1));

        context.bgp().builder("send-ui-updates").interval(Duration.ofSeconds(1)).execute(() -> {
            for (Iterator<SendUpdateContext> iterator = sendToUIMap.values().iterator(); iterator.hasNext(); ) {
                SendUpdateContext context = iterator.next();

                try {
                    sendDynamicUpdateSupplied(new DynamicUpdateRequest(context.dynamicUpdateID(), null), context.handler::get);
                } catch (Exception ex) {
                    log.warn("Unable to send dynamic update for: {}. {}", context.dynamicUpdateID, CommonUtils.getErrorMessage(ex));
                }
                iterator.remove();
            }
            // update tree separately because mqtt updates much faster
            try {
                treeNodeLock.lock();
                for (Entry<String, TreeNode> entry : treeNodesSendToUIMap.entrySet()) {
                    try {
                        sendDynamicUpdateSupplied(new DynamicUpdateRequest(entry.getKey(), null),
                                () -> OBJECT_MAPPER.createObjectNode().putPOJO("value", entry.getValue()));
                    } catch (Exception ex) {
                        log.warn("Unable to send dynamic update for: {}. {}", entry.getKey(), CommonUtils.getErrorMessage(ex));
                    }
                }
                treeNodesSendToUIMap.clear();
            } finally {
                treeNodeLock.unlock();
            }

            for (Iterator<Entry<String, Object>> iterator = refreshConsolePlugin.entrySet().iterator(); iterator.hasNext(); ) {
                Entry<String, Object> entry = iterator.next();
                ConsolePlugin<?> plugin = consolePluginsMap.get(entry.getKey());
                if (plugin != null) {
                    Object value = entry.getValue() == EMPTY ? null : entry.getValue();
                    sendGlobal(GlobalSendType.redrawConsole, entry.getKey(), value, null, null);
                }
                iterator.remove();
            }
        });
    }

    public void registerForUpdates(DynamicUpdateRequest request) {
        DynamicUpdateContext duc = dynamicUpdateRegisters.get(request);
        if (duc == null) {
            dynamicUpdateRegisters.put(request, new DynamicUpdateContext());
        } else {
            duc.timeout = System.currentTimeMillis(); // refresh timer
            duc.registerCounter.incrementAndGet();
        }
        context.event().addEventListener(request.getDynamicUpdateId(), value ->
                sendDynamicUpdateSupplied(request, () -> value));
    }

    public void unRegisterForUpdates(DynamicUpdateRequest request) {
        DynamicUpdateContext context = dynamicUpdateRegisters.get(request);
        if (context != null && context.registerCounter.decrementAndGet() == 0) {
            dynamicUpdateRegisters.remove(request);
        }
    }

    public void sendDynamicUpdateImpl(@NotNull String dynamicUpdateId, @Nullable String entityId, @Nullable Object value) {
        if (value != null) {
            sendDynamicUpdateSupplied(new DynamicUpdateRequest(dynamicUpdateId, entityId), () -> value);
        }
    }

    public void sendDynamicUpdateSupplied(@NotNull DynamicUpdateRequest request, @NotNull Supplier<Object> supplier) {
        DynamicUpdateContext context = dynamicUpdateRegisters.get(request);
        if (context != null) {
            Object value = supplier.get();
            if (value != null) {
                if (System.currentTimeMillis() - context.timeout > 60000) {
                    dynamicUpdateRegisters.remove(request);
                } else {
                    sendGlobal(GlobalSendType.dynamicUpdate, null, value, null, OBJECT_MAPPER.createObjectNode().putPOJO("dynamicRequest", request));
                }
            }
        }
    }

    public void sendAudio(String url) {
        sendGlobal(GlobalSendType.audio, String.valueOf(url.hashCode()), url, null, null);
    }

    @Override
    public @NotNull UIInputBuilder inputBuilder() {
        return new UIInputBuilderImpl(context);
    }

    @Override
    public void removeItem(@NotNull BaseEntity entity) {
        this.sendToUIMap.put(entity.getEntityID(), new SendUpdateContext(
                buildEntityDynamicUpdateId(entity), () ->
                OBJECT_MAPPER.createObjectNode()
                        .put("type", "remove")
                        .put("entityID", entity.getEntityID())
                        .putPOJO("entity", entity)));
    }

    @Override
    public void updateItem(@NotNull BaseEntity entity) {
        updateItem(entity, false);
    }

    @Override
    public void updateItem(
            @NotNull BaseEntityIdentifier entity,
            @NotNull String updateField,
            @Nullable Object value) {
        if (isUpdateNotRegistered(entity)) {
            return;
        }
        this.sendToUIMap.put(entity.getEntityID() + updateField, new SendUpdateContext(
                buildEntityDynamicUpdateId(entity), () ->
                OBJECT_MAPPER.createObjectNode()
                        .put("type", "add")
                        .put("entityID", entity.getEntityID())
                        .put("updateField", updateField)
                        .putPOJO("value", value)));
    }

    @Override
    public void updateInnerSetItem(
            @NotNull BaseEntityIdentifier parentEntity,
            @NotNull String parentFieldName,
            @NotNull String innerEntityID,
            @NotNull String updateField,
            @NotNull Object value) {

        if (isUpdateNotRegistered(parentEntity)) {
            return;
        }
        this.sendToUIMap.put(parentEntity.getEntityID() + parentFieldName + innerEntityID + updateField, new SendUpdateContext(
                buildEntityDynamicUpdateId(parentEntity), () ->
                OBJECT_MAPPER
                        .createObjectNode()
                        .put("type", "add")
                        .put("entityID", parentEntity.getEntityID())
                        .put("updateField", updateField)
                        .put("parentField", parentFieldName)
                        .putPOJO("value", value)));
    }

    @Override
    public void sendDynamicUpdate(@NotNull String dynamicUpdateID, @NotNull Object value) {
        if (dynamicUpdateID.startsWith("tree-")) {
            try {
                treeNodeLock.lock();
                TreeNode node = treeNodesSendToUIMap.get(dynamicUpdateID);
                if (node != null) {
                    TreeNode update = (TreeNode) value;
                    node.merge(update);
                } else {
                    treeNodesSendToUIMap.put(dynamicUpdateID, (TreeNode) value);
                }
            } finally {
                treeNodeLock.unlock();
            }
            return;
        }
        this.sendToUIMap.put(dynamicUpdateID, new SendUpdateContext(dynamicUpdateID, () ->
                OBJECT_MAPPER.createObjectNode().putPOJO("value", value)));
    }

    public static ConsolePlugin<?> getPlugin(String tab) {
        ConsolePlugin<?> consolePlugin = consolePluginsMap.get(tab);
        if (consolePlugin == null) {
            consolePlugin = consoleRemovablePluginsMap.get(tab);
        }
        return consolePlugin;
    }

    @Override
    public void addItemContextMenu(@NotNull String entityID, @NotNull String key, @NotNull Consumer<UIInputBuilder> builder) {
        context.db().getEntityRequire(entityID);
        UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
        builder.accept(uiInputBuilder);
        itemsContextMenuActions.computeIfAbsent(entityID, s -> new HashMap<>())
                .put(key, new ItemsContextMenuAction(uiInputBuilder, uiInputBuilder.buildAll()));
    }

    public void addHeaderMenuButton(String name, Icon icon, @NotNull Class<? extends BaseEntity> page) {
        headerMenuButtons.put(name, new HeaderButtonSelection(name, icon, getPageName(page)));
    }

    private boolean isUpdateNotRegistered(@NotNull BaseEntityIdentifier parentEntity) {
        return !this.dynamicUpdateRegisters.containsKey(new DynamicUpdateRequest("entity-type-" + parentEntity.getDynamicUpdateType()));
    }

    public void updateItem(@NotNull BaseEntity entity, boolean ignoreExtra) {
        if (isUpdateNotRegistered(entity)) {
            return;
        }
        this.sendToUIMap.put(entity.getEntityID(), new SendUpdateContext(
                buildEntityDynamicUpdateId(entity), () -> {
            ObjectNode metadata = OBJECT_MAPPER.createObjectNode()
                    .put("type", "add")
                    .put("entityID", entity.getEntityID())
                    .putPOJO("entity", entity);

            if (!ignoreExtra) {
                // insert context actions. maybe it's changed
                if (entity instanceof HasDynamicContextMenuActions da) {
                    UIInputBuilder uiInputBuilder = inputBuilder();
                    da.assembleActions(uiInputBuilder);
                    metadata.putPOJO("actions", uiInputBuilder.buildAll());
                }
            }
            return metadata;
        }));
    }

    @Override
    public void sendRawData(@NotNull String destination, @NotNull String value) {
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, value);
    }

    @Override
    public void sendRawData(@NotNull String destination, @NotNull ObjectNode param) {
        messagingTemplate.convertAndSend(WebSocketConfig.DESTINATION_PREFIX + destination, param);
    }

    @Override
    public HeaderButtonBuilder headerButtonBuilder(@NotNull String entityID) {
        return new HeaderButtonBuilder() {
            private final HeaderButtonNotification builder = new HeaderButtonNotification(entityID);

            @Override
            public @NotNull HeaderButtonBuilder title(@Nullable String title) {
                builder.setTitle(title);
                return this;
            }

            @Override
            public @NotNull HeaderButtonBuilder icon(@NotNull Icon icon) {
                builder.setIcon(icon.getIcon()).setIconColor(icon.getColor());
                return this;
            }

            @Override
            public @NotNull HeaderButtonBuilder border(int width, String color) {
                builder.setBorderWidth(width);
                builder.setBorderColor(color);
                return this;
            }

            @Override
            public @NotNull HeaderButtonBuilder duration(int duration) {
                builder.setDuration(duration);
                return this;
            }

            @Override
            public @NotNull HeaderButtonBuilder availableForPage(@NotNull Class<? extends BaseEntity> page) {
                if (!page.isAnnotationPresent(UISidebarMenu.class)) {
                    throw new IllegalArgumentException(
                            "Trying add header button to page without annotation UISidebarMenu");
                }
                builder.setPage(getPageName(page));
                return this;
            }

            @Override
            public @NotNull HeaderButtonBuilder clickAction(@NotNull Class<? extends SettingPluginButton> clickAction) {
                builder.setClickAction(
                        () -> {
                            context.setting().setValue(clickAction, null);
                            return null;
                        });
                return this;
            }

            @Override
            public @NotNull HeaderButtonBuilder clickAction(@NotNull Supplier<ActionResponseModel> clickAction) {
                builder.setClickAction(clickAction);
                return this;
            }

            @NotNull
            @Override
            public HeaderButtonBuilder attachToHeaderMenu(@NotNull String name) {
                if (!headerMenuButtons.containsKey(name)) {
                    throw new IllegalArgumentException("Unable to find header menu button: " + name);
                }
                builder.setAttachToHeaderMenu(name);
                return this;
            }

            @Override
            public void build() {
                HeaderButtonNotification existedModel = headerButtonNotifications.get(entityID);
                // preserve confirmations
                if (existedModel != null) {
                    builder.getDialogs().addAll(existedModel.getDialogs());
                }
                builder.setHandleActionID(entityID);
                headerButtonNotifications.put(entityID, builder);
                sendHeaderButtonToUI(builder, null);
            }
        };
    }

    private static String getPageName(@NotNull Class<? extends BaseEntity> page) {
        return defaultIfEmpty(
                page.getDeclaredAnnotation(UISidebarMenu.class).overridePath(),
                page.getSimpleName());
    }

    @Override
    public void removeHeaderButton(
            @NotNull String entityID, @Nullable String icon, boolean forceRemove) {
        HeaderButtonNotification notification = headerButtonNotifications.get(entityID);
        if (notification != null) {
            if (notification.getDialogs().isEmpty()) {
                headerButtonNotifications.remove(entityID);
            } else {
                notification.setIcon(icon == null ? notification.getIcon() : icon);
            }
            sendHeaderButtonToUI(
                    notification,
                    jsonObject -> jsonObject.put("action", forceRemove ? "forceRemove" : "remove"));
        }
    }

    @Override
    public void sendJsonMessage(
            @Nullable String title, @NotNull Object json, @Nullable FlowMap messageParam) {
        title = title == null ? null : Lang.getServerMessage(title, messageParam);
        sendGlobal(GlobalSendType.json, null, json, title, null);
    }

    public NotificationResponse getNotifications() {
        long time = System.currentTimeMillis();
        headerButtonNotifications.entrySet().removeIf(
                item -> {
                    HeaderButtonNotification json = item.getValue();
                    return json.getDuration() != null && time - item.getValue().getCreationTime().getTime() > json.getDuration() * 1000;
                });

        NotificationResponse notificationResponse = new NotificationResponse();
        notificationResponse.headerMenuButtons = headerMenuButtons.values();

        notificationResponse.dialogs = dialogRequest.values();
        notificationResponse.frameDialogs = frameRequest.values();
        UserEntity user = context.getUser();
        notificationResponse.notifications = blockNotifications.values();
        if (user != null) {
            for (NotificationBlock notification : notificationResponse.notifications) {
                if (notification.getFireOnFetchHandler() != null) {
                    notification.getFireOnFetchHandler().run();
                }
            }
            notificationResponse.notifications = blockNotifications.values().stream().filter(block ->
                    block.getEmail() == null || block.getEmail().equals(user.getEmail())).collect(Collectors.toList());
        }
        notificationResponse.headerButtonNotifications = headerButtonNotifications.values();
        notificationResponse.progress = progressMap.values();

        return notificationResponse;
    }

    public void handleDialog(String entityID, DialogResponseType dialogResponseType, String pressedButton, ObjectNode params) {
        DialogModel model = dialogRequest.remove(entityID);
        if (model != null) {
            model.getActionHandler().handle(dialogResponseType, pressedButton, params);
            if (dialogResponseType != DialogResponseType.Timeout && model.getMaxTimeoutInSec() > 0) {
                context.bgp().cancelThread(entityID + "dialog-timeout");
            }

            for (HeaderButtonNotification notificationModel : headerButtonNotifications.values()) {
                if (notificationModel.getDialogs().remove(model) && notificationModel.getDialogs().isEmpty()) {
                    this.removeHeaderButton(notificationModel.getEntityID()); // request to remove header button if no
                    // confirmation exists
                }
            }
        }
    }

    public ActionResponseModel handleNotificationAction(String entityID, String actionEntityID, JSONObject params) throws Exception {
        HeaderButtonNotification headerButtonNotification = headerButtonNotifications.get(entityID);
        if (headerButtonNotification != null) {
            return headerButtonNotification.getClickAction().get();
        }
        return handleNotificationBlockAction(entityID, actionEntityID, params);
    }

    public void handleResponse(@Nullable ActionResponseModel response) {
        if (response == null) {
            return;
        }
        switch (response.getResponseAction()) {
            case info -> toastr.info(String.valueOf(response.getValue()));
            case error -> toastr.error(String.valueOf(response.getValue()));
            case warning -> toastr.warn(String.valueOf(response.getValue()));
            case success -> toastr.success(String.valueOf(response.getValue()));
            case files -> throw new NotImplementedException(); // not implemented yet
        }
    }

    void sendGlobal(@NotNull GlobalSendType type, @Nullable String entityID, @Nullable Object value, @Nullable String title, @Nullable ObjectNode objectNode) {
        if (objectNode == null) {
            objectNode = OBJECT_MAPPER.createObjectNode();
        }
        objectNode.put("entityID", entityID).put("type", type.name());
        if (value != null) {
            objectNode.putPOJO("value", value);
        }
        if (title != null) {
            objectNode.put("title", title);
        }

        sendRawData("-global", objectNode);
    }

    private void sendHeaderButtonToUI(HeaderButtonNotification notification, Consumer<ObjectNode> additionalSupplier) {
        ObjectNode jsonNode = OBJECT_MAPPER.valueToTree(notification);
        if (additionalSupplier != null) {
            additionalSupplier.accept(jsonNode);
        }
        sendGlobal(GlobalSendType.headerButton, notification.getEntityID(), null, notification.getTitle(), jsonNode);
    }

    private ActionResponseModel handleNotificationBlockAction(String entityID, String actionEntityID, JSONObject params) throws Exception {
        NotificationBlock notificationBlock = blockNotifications.get(entityID);
        if (notificationBlock != null) {
            if ("UPDATE".equals(actionEntityID)) {
                return fireUpdatePackageAction(entityID, params, notificationBlock);
            }
            Info info = actionEntityID == null ? null :
                    notificationBlock.getInfoItems().stream()
                            .filter(i -> Objects.equals(actionEntityID, i.getKey()))
                            .findAny().orElse(null);
            if (info != null) {
                return info.getHandler().handleAction(context, null);
            }
            UIActionHandler actionHandler = findNotificationAction(actionEntityID, notificationBlock);
            if (actionHandler != null) {
                return actionHandler.handleAction(context, params);
            }
        }
        throw new IllegalArgumentException("Unable to find header notification: <" + entityID + ">");
    }

    private ActionResponseModel fireUpdatePackageAction(String entityID, JSONObject params, NotificationBlock notificationBlock) {
        if (!params.has("version")) {
            throw new IllegalArgumentException("Version must be specified for update: " + entityID);
        }
        notificationBlock.setUpdating(true);
        context.bgp()
                .runWithProgress("update-" + entityID)
                .execute(progressBar -> {
                    val handler = notificationBlock.getUpdateHandler();
                    if (handler == null) {
                        throw new IllegalStateException("Unable to fire update action without handler");
                    }
                    try {
                        handleResponse(handler.apply(progressBar, params.getString("version")));
                    } finally {
                        if (notificationBlock.getRefreshBlockBuilder() != null) {
                            notificationBlock.getRefreshBlockBuilder().accept(
                                    new NotificationBlockBuilderImpl(notificationBlock, context));
                            sendGlobal(GlobalSendType.notification, entityID, notificationBlock, null, null);
                        }
                    }
                });
        return ActionResponseModel.fired();
    }

    private UIActionHandler findNotificationAction(String actionEntityID, NotificationBlock notificationBlock) {
        UIActionHandler action = null;
        if (notificationBlock.getContextMenuActions() != null) {
            action = notificationBlock.getContextMenuActions()
                    .stream()
                    .filter(ca -> ca.getEntityID().equals(actionEntityID) && ca instanceof UIInputEntityActionHandler)
                    .findAny()
                    .map(f -> ((UIInputEntityActionHandler) f).getActionHandler())
                    .orElse(null);
        }
        if (action == null && notificationBlock.getActions() != null) {
            for (UIInputEntity inputEntity : notificationBlock.getActions()) {
                action = inputEntity.findAction(actionEntityID);
                if (action != null) {
                    break;
                }
            }
        }
        return action;
    }

    enum GlobalSendType {
        headerMenuButton,
        popup,
        json,
        setting,
        progress,
        bell,
        notification,
        headerButton,
        openConsole,
        frame,
        redrawConsole,
        reload,
        addItem,
        dialog,
        // send audio to play on ui
        audio,
        // next generation
        dynamicUpdate
    }

    @Getter
    public static class NotificationResponse {

        private Collection<HeaderButtonSelection> headerMenuButtons;
        private Collection<DialogModel> frameDialogs;
        private Collection<HeaderButtonNotification> headerButtonNotifications;
        private Collection<ProgressNotification> progress;
        private Collection<DialogModel> dialogs;
        private Collection<NotificationBlock> notifications;
    }

    private static class DynamicUpdateContext {

        private final AtomicInteger registerCounter = new AtomicInteger(0);
        private long timeout = System.currentTimeMillis();
    }

    private record NotificationBlockBuilderImpl(NotificationBlock notificationBlock,
                                                ContextImpl context) implements NotificationBlockBuilder {

        @Override
        public @NotNull NotificationBlockBuilder linkToEntity(@NotNull BaseEntity entity) {
            notificationBlock.setLink(entity.getEntityID());
            notificationBlock.setLinkType(UIFieldUtils.getClassEntityNavLink(notificationBlock.getName(), entity.getClass()));
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder visibleForUser(@Nullable String email) {
            notificationBlock.setEmail(email);
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder blockActionBuilder(Consumer<UIInputBuilder> builder) {
            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
            builder.accept(uiInputBuilder);
            notificationBlock.getKeyValueActions().put("general", uiInputBuilder.buildAll());
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder addFlexAction(@NotNull String name, @NotNull Consumer<UIFlexLayoutBuilder> builder) {
            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
            uiInputBuilder.addFlex(name, builder);
            notificationBlock.getKeyValueActions().put(name, uiInputBuilder.buildAll());
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder contextMenuActionBuilder(Consumer<UIInputBuilder> builder) {
            UIInputBuilder uiInputBuilder = context.ui().inputBuilder();
            builder.accept(uiInputBuilder);
            if (notificationBlock.getContextMenuActions() == null) {
                notificationBlock.setContextMenuActions(new ArrayList<>());
            }
            notificationBlock.getContextMenuActions().addAll(uiInputBuilder.buildAll());
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder setNameColor(@Nullable String color) {
            notificationBlock.setNameColor(color);
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder setStatus(Status status) {
            notificationBlock.setStatus(status);
            notificationBlock.setStatusColor(status == null ? null : status.getColor());
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder fireOnFetch(@NotNull Runnable handler) {
            notificationBlock.setFireOnFetchHandler(handler);
            handler.run();
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder setUpdating(boolean value) {
            notificationBlock.setUpdating(value);
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder setBorderColor(@Nullable String color) {
            notificationBlock.setBorderColor(color);
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder setVersion(@Nullable String version) {
            notificationBlock.setVersion(version);
            return this;
        }

        @Override
        public @NotNull NotificationBlockBuilder setUpdatable(@NotNull ThrowingBiFunction<ProgressBar, String, ActionResponseModel, Exception> updateHandler,
                                                              @NotNull List<OptionModel> versions) {
            notificationBlock.setUpdatable(updateHandler, versions);
            return this;
        }

        @Override
        public @NotNull NotificationInfoLineBuilder addInfo(@NotNull String key, @Nullable Icon icon, @NotNull String info) {
            return notificationBlock.addInfoLine(key, info, icon);
        }

        @Override
        public @NotNull NotificationBlockBuilder addEntityInfo(@NotNull BaseEntity entity) {
            Icon icon = entity.getEntityIcon();
            if (icon == null && entity instanceof BaseFileSystemEntity fs) {
                icon = fs.getFileSystemIcon();
            }
            Info info = notificationBlock.addInfoLine(entity.getEntityID(), entity.getTitle(), icon);
            info.setAsLink(entity);

            if (entity instanceof HasStatusAndMsg sm) {
                info.setRightText(sm.getStatus());
                setStatus(sm.getStatus());
            }
            if (entity instanceof HasFirmwareVersion ve) {
                if (info.getRightText() == null) {
                    info.setRightText(ve.getFirmwareVersion(), null, null);
                } else {
                    info.updateTextInfo(entity.getTitle() + " [" + ve.getFirmwareVersion() + "]");
                }
            }
            return this;
        }

        @Override
        public boolean removeInfo(@NotNull String key) {
            boolean removed = notificationBlock.remove(key);
            if (!removed) {
                removed = notificationBlock.getKeyValueActions().remove(key) != null;
            }
            if (!removed) {
                removed = notificationBlock.getInfoItems().removeIf(info -> info.getInfo().equals(key));
            }
            return removed;
        }
    }

    private static String buildEntityDynamicUpdateId(@NotNull BaseEntityIdentifier parentEntity) {
        return "entity-type-%s".formatted(parentEntity instanceof WidgetEntity ? "widget" : parentEntity.getType());
    }

    private record SendUpdateContext(@NotNull String dynamicUpdateID, @NotNull Supplier<ObjectNode> handler) {

    }

    public class ContextUIToastrImpl implements ContextUIToastr {

        @Override
        public void sendMessage(@Nullable String title, @Nullable String message, @Nullable NotificationLevel level, @Nullable Integer timeout) {
            ObjectNode param = OBJECT_MAPPER.createObjectNode();
            if (level != null) {
                param.put("level", level.name());
            }
            if (timeout != null && timeout > 3) {
                param.put("timeout", timeout);
            }
            sendGlobal(GlobalSendType.popup, null, message, title, param);
        }
    }

    public class ContextUINotificationImpl implements ContextUINotification {

        @Override
        public void removeEmptyBlock(@NotNull String entityID) {
            NotificationBlock notificationBlock = blockNotifications.get(entityID);
            if (notificationBlock != null && !notificationBlock.getInfoItems().isEmpty()) {
                blockNotifications.remove(entityID);
            }
        }

        @Override
        public void addBlock(@NotNull String entityID, @NotNull String name, @Nullable Icon icon, @Nullable Consumer<NotificationBlockBuilder> builder) {
            val notificationBlock = new NotificationBlock(entityID, name, icon);
            if (builder != null) {
                builder.accept(new NotificationBlockBuilderImpl(notificationBlock, context));
            }
            if (notificationBlock.getUpdateHandler() != null) {
                notificationBlock.setRefreshBlockBuilder(builder);
            }
            blockNotifications.put(entityID, notificationBlock);
            sendGlobal(GlobalSendType.notification, entityID, notificationBlock, null, null);
        }

        @Override
        public void updateBlock(@NotNull String entityID, @NotNull Consumer<NotificationBlockBuilder> builder) {
            NotificationBlock notificationBlock = blockNotifications.get(entityID);
            if (notificationBlock == null) {
                throw new IllegalArgumentException("Unable to find notification block: " + entityID);
            }
            builder.accept(new NotificationBlockBuilderImpl(notificationBlock, context));
            sendGlobal(GlobalSendType.notification, entityID, notificationBlock, null, null);
        }

        @Override
        public boolean isHasBlock(@NotNull String key) {
            return blockNotifications.containsKey(key);
        }

        @Override
        public void removeBlock(@NotNull String entityID) {
            if (blockNotifications.remove(entityID) != null) {
                sendGlobal(GlobalSendType.notification, entityID, null, null, OBJECT_MAPPER.createObjectNode().put("action", "remove"));
            }
        }
    }

    public class ContextUIConsoleImpl implements ContextUIConsole {

        @Override
        public void registerPluginName(@NotNull String name, @Nullable String resource) {
            customConsolePluginNames.put(name, trimToEmpty(resource));
        }

        @Override
        public <T extends ConsolePlugin> void registerPlugin(@NotNull String name, @NotNull T plugin) {
            consolePluginsMap.put(name, plugin);
        }

        public <T extends ConsolePlugin> void registerPlugin(@NotNull String name, @NotNull T plugin, boolean removable) {
            if (removable) {
                consoleRemovablePluginsMap.put(name, plugin);
            } else {
                consolePluginsMap.put(name, plugin);
            }
        }

        @Override
        public <T extends ConsolePlugin> @Nullable T getRegisteredPlugin(@NotNull String name) {
            if (consolePluginsMap.containsKey(name)) {
                return (T) consolePluginsMap.get(name);
            }
            return (T) consoleRemovablePluginsMap.get(name);
        }

        @Override
        public boolean unRegisterPlugin(@NotNull String name) {
            if (consolePluginsMap.remove(name) != null) {
                consolePluginsMap.remove(name);
                return true;
            }
            if (consoleRemovablePluginsMap.remove(name) != null) {
                consoleRemovablePluginsMap.remove(name);
                return true;
            }
            return false;
        }

        @Override
        public <T extends ConsolePlugin<?>> void openConsole(@NotNull String name) {
            if (consolePluginsMap.containsKey(name)) {
                throw new ServerException("Console plugin: " + name + " not registered");
            }
            sendGlobal(GlobalSendType.openConsole, name, null, null, null);
        }

        @Override
        public void refreshPluginContent(@NotNull String name) {
            refreshConsolePlugin.put(name, EMPTY);
        }

        @Override
        public void refreshPluginContent(@NotNull String name, Object value) {
            refreshConsolePlugin.put(name, value);
        }
    }

    public class ContextUIDialogImpl implements ContextUIDialog {

        @Override
        public void removeDialogRequest(@NotNull String uuid) {
            if (dialogRequest.remove(uuid) != null) {
                sendGlobal(GlobalSendType.dialog, uuid, null, null, null);
            }
        }

        @Override
        @SneakyThrows
        public void sendFrame(String host, String title, boolean proxy) {
            if (!frameRequest.containsKey(host)) {
                try {
                    if (!host.startsWith("http")) {
                        host = "http://" + host;
                    }
                    URL url = new URL(host);
                    ContextNetwork.ping(url.getHost(), url.getPort() == -1 ? 80 : url.getPort());
                    if (proxy) {
                        host = context.service().registerUrlProxy(String.valueOf(Math.abs(host.hashCode())), host, builder -> {
                        });
                    }
                    DialogModel dialogModel = new DialogModel(host, title, null);
                    dialogModel.frame();
                    frameRequest.put(host, dialogModel);
                    sendGlobal(GlobalSendType.frame, host, null, title, null);
                } catch (Exception ignore) {
                }
            }
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
                    context
                            .bgp()
                            .builder(key + "-dialog-timeout")
                            .delay(Duration.ofSeconds(dialogModel.getMaxTimeoutInSec()))
                            .execute(() -> handleDialog(key, DialogResponseType.Timeout, null, null));
                }

                sendGlobal(GlobalSendType.dialog, key, dialogModel, null, null);

                return dialogModel;
            });
            sendGlobal(GlobalSendType.dialog, dialogModel.getEntityID(), dialogModel, null, null);
        }

        @Override
        public void reloadWindow(@NotNull String reason, int timeoutToReload) {
            if (timeoutToReload < 5) {
                throw new IllegalArgumentException("TimeoutToReload must greater than 4 seconds");
            }
            if (timeoutToReload > 60) {
                throw new IllegalArgumentException("TimeoutToReload must be lowe than 61 seconds");
            }
            sendGlobal(GlobalSendType.reload, reason, timeoutToReload, null, null);
        }
    }

    public class ContextUIProgressImpl implements ContextUIProgress {

        private final Map<String, Pair<ProgressBar, Runnable>> simpleProgressBars = new ConcurrentHashMap<>();

        @Override
        public ProgressBar createProgressBar(@NotNull String key, boolean dummy, @Nullable Runnable cancelHandler) {
            ProgressBar progressBar = new ProgressBar() {
                @Override
                public void progress(double progress, @Nullable String message, boolean error) {
                    context().ui().progress().update(key, progress, message, cancelHandler != null);
                }

                @Override
                public boolean isCancelled() {
                    return cancelHandler != null && !simpleProgressBars.containsKey(key);
                }
            };
            if (cancelHandler != null) {
                simpleProgressBars.put(key, Pair.of(progressBar, cancelHandler));
            }
            return progressBar;
        }

        @Override
        public void update(@NotNull String key, double progress, @Nullable String message, boolean cancellable) {
            if (progress >= 100) {
                progressMap.remove(key);
                cancel(key);
            } else {
                progressMap.put(key, new ProgressNotification(key, progress, message, cancellable));
            }
            sendGlobal(GlobalSendType.progress, key, Math.round(progress), message,
                    cancellable ? OBJECT_MAPPER.createObjectNode().put("cancellable", true) : null);
        }

        public void cancel(String name) {
            Pair<ProgressBar, Runnable> removed = simpleProgressBars.remove(name);
            if (removed != null) {
                removed.getValue().run();
            }
        }
    }

    public class ContextUIMediaImpl implements ContextUIMedia {

        @Override
        @SneakyThrows
        public void playWebAudio(InputStream stream, AudioFormat format, Integer from, Integer to) {
            ByteArrayAudioStream audioStream = new ByteArrayAudioStream(IOUtils.toByteArray(stream), format);
            webAudioSink.play(audioStream, null, from, to);
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ItemsContextMenuAction {

        private UIInputBuilder uiInputBuilder;
        private Collection<UIInputEntity> actions;
    }
}
