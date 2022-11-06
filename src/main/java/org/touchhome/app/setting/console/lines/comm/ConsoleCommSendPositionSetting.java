package org.touchhome.app.setting.console.lines.comm;

import lombok.RequiredArgsConstructor;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.model.KeyValueEnum;
import org.touchhome.bundle.api.setting.SettingPluginOptionsEnum;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;
import org.touchhome.bundle.api.ui.field.UIFieldType;

public class ConsoleCommSendPositionSetting implements ConsoleSettingPlugin<ConsoleCommSendPositionSetting.Position>,
    SettingPluginOptionsEnum<ConsoleCommSendPositionSetting.Position> {

  @Override
  public UIFieldType getSettingType() {
    return UIFieldType.SelectBoxButton;
  }

  @Override
  public Class<Position> getType() {
    return ConsoleCommSendPositionSetting.Position.class;
  }

  @Override
  public String getIcon() {
    return "fas fa-arrows-alt";
  }

  @Override
  public int order() {
    return 1200;
  }

  @Override
  public ConsolePlugin.RenderType[] renderTypes() {
    return new ConsolePlugin.RenderType[]{ConsolePlugin.RenderType.comm};
  }

  @RequiredArgsConstructor
  enum Position implements KeyValueEnum {
    Left(""),
    Right("justify-content: flex-end"),
    Center("justify-content: center"),
    Padding100("padding-left: 100px");
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
