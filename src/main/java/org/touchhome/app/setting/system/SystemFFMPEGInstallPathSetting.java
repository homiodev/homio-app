package org.touchhome.app.setting.system;

import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptionsFileExplorer;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.util.CommonUtils;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SystemFFMPEGInstallPathSetting implements CoreSettingPlugin<Path>, SettingPluginOptionsFileExplorer {

    @Override
    public int order() {
        return 500;
    }

    @Override
    public Path rootPath() {
        return CommonUtils.getRootPath();
    }

    @Override
    public String getDefaultValue() {
        return "ffmpeg";
    }

    @Override
    public boolean removableOption(OptionModel optionModel) {
        return false;
    }

    @Override
    public boolean lazyLoading() {
        return true;
    }

    @Override
    public boolean allowSelectDirs() {
        return false;
    }

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }
}
