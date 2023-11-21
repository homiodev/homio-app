package org.homio.app.rest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.homio.app.rest.InstallUtils.GitHubDescription.Asset;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.homio.hquery.hardware.other.MachineHardwareRepository;

public final class InstallUtils {

    @SneakyThrows
    public static void downloadTmate(ProgressBar progressBar, MachineHardwareRepository repository, Path rootPath) {
        if (SystemUtils.IS_OS_LINUX) {
            try {
                repository.installSoftware("tmate", 60, progressBar);
            } catch (Exception ex) {
                System.out.printf("Unable to install tmate. Error: %s%n", ex.getMessage());
                String arm = getTmateArm(repository);
                if (arm != null) {
                    String url = "https://github.com/tmate-io/tmate/releases/download/2.4.0/tmate-2.4.0-static-linux-%s.tar.xz".formatted(arm);
                    Path target = rootPath.resolve("tmate.tar.xz");
                    System.out.printf("Download tmate %s to %s%n", url, target);
                    Curl.downloadWithProgress(url, target, progressBar);
                    repository.execute("sudo tar -C %s -xvf %s/tmate.tar.xz".formatted(rootPath, rootPath));
                    Files.deleteIfExists(target);
                    Path unpackedTmate = rootPath.resolve("tmate-2.4.0-static-linux-%s".formatted(arm));
                    Files.createDirectories(Paths.get("ssh"));
                    Path tmate = Paths.get("ssh/tmate");
                    Files.move(unpackedTmate.resolve("tmate"), tmate, StandardCopyOption.REPLACE_EXISTING);
                    FileUtils.deleteDirectory(unpackedTmate.toFile());
                    repository.setPermissions(tmate, 555); // r+w for all
                } else {
                    System.err.println("Unable to find device arm");
                }
            }
        }
    }

    @SneakyThrows
    public static void downloadApp(ProgressBar progressBar, Path rootPath, MachineHardwareRepository repository) {
        Path archiveAppPath = rootPath.resolve("homio-app.zip");
        Files.deleteIfExists(archiveAppPath);

        System.out.println("Downloading application..");
        GitHubDescription gitHubDescription =
                Curl.get("https://api.github.com/repos/homiodev/homio-app/releases/latest", GitHubDescription.class);

        Asset asset = gitHubDescription.assets.stream()
                .filter(a -> a.name.equals(archiveAppPath.getFileName().toString()))
                .findAny().orElse(null);
        if (asset == null) {
            throw new IllegalStateException("Unable to find " + archiveAppPath.getFileName() + " asset from server");
        }
        System.out.printf("Downloading '%s' to '%s'%n", archiveAppPath.getFileName(), archiveAppPath);
        Curl.downloadWithProgress(asset.browser_download_url, archiveAppPath, progressBar);

        System.out.println("Extracting homio-app.zip");
        repository.execute("unzip -o " + archiveAppPath, 300, progressBar);
        Files.delete(archiveAppPath);
        System.out.println("Finished extract homio-app.zip");
    }

    private static String getTmateArm(MachineHardwareRepository repository) {
        String architecture = repository.getMachineInfo().getArchitecture();
        if (architecture.startsWith("armv6")) {
            return "arm32v6";
        } else if (architecture.startsWith("armv7")) {
            return "arm32v7";
        } else if (architecture.startsWith("i386")) {
            return "i386";
        } else if (architecture.startsWith("armv8") || architecture.startsWith("aarch64")) {
            return "arm64v8";
        } else if (architecture.startsWith("x86_64")) {
            return "amd64";
        }
        return null;
    }

    @Setter
    @Getter
    public static class GitHubDescription {

        private String name;
        private String tag_name;
        private List<Asset> assets = new ArrayList<>();

        @Setter
        @Getter
        public static class Asset {

            private String name;
            private long size;
            private String browser_download_url;
            private String updated_at;
        }
    }
}
