package org.homio.app.setting;

import org.homio.api.Context;
import org.homio.api.entity.BaseEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginToggle;
import org.homio.app.model.var.WorkspaceGroup;
import org.jetbrains.annotations.NotNull;

public class WorkspaceGroupEntityCompactModeSetting implements SettingPluginToggle {

  @Override
  public Class<? extends BaseEntity> availableForEntity() {
    return WorkspaceGroup.class;
  }

  @Override
  public int order() {
    return 20;
  }

  @Override
  public @NotNull Icon getIcon() {
    return new Icon("fas fa-minimize");
  }

  @Override
  public @NotNull Icon getToggleIcon() {
    return new Icon("fas fa-maximize");
  }

  @Override
  public boolean isVisible(Context context) {
    return false;
  }
}
