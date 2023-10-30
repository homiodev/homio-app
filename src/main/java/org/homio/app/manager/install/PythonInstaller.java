package org.homio.app.manager.install;

import static org.apache.commons.lang3.SystemUtils.IS_OS_LINUX;
import static org.apache.commons.lang3.SystemUtils.IS_OS_WINDOWS;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import lombok.SneakyThrows;
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

    private final ReentrantLock pythonLock;

    public PythonInstaller(Context context, ReentrantLock pythonLock) {
        super(context);
        this.pythonLock = pythonLock;
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

    @SneakyThrows
    @Override
    protected void installDependencyInternal(@NotNull ProgressBar progressBar, String version) {
        try {
            pythonLock.lock();

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
                Path pythonDir = CommonUtils.getInstallPath().resolve("python");
                executable = pythonDir.resolve("python.exe").toString();

                // install pip
                Path pipPath = pythonDir.resolve("get-pip.py");
                Curl.download("https://bootstrap.pypa.io/get-pip.py", pipPath);
                context.hardware().execute("%s %s".formatted(executable, pipPath), 600);

                modifyPathFile(pythonDir);
                // install virtualenv
                context.hardware().execute("%s -m pip install virtualenv".formatted(executable), 600);
                Path venvPath = CommonUtils.getConfigPath().resolve("venv");
                Files.createDirectories(venvPath);
            }
        } finally {
            pythonLock.unlock();
        }
    }

    @SneakyThrows
    private static void modifyPathFile(Path pythonDir) {
        File[] files = pythonDir.toFile().listFiles();
        if (files != null) {
            Pattern filePattern = Pattern.compile("python.*._pth");
            for (File file : files) {
                if (filePattern.matcher(file.getName()).matches()) {
                    try (FileWriter fileWriter = new FileWriter(file, true)) {
                        fileWriter.write(System.lineSeparator());
                        fileWriter.write("Lib/site-packages");
                    }
                    return;
                }
            }
        }
    }
}
