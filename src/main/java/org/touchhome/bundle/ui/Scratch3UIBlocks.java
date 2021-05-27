package org.touchhome.bundle.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.setting.SendBroadcastSetting;
import org.touchhome.app.workspace.WorkspaceManager;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.NotificationLevel;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.*;

import java.util.function.BiConsumer;

@Log4j2
@Getter
@Component
public class Scratch3UIBlocks extends Scratch3ExtensionBlocks {

    private static final String COLOR = "#417505";
    private final MenuBlock.StaticMenuBlock<PopupType> popupType;

    private final Scratch3Block popupCommand;
    private final Scratch3Block headerCommand;
    private final Scratch3Block topHeaderCommand;
    private final Scratch3Block topImageHeaderCommand;
    private final Scratch3EventsBlocks scratch3EventsBlocks;

    public Scratch3UIBlocks(EntityContext entityContext, Scratch3EventsBlocks scratch3EventsBlocks, WorkspaceManager workspaceManager) {
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
        this.topHeaderCommand.addArgument("COLOR", ArgumentType.color, COLOR);
        this.topHeaderCommand.addArgument("DURATION", 60);
        this.topHeaderCommand.addArgument("BROADCAST", ArgumentType.broadcast);

        this.topImageHeaderCommand = Scratch3Block.ofHandler(50, "top_img_header", BlockType.command,
                "Top header [MSG][COLOR]/[ICON] | Click event [BROADCAST]", this::topImageHeaderHandler);
        this.topImageHeaderCommand.addArgument("MSG", "message");
        this.topImageHeaderCommand.addArgument("COLOR", ArgumentType.color, COLOR);
        this.topImageHeaderCommand.addArgument("ICON", ArgumentType.icon, "cog");
        this.topImageHeaderCommand.addArgument("BROADCAST", ArgumentType.broadcast);

        entityContext.setting().listenValue(SendBroadcastSetting.class, "listen-ui-header-click", json -> {
            String workspaceEntityID = json.getString("entityID");
            WorkspaceBlock workspaceBlock = workspaceManager.getWorkspaceBlockById(workspaceEntityID);
            if (workspaceBlock != null) {
                String broadcastID = workspaceBlock.getInputString("BROADCAST");
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
        String title = workspaceBlock.getInputString("MSG");
        String color = workspaceBlock.getInputString("COLOR");
        // TODO: ????? String broadcast = workspaceBlock.getInputString("BROADCAST");
        if (isFetchDuration) {
            entityContext.ui().addHeaderButton(workspaceBlock.getId(), color, title, null, false, true, workspaceBlock.getInputInteger("DURATION"), null, SendBroadcastSetting.class);
        } else {
            entityContext.ui().addHeaderButton(workspaceBlock.getId(), color, title, "fas fa-" + workspaceBlock.getInputString("ICON"), false, true, null, null, SendBroadcastSetting.class);
        }
        workspaceBlock.onRelease(() -> entityContext.ui().removeHeaderButton(workspaceBlock.getId()));
    }

    private void headerHandler(WorkspaceBlock workspaceBlock) {
        entityContext.ui().addBellNotification(workspaceBlock.getId(), workspaceBlock.getInputString("NAME"),
                workspaceBlock.getInputString("MSG"), workspaceBlock.getMenuValue("TYPE", this.popupType).level);
        workspaceBlock.onRelease(() -> entityContext.ui().removeBellNotification(workspaceBlock.getId()));
    }

    private void showPopupHandler(WorkspaceBlock workspaceBlock) {
        workspaceBlock.getMenuValue("TYPE", this.popupType).popupHandler
                .accept(entityContext, workspaceBlock.getInputString("MSG"));
    }

    @RequiredArgsConstructor
    private enum PopupType {
        INFO((context, msg) -> context.ui().sendInfoMessage(msg), NotificationLevel.info),
        WARN((context, msg) -> context.ui().sendWarningMessage(msg), NotificationLevel.warning),
        ERROR((context, msg) -> context.ui().sendErrorMessage(msg), NotificationLevel.error),
        SUCCESS((context, msg) -> context.ui().sendSuccessMessage(msg), NotificationLevel.success);

        private final BiConsumer<EntityContext, String> popupHandler;
        private final NotificationLevel level;
    }
}
