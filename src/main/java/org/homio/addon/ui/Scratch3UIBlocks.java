package org.homio.addon.ui;

import java.util.function.BiConsumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextUI.HeaderButtonBuilder;
import org.homio.api.model.Icon;
import org.homio.api.util.NotificationLevel;
import org.homio.api.workspace.WorkspaceBlock;
import org.homio.api.workspace.scratch.ArgumentType;
import org.homio.api.workspace.scratch.MenuBlock;
import org.homio.api.workspace.scratch.Scratch3ExtensionBlocks;
import org.homio.app.setting.SendBroadcastSetting;
import org.homio.app.workspace.WorkspaceService;
import org.homio.app.workspace.block.core.Scratch3EventsBlocks;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Log4j2
@Getter
@Component
public class Scratch3UIBlocks extends Scratch3ExtensionBlocks {

  private static final String COLOR = "#417505";
  private final MenuBlock.StaticMenuBlock<PopupType> popupType;

  private final Scratch3EventsBlocks scratch3EventsBlocks;

  public Scratch3UIBlocks(
      Context context,
      Scratch3EventsBlocks scratch3EventsBlocks,
      WorkspaceService workspaceService) {
    super("#7C4B96", context, null, "ui");
    this.scratch3EventsBlocks = scratch3EventsBlocks;

    this.popupType = menuStatic("popupType", PopupType.class, PopupType.INFO);

    blockCommand(10, "popup", "Popup [MSG] [TYPE]", this::showPopupHandler,
        block -> {
          block.addArgument("MSG", "message");
          block.addArgument("TYPE", this.popupType);
        });

    blockCommand(40, "top_header", "Top header [MSG]/[COLOR] for [DURATION] sec. | Click event [BROADCAST]", this::topHeaderHandler,
        block -> {
          block.addArgument("MSG", "message");
          block.addArgument("COLOR", ArgumentType.color, COLOR);
          block.addArgument("DURATION", 60);
          block.addArgument("BROADCAST", ArgumentType.broadcast);
        });

    blockCommand(50, "top_img_header", "Top header [MSG][COLOR]/[ICON] | Click event [BROADCAST]", this::topImageHeaderHandler,
        block -> {
          block.addArgument("MSG", "message");
          block.addArgument("COLOR", ArgumentType.color, COLOR);
        block.addArgument("ICON", ArgumentType.icon, "cog");
          block.addArgument("BROADCAST", ArgumentType.broadcast);
        });

      blockCommand(
              100,
              "push_notification",
              "Push notification [TITLE] / [MESSAGE]",
              this::pushNotification,
              block -> {
                  block.addArgument("TITLE", "Title");
                  block.addArgument("MESSAGE", "Message");
              });

    context.setting().listenValue(SendBroadcastSetting.class, "listen-ui-header-click",
            json -> {
              String workspaceEntityID = json.getString("entityID");
        WorkspaceBlock workspaceBlock = workspaceService.getWorkspaceBlockById(workspaceEntityID);
              if (workspaceBlock != null) {
                String broadcastID = workspaceBlock.getInputString("BROADCAST");
                scratch3EventsBlocks.fireBroadcastEvent(broadcastID);
              }
            });
  }

    private void pushNotification(@NotNull WorkspaceBlock workspaceBlock) {
      context.ui().notification().sendPushNotification(
              workspaceBlock.getInputString("TITLE"),
              workspaceBlock.getInputString("MESSAGE")
      );
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
          HeaderButtonBuilder headerButtonBuilder =
              context
                  .ui()
                  .headerButtonBuilder(workspaceBlock.getId())
                  .title(title)
                  .border(3, color)
                  .clickAction(SendBroadcastSetting.class);

          if (isFetchDuration) {
            headerButtonBuilder.duration(workspaceBlock.getInputInteger("DURATION")).build();
          } else {
            headerButtonBuilder
                .icon(new Icon("fas fa-" + workspaceBlock.getInputString("ICON")))
                .build();
          }
        },
        () -> context.ui().removeHeaderButton(workspaceBlock.getId()));
  }

  private void showPopupHandler(WorkspaceBlock workspaceBlock) {
    workspaceBlock
        .getMenuValue("TYPE", this.popupType)
        .popupHandler
        .accept(context, workspaceBlock.getInputString("MSG"));
  }

  @RequiredArgsConstructor
  private enum PopupType {
    INFO((context, msg) -> context.ui().toastr().info(msg), NotificationLevel.info),
    WARN((context, msg) -> context.ui().toastr().warn(msg), NotificationLevel.warning),
    ERROR((context, msg) -> context.ui().toastr().error(msg), NotificationLevel.error),
    SUCCESS((context, msg) -> context.ui().toastr().success(msg), NotificationLevel.success);

    private final BiConsumer<Context, String> popupHandler;
    private final NotificationLevel level;
  }
}
