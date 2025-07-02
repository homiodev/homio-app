package org.homio.app.setting.system;

import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.app.model.entity.LocalBoardEntity;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Log4j2
public class SystemSubscribeNotificationsButtonSetting
  implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public @NotNull Icon getIcon() {
    return new Icon("fas fa-envelope");
  }

  @Override
  public String getConfirmMsg() {
    return "W.CONFIRM.ENABLE_NOTIFICATIONS";
  }

  @Override
  public int order() {
    return 500;
  }

  @Override
  public boolean isDisabled(Context context) {
    return false;
    // return LocalBoardEntity.getEntity(context).getVapidSubscription() != null;
  }
}
