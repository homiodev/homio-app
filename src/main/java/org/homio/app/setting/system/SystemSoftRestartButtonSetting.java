package org.homio.app.setting.system;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.entity.UserEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UI.Color;
import org.homio.app.LogService;
import org.homio.app.config.AppConfig;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@Log4j2
public class SystemSoftRestartButtonSetting
  implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

  public static void restart(ContextImpl context) {
    log.info("Fire homio app soft restarting...");
    ConfigurableApplicationContext appContext = context.getBean(ConfigurableApplicationContext.class);
    Thread thread = new Thread(() -> safeRestart(appContext));
    thread.setDaemon(false);
    thread.start();
  }

  private static void safeRestart(ConfigurableApplicationContext context) {
    try {
      context.close();
      new SpringApplicationBuilder(AppConfig.class).listeners(new LogService()).run();
      log.info("Homio app context restarted");
    } catch (Exception ex) {
      log.info("Could not doRestart: {}", ex.getMessage());
    }
  }

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public @NotNull Icon getIcon() {
    return new Icon("fas fa-power-off");
  }

  @Override
  public String getConfirmMsg() {
    return "W.CONFIRM.SYS_RESTART";
  }

  @Override
  public String getDialogColor() {
    return Color.ERROR_DIALOG;
  }

  @Override
  public int order() {
    return 200;
  }

  @Override
  public boolean isDisabled(Context context) {
    return !SystemUtils.IS_OS_LINUX;
  }

  @SneakyThrows
  @Override
  public void assertUserAccess(@NotNull Context context, @Nullable UserEntity user) {
    UserGuestEntity.assertAction(context,
      UserGuestEntity::getRestartDevice,
      "User is not allowed to restart device");
  }
}
