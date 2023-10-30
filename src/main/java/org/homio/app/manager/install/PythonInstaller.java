package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.log4j.Log4j2;
import org.homio.api.Context;
import org.homio.api.ContextHardware;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.service.DependencyExecutableInstaller;
import org.homio.api.util.CommonUtils;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Log4j2
public class PythonInstaller extends DependencyExecutableInstaller {

    public PythonInstaller(Context context) {
        super(context);
    }

    @Override
    public String getName() {
        return "python";
    }

    @Override
    public @Nullable String getExecutablePath(@NotNull Path execPath) {
        return getVersion() == null ? null : executable;
    }

    @Override
    protected @Nullable String getInstalledVersion() {
        ContextHardware hardware = context.hardware();
        executable = "python";
        if (IS_OS_WINDOWS) {
            Path targetPath = CommonUtils.getInstallPath().resolve("python").resolve("python.exe");
            if (Files.isRegularFile(targetPath)) {
                executable = targetPath.toString();
            }
        }
        String result = hardware.executeNoErrorThrow(executable + " --version", 60, null);
        if (result.startsWith("The system cannot")) {
            return null;
        }
        if (result.contains(" ")) {
            return result.split(" ")[1];
        }
        return result;
    }

    @Override
    protected void installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        executable = "python";
        if (IS_OS_LINUX) {
            ContextHardware hardware = context.hardware();
            hardware.installSoftware("python", 600);
        } else {
            String url = context.setting().getEnv("source-python");
            if (url == null) {
                url = "https://www.python.org/ftp/python/3.12.0/python-3.12.0-embed-amd64.zip";
            }
            ArchiveUtil.downloadAndExtract(url, "python.zip",
                (progress, message, error) -> {
                    progressBar.progress(progress, message);
                    log.info("Python: {}", message);
                }, CommonUtils.getInstallPath().resolve("python"));
            executable = CommonUtils.getInstallPath().resolve("python").resolve("python.exe").toString();

            // install pip
            Path pipPath = Paths.get(executable).resolveSibling("get-pip.py");
            Curl.download("https://bootstrap.pypa.io/get-pip.py", pipPath);
            context.hardware().execute("%s %s".formatted(executable, pipPath), 600);
        }
    }
}
