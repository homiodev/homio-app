package org.touchhome.app.setting.console.editor;

import lombok.RequiredArgsConstructor;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.KeyValueEnum;
import org.touchhome.bundle.api.setting.SettingPluginOptionsEnum;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;

public class ConsoleEditorThemeSetting implements ConsoleSettingPlugin<ConsoleEditorThemeSetting.Theme>,
    SettingPluginOptionsEnum<ConsoleEditorThemeSetting.Theme> {

  @Override
  public Class<Theme> getType() {
    return Theme.class;
  }

  @Override
  public int order() {
    return 1200;
  }

  @Override
  public ConsolePlugin.RenderType[] renderTypes() {
    return new ConsolePlugin.RenderType[]{ConsolePlugin.RenderType.editor};
  }

  @RequiredArgsConstructor
  enum Theme implements KeyValueEnum {
    VsDark("vs-dark");
    private final String value;

    @Override
    public String getKey() {
      return value;
    }

    @Override
    public String getValue() {
      return name();
    }
  }
}
