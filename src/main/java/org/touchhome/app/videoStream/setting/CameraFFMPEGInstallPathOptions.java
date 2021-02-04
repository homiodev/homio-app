package org.touchhome.app.videoStream.setting;

import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPluginOptionsFileExplorer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;

public class CameraFFMPEGInstallPathOptions implements SettingPluginOptionsFileExplorer {
    @Override
    public int order() {
        return 200;
    }

    @Override
    public Path rootPath() {
        return Paths.get("/");
    }

    @Override
    public String getDefaultValue() {
        return "ffmpeg";
    }

    @Override
    public Predicate<Path> filterPath() {
        return path -> !Files.isDirectory(path);
    }

    @Override
    public boolean removableOption(OptionModel optionModel) {
        return false;
    }
}
