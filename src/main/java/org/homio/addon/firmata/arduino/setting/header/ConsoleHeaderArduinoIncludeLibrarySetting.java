package org.homio.addon.firmata.arduino.setting.header;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.homio.addon.firmata.arduino.avr.LibraryOfSameTypeComparator;
import org.homio.addon.firmata.platform.BaseNoGui;
import org.homio.addon.firmata.platform.debug.TargetPlatform;
import org.homio.addon.firmata.platform.packages.LibraryList;
import org.homio.addon.firmata.platform.packages.UserLibrary;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.header.ConsoleHeaderSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

public class ConsoleHeaderArduinoIncludeLibrarySetting implements ConsoleHeaderSettingPlugin<String>,
    SettingPluginOptions<String> {

  @Override
  public @NotNull Collection<OptionModel> getOptions(Context context, JSONObject params) {
    List<OptionModel> options = new ArrayList<>();

    TargetPlatform targetPlatform = BaseNoGui.packages == null ? null : BaseNoGui.getTargetPlatform();
    if (targetPlatform != null) {
      LibraryList libs = getSortedLibraries();
      String lastLibType = null;
      for (UserLibrary lib : libs) {
        String libType = lib.getTypes().get(0);
        if (!libType.equals(lastLibType)) {
          if (lastLibType != null) {
            options.add(OptionModel.separator());
          }
          lastLibType = libType;
        }
        options.add(OptionModel.key(lib.getName()));
      }
    }

    return options;
  }

  private LibraryList getSortedLibraries() {
    LibraryList installedLibraries = BaseNoGui.librariesIndexer.getInstalledLibraries();
    installedLibraries.sort(new LibraryOfSameTypeComparator());
    return installedLibraries;
  }

  @Override
  public @NotNull Class<String> getType() {
    return String.class;
  }

  @Override
  public Icon getIcon() {
    return new Icon("fas fa-plus");
  }
}
