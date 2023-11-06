package org.homio.addon.firmata.arduino.setting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.homio.addon.firmata.arduino.ArduinoConsolePlugin;
import org.homio.addon.firmata.platform.BaseNoGui;
import org.homio.addon.firmata.platform.debug.TargetBoard;
import org.homio.addon.firmata.platform.debug.TargetPlatform;
import org.homio.addon.firmata.platform.helpers.PreferencesMap;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ConsoleArduinoProgrammerSetting implements ConsoleSettingPlugin<String>,
    SettingPluginOptions<String> {

  @Override
  public Integer getMaxWidth() {
    return 80;
  }

  @Override
  public @NotNull Collection<OptionModel> getOptions(Context context, JSONObject params) {
    List<OptionModel> programmerMenus = new ArrayList<>();

    if (BaseNoGui.packages != null) {
      TargetBoard board = BaseNoGui.getTargetBoard();
      if (board != null) {
        TargetPlatform boardPlatform = board.getContainerPlatform();
        TargetPlatform corePlatform = null;

        String core = board.getPreferences().get("build.core");
        if (core != null && core.contains(":")) {
          String[] split = core.split(":", 2);
          corePlatform = BaseNoGui.getCurrentTargetPlatformFromPackage(split[0]);
        }

        addProgrammersForPlatform(boardPlatform, programmerMenus);
        if (corePlatform != null) {
          addProgrammersForPlatform(corePlatform, programmerMenus);
        }
      }
    }
    return programmerMenus;
  }

  public void addProgrammersForPlatform(TargetPlatform platform, List<OptionModel> menus) {
    for (Map.Entry<String, PreferencesMap> entry : platform.getProgrammers().entrySet()) {
      String id = platform.getContainerPackage().getId() + ":" + entry.getKey();
      menus.add(OptionModel.of(id, entry.getValue().get("name")));
    }
  }

  @Override
  public int order() {
    return 250;
  }

  @Override
  public @NotNull Class<String> getType() {
    return String.class;
  }

  @Override
  public boolean acceptConsolePluginPage(ConsolePlugin consolePlugin) {
    return consolePlugin instanceof ArduinoConsolePlugin;
  }
}
