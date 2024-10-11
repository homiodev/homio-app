package org.homio.app.setting.system;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.SystemUtils;
import org.homio.api.Context;
import org.homio.api.entity.UserEntity;
import org.homio.api.model.Icon;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.ui.UI.Color;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.model.entity.user.UserGuestEntity;
import org.homio.app.setting.CoreSettingPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

@Log4j2
public class SystemHardRestartButtonSetting
        implements CoreSettingPlugin<JSONObject>, SettingPluginButton {

    public static void restart(ContextImpl context) {
        log.info("Fire device restarting");
        context.hardware().reboot();
    }

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public @NotNull Icon getIcon() {
        return new Icon("fas fa-computer");
    }

    @Override
    public String getConfirmMsg() {
        return "W.CONFIRM.SYS_HARD_RESTART";
    }

    @Override
    public String getDialogColor() {
        return Color.ERROR_DIALOG;
    }

    @Override
    public int order() {
        return 210;
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
