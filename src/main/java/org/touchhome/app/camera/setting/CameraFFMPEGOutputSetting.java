package org.touchhome.app.camera.setting;

import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptionsFileExplorer;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

public class CameraFFMPEGOutputSetting implements SettingPluginOptionsFileExplorer {
    @Override
    public int order() {
        return 100;
    }

    @Override
    public String getDefaultValue() {
        return rootPath().resolve("camera").toString();
    }

    @Override
    public Path rootPath() {
        return TouchHomeUtils.getMediaPath();
    }

    @Override
    public Predicate<Path> filterPath() {
        return path -> Files.isDirectory(path);
    }

    @Override
    public boolean removableOption(OptionModel optionModel) {
        return false;
    }
}
