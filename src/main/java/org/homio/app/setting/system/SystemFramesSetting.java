package org.homio.app.setting.system;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.homio.api.Context;
import org.homio.api.setting.SettingType;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;

public class SystemFramesSetting implements CoreSettingPlugin<ObjectNode> {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public @NotNull Class<ObjectNode> getType() {
    return ObjectNode.class;
  }

  @Override
  public @NotNull SettingType getSettingType() {
    return SettingType.TextInput;
  }

  @Override
  public int order() {
    return -1;
  }

  @Override
  public boolean isVisible(Context context) {
    return false;
  }
}
