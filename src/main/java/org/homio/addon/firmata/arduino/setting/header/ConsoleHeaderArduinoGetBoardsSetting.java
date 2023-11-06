package org.homio.addon.firmata.arduino.setting.header;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.homio.addon.firmata.platform.BaseNoGui;
import org.homio.addon.firmata.platform.debug.TargetBoard;
import org.homio.addon.firmata.platform.debug.TargetPackage;
import org.homio.addon.firmata.platform.debug.TargetPlatform;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ConsoleHeaderArduinoGetBoardsSetting implements ConsoleHeaderSettingPlugin<String>,
    SettingPluginOptions<String> {

  @Override
  public Collection<OptionModel> getOptions(Context context, JSONObject params) {
    List<OptionModel> options = new ArrayList<>();
    // Cycle through all packages
    if (BaseNoGui.packages != null) {
      for (TargetPackage targetPackage : BaseNoGui.packages.values()) {
        // For every package cycle through all platform
        for (TargetPlatform targetPlatform : targetPackage.platforms()) {

          // Add a title for each platform
          String platformLabel = targetPlatform.getPreferences().get("name");
          if (platformLabel == null) {
            platformLabel = targetPackage.getId() + "-" + targetPlatform.getId();
          }

          // add an hint that this core lives in sketchbook
          if (targetPlatform.isInSketchbook()) {
            platformLabel += " (in sketchbook)";
          }

          OptionModel boardFamily = OptionModel.of(targetPackage.getId() + "~~~" + targetPlatform.getId(), platformLabel);

          for (TargetBoard board : targetPlatform.getBoards().values()) {
            if (board.getPreferences().get("hide") != null) {
              continue;
            }
            OptionModel boardType = OptionModel.of(board.getId(), board.getName());
            boardFamily.addChild(boardType);
          }
          if (boardFamily.hasChildren()) {
            options.add(boardFamily);
          }
        }
      }
    }
    return options;
  }

  @Override
  public @NotNull Class<String> getType() {
    return String.class;
  }

  @Override
  public Integer getMaxWidth() {
    return 150;
  }

  @Override
  public Icon getIcon() {
    return new Icon("fab fa-flipboard");
  }

  @Override
  public boolean isRequired() {
    return true;
  }
}
