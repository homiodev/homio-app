package org.touchhome.bundle.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.setting.SendBroadcastSetting;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.NotificationMessageEntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.scratch.*;
import org.touchhome.bundle.api.setting.BundleSettingPluginButton;
import org.touchhome.bundle.api.util.NotificationType;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Log4j2
@Getter
@Component
public class Scratch3UIBlocks extends Scratch3ExtensionBlocks {

    private final MenuBlock.StaticMenuBlock<PopupType> popupType;

    private final Scratch3Block popupCommand;
    private final Scratch3Block headerCommand;
    private final Scratch3Block topHeaderCommand;
    private final Scratch3Block topImageHeaderCommand;
    private final Scratch3EventsBlocks scratch3EventsBlocks;

    public Scratch3UIBlocks(EntityContext entityContext, Scratch3EventsBlocks scratch3EventsBlocks) {
        super("#7C4B96", entityContext, null, "ui");
        this.scratch3EventsBlocks = scratch3EventsBlocks;

        this.popupType = MenuBlock.ofStatic("popupType", PopupType.class, PopupType.INFO);

        this.popupCommand = Scratch3Block.ofHandler(10, "popup", BlockType.command,
                "Popup [MSG] [TYPE]", this::showPopupHandler);
        this.popupCommand.addArgument("MSG", "message");
        this.popupCommand.addArgument("TYPE", this.popupType);

        this.headerCommand = Scratch3Block.ofHandler(20, "header", BlockType.command,
                "Notification [NAME]/[MSG] [TYPE]", this::headerHandler);
        this.headerCommand.addArgument("NAME", "name");
        this.headerCommand.addArgument("MSG", "message");
        this.headerCommand.addArgument("TYPE", this.popupType);

        this.topHeaderCommand = Scratch3Block.ofHandler(40, "top_header", BlockType.command,
                "Top header [MSG]/[COLOR] for [DURATION] sec. | Click event [BROADCAST]", this::topHeaderHandler);
        this.topHeaderCommand.addArgument("MSG", "message");
        this.topHeaderCommand.addArgument("COLOR", ArgumentType.color, "#1F4131");
        this.topHeaderCommand.addArgument("DURATION", 60);
        this.topHeaderCommand.addArgument("BROADCAST", ArgumentType.broadcast);

        this.topImageHeaderCommand = Scratch3Block.ofHandler(50, "top_img_header", BlockType.command,
                "Top header [MSG][COLOR]/[ICON] | Click event [BROADCAST]", this::topImageHeaderHandler);
        this.topImageHeaderCommand.addArgument("MSG", "message");
        this.topImageHeaderCommand.addArgument("COLOR", ArgumentType.color, "#1F4131");
        this.topImageHeaderCommand.addArgument("ICON", ArgumentType.icon, "cog");
        this.topImageHeaderCommand.addArgument("BROADCAST", ArgumentType.broadcast);

        this.postConstruct();

        entityContext.listenSettingValue(SendBroadcastSetting.class, "listen-ui-header-click", new Consumer<JSONObject>() {
            @Override
            public void accept(JSONObject json) {
                String broadcastID = json.getString("name");
                scratch3EventsBlocks.fireBroadcastEvent(broadcastID);
            }
        });
    }

    private void topImageHeaderHandler(WorkspaceBlock workspaceBlock) {
        createHeaderEntity(workspaceBlock, false);
    }

    private void topHeaderHandler(WorkspaceBlock workspaceBlock) {
        createHeaderEntity(workspaceBlock, true);
    }

    private void createHeaderEntity(WorkspaceBlock workspaceBlock, boolean isFetchDuration) {
        NotificationEntityJSON json = new NotificationEntityJSON(workspaceBlock.getId());
        json.setDescription(workspaceBlock.getInputString("MSG"));
        Class<? extends BundleSettingPluginButton> stopAction = null;
        String color = workspaceBlock.getInputString("COLOR");
        String broadcast = workspaceBlock.getInputString("BROADCAST");
        json.setName(broadcast);
        if (isFetchDuration) {
            entityContext.showAlwaysOnViewNotification(json, workspaceBlock.getInputInteger("DURATION"), color, SendBroadcastSetting.class);
        } else {
            entityContext.showAlwaysOnViewNotification(json, "fas fa-" + workspaceBlock.getInputString("ICON"), color, SendBroadcastSetting.class);
        }
        workspaceBlock.onRelease(() -> entityContext.hideAlwaysOnViewNotification(json));
    }

    private void headerHandler(WorkspaceBlock workspaceBlock) {
        NotificationEntityJSON json = new NotificationEntityJSON(workspaceBlock.getId());
        json.setNotificationType(workspaceBlock.getMenuValue("TYPE", this.popupType).NotificationType);
        json.setName(workspaceBlock.getInputString("NAME"));
        json.setDescription(workspaceBlock.getInputString("MSG"));
        entityContext.addHeaderNotification(json);
        workspaceBlock.onRelease(() -> entityContext.removeHeaderNotification(json));
    }

    private void showPopupHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.getMenuValue("TYPE", this.popupType).popupHandler
                .accept(entityContext, workspaceBlock.getInputString("MSG"));
    }

    @RequiredArgsConstructor
    private enum PopupType {
        INFO(NotificationMessageEntityContext::sendInfoMessage, org.touchhome.bundle.api.util.NotificationType.info),
        WARN(NotificationMessageEntityContext::sendWarnMessage, org.touchhome.bundle.api.util.NotificationType.warning),
        ERROR(NotificationMessageEntityContext::sendErrorMessage, org.touchhome.bundle.api.util.NotificationType.danger),
        SUCCESS(NotificationMessageEntityContext::sendSuccessMessage, org.touchhome.bundle.api.util.NotificationType.success);

        private final BiConsumer<EntityContext, String> popupHandler;
        private final NotificationType NotificationType;
    }
}
