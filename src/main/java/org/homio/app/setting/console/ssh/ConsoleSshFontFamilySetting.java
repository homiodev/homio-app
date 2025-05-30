package org.homio.app.setting.console.ssh;

import lombok.SneakyThrows;
import org.homio.api.Context;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.util.CommonUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.String.format;

public class ConsoleSshFontFamilySetting
  implements ConsoleSettingPlugin<String>, SettingPluginOptions<String> {

  @Override
  public @NotNull Class<String> getType() {
    return String.class;
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "DejaVu Sans Mono";
  }

  @SneakyThrows
  @Override
  public @NotNull Collection<OptionModel> getOptions(Context context, JSONObject params) {
    List<OptionModel> result = new ArrayList<>();
    Set<String> fml = new HashSet<>(Arrays.asList(
      "Cascadia Code",
      "Courier New",
      "Ubuntu Mono",
      "Source Sans Pro"));
    File fontsFile = CommonUtils.getConfigPath().resolve("fonts").toFile();
    if (fontsFile.exists()) {
      File[] fonts = fontsFile.listFiles();
      if (fonts != null) {
        for (File font : fonts) {
          Font trueFont = Font.createFont(Font.TRUETYPE_FONT, font);
          fml.add(trueFont.getFamily());
        }
      }
    }
    for (String fontFamily : fml) {
      result.add(OptionModel.of(fontFamily,
        format("<div style=\"font-family:%s\">%s</div>", fontFamily, fontFamily)));
    }
    return result;
  }

  @Override
  public int order() {
    return 450;
  }

  @Override
  public String[] pages() {
    return new String[]{"ssh"};
  }
}
