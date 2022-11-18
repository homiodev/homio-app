package org.touchhome.bundle.ui;

import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.touchhome.app.setting.SendBroadcastSetting;
import org.touchhome.app.workspace.WorkspaceService;
import org.touchhome.app.workspace.block.core.Scratch3EventsBlocks;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.util.NotificationLevel;
import org.touchhome.bundle.api.workspace.WorkspaceBlock;
import org.touchhome.bundle.api.workspace.scratch.ArgumentType;
import org.touchhome.bundle.api.workspace.scratch.MenuBlock;
import org.touchhome.bundle.api.workspace.scratch.Scratch3ExtensionBlocks;

@Log4j2
@Getter
@Component
public class Scratch3UIBlocks extends Scratch3ExtensionBlocks {

  private static final String COLOR = "#417505";
  private final MenuBlock.StaticMenuBlock<PopupType> popupType;

  private final Scratch3EventsBlocks scratch3EventsBlocks;

  public Scratch3UIBlocks(EntityContext entityContext, Scratch3EventsBlocks scratch3EventsBlocks,
      WorkspaceService workspaceService) {
    super("#7C4B96", entityContext, null, "ui");
    this.scratch3EventsBlocks = scratch3EventsBlocks;

    this.popupType = menuStatic("popupType", PopupType.class, PopupType.INFO);

    blockCommand(10, "popup", "Popup [MSG] [TYPE]", this::showPopupHandler, block -> {
      block.addArgument("MSG", "message");
      block.addArgument("TYPE", this.popupType);
    });

    blockCommand(20, "header", "Bell [TYPE] notification [NAME]/[MSG]", this::headerHandler, block -> {
      block.addArgument("NAME", "name");
      block.addArgument("MSG", "message");
      block.addArgument("TYPE", this.popupType);
    });

    blockCommand(40, "top_header", "Top header [MSG]/[COLOR] for [DURATION] sec. | Click event [BROADCAST]", this::topHeaderHandler, block -> {
      block.addArgument("MSG", "message");
      block.addArgument("COLOR", ArgumentType.color, COLOR);
      block.addArgument("DURATION", 60);
      block.addArgument("BROADCAST", ArgumentType.broadcast);
    });

    blockCommand(50, "top_img_header", "Top header [MSG][COLOR]/[ICON] | Click event [BROADCAST]", this::topImageHeaderHandler, block -> {
      block.addArgument("MSG", "message");
      block.addArgument("COLOR", ArgumentType.color, COLOR);
      block.addArgument("ICON", ArgumentType.icon, "cog");
      block.addArgument("BROADCAST", ArgumentType.broadcast);
    });

    entityContext.setting().listenValue(SendBroadcastSetting.class, "listen-ui-header-click", json -> {
      String workspaceEntityID = json.getString("entityID");
      WorkspaceBlock workspaceBlock = workspaceService.getWorkspaceBlockById(workspaceEntityID);
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
    workspaceBlock.handleAndRelease(
        () -> {
          String title = workspaceBlock.getInputString("MSG");
          String color = workspaceBlock.getInputString("COLOR");
          // TODO: ????? String broadcast = workspaceBlock.getInputString("BROADCAST");
          if (isFetchDuration) {
            entityContext.ui().addHeaderButton(workspaceBlock.getId(), color, title, null, false, 3,
                workspaceBlock.getInputInteger("DURATION"), null, SendBroadcastSetting.class);
          } else {
            entityContext.ui().addHeaderButton(workspaceBlock.getId(), color, title,
                "fas fa-" + workspaceBlock.getInputString("ICON"), false, 3, null, null,
                SendBroadcastSetting.class);
          }
        },
        () -> entityContext.ui().removeHeaderButton(workspaceBlock.getId())
    );
  }

  private void headerHandler(WorkspaceBlock workspaceBlock) {
    workspaceBlock.handleAndRelease(
        () -> entityContext.ui().addBellNotification(workspaceBlock.getId(), workspaceBlock.getInputString("NAME"),
            workspaceBlock.getInputString("MSG"), workspaceBlock.getMenuValue("TYPE", this.popupType).level,
            null),
        () -> entityContext.ui().removeBellNotification(workspaceBlock.getId()));
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
