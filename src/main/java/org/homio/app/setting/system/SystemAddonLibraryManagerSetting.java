package org.homio.app.setting.system;

import static java.lang.String.format;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.homio.api.EntityContext;
import org.homio.api.cache.CachedValue;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.UnzipFileIssueHandler;
import org.homio.api.repository.GitHubProject;
import org.homio.api.setting.SettingPluginPackageInstall;
import org.homio.api.util.CommonUtils;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

@Log4j2
public class SystemAddonLibraryManagerSetting
        implements SettingPluginPackageInstall, CoreSettingPlugin<JSONObject> {

    private static final CachedValue<Collection<PackageModel>, EntityContext> addons =
            new CachedValue<>(Duration.ofHours(24),
                    SystemAddonLibraryManagerSetting::readAddons);

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public @NotNull GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public boolean isVisible(EntityContext entityContext) {
        return false;
    }

    @Override
    public PackageContext allPackages(EntityContext entityContext) {
        PackageContext packageContext = new PackageContext();
        try {
            Collection<PackageModel> allPackageModels = new ArrayList<>(addons.getValue(entityContext));
            filterMatchPackages(entityContext, allPackageModels);
            packageContext.setPackages(allPackageModels);
        } catch (Exception ex) {
            packageContext.setError(CommonUtils.getErrorMessage(ex));
        }
        return packageContext;
    }

    /**
     * Remove packages if no versions available. Also remove versions that not match app major version
     */
    private void filterMatchPackages(EntityContext entityContext, Collection<PackageModel> allPackageModels) {
        int appVersion = entityContext.setting().getApplicationMajorVersion();
        if (appVersion == 0) {
            return;
        }
        allPackageModels.removeIf(packageModel -> {
            if (packageModel.getVersions() == null) {
                return true;
            } else {
                packageModel.getVersions().removeIf(packageVersion -> !AddonContext.validVersion(packageVersion, appVersion));
                return packageModel.getVersions().isEmpty();
            }
        });
    }

    @Override
    public PackageContext installedPackages(EntityContext entityContext) {
        return new PackageContext(
                null,
                ((EntityContextImpl) entityContext).getAddon().getInstalledAddons()
                        .stream()
                        .map(this::build)
                        .collect(Collectors.toSet()));
    }

    @Override
    public void installPackage(EntityContext entityContext, PackageRequest request, ProgressBar progressBar) {
        ((EntityContextImpl) entityContext).getAddon().installAddon(request.getName(), request.getUrl(),
                request.getVersion(), progressBar);
    }

    @Override
    public void unInstallPackage(
            EntityContext entityContext, PackageRequest packageRequest, ProgressBar progressBar) {
        ((EntityContextImpl) entityContext).getAddon().uninstallAddon(packageRequest.getName(), true);
    }

    @Override
    public String getConfirmMsg() {
        return null;
    }

    private PackageModel build(AddonContext addonContext) {
        return new PackageModel(
                addonContext.getAddonID(),
                addonContext.getPomFile().getName(),
                addonContext.getPomFile().getDescription())
                .setVersion(addonContext.getVersion())
                .setReadme(addonContext.getPomFile().getDescription());
    }

    private static Collection<PackageModel> readAddons(EntityContext entityContext) {
        Collection<PackageModel> addons = new ArrayList<>();
        for (String repoURL : entityContext.setting().getValue(SystemAddonRepositoriesSetting.class)) {
            addons.addAll(getAddons(repoURL));
        }
        return addons;
    }

    @SneakyThrows
    private static Collection<PackageModel> getAddons(String repoURL) {
        log.info("Fetch addons for repo: {}", repoURL);
        try {
            GitHubProject addonsRepo = GitHubProject.of(repoURL);
            Path addonPath = CommonUtils.getTmpPath().resolve(addonsRepo.getRepo());
            Files.createDirectories(addonPath);
            Path iconsArchivePath = addonPath.resolve("icons.7z");
            Path iconsPath = addonPath.resolve("icons");
            String iconsURL = repoURL + "/raw/master/icons.7z";
            if (isRequireDownloadIcons(iconsArchivePath, iconsPath, iconsURL)) {
                Curl.download(iconsURL, iconsArchivePath);
                ArchiveUtil.unzip(iconsArchivePath, addonPath, UnzipFileIssueHandler.replace);
            }

            Map<String, Map<String, Object>> addons = Objects.requireNonNull(addonsRepo.getFile("addons.yml", Map.class));
            return addons.entrySet().stream()
                    .map(entry -> readAddon(entry.getKey(), entry.getValue(), iconsPath))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.error("Unable to fetch addons for repo: {}", repoURL, ex);
            throw ex;
        }
    }

    private static boolean isRequireDownloadIcons(Path iconsArchivePath, Path iconsPath, String iconsURL) {
        if (!Files.exists(iconsPath) || !Files.exists(iconsArchivePath)) {
            return true;
        }
        try {
            if (Files.size(iconsArchivePath) != Curl.getFileSize(iconsURL)) {
                return true;
            }
        } catch (Exception ignore) {
        } // ssl handshake error
        return false;
    }

    private static PackageModel readAddon(String name, Map<String, Object> addonConfig, Path iconsPath) {
        String repository = (String) addonConfig.get("repository");
        try {
            log.debug("Read addon: {}", repository);
            GitHubProject addonRepo = GitHubProject.of(repository);
            List<Object> versions = (List<Object>) addonConfig.get("versions");
            if (versions != null) {
                List<String> strVersions = versions.stream().map(Object::toString).collect(Collectors.toList());
                String lastReleaseVersion = strVersions.get(strVersions.size() - 1);
                // String jarFile = addonRepo.getRepo() + ".jar";
                // Model pomModel = addonRepo.getPomModel();
                String key = name.startsWith("addon-") ? name : "addon-" + name;
                PackageModel entity = new PackageModel(key, (String) addonConfig.get("name"), (String) addonConfig.get("description"));
                entity.setJarUrl(format("https://github.com/%s/releases/download/%s/%s.jar", repository, "%s", addonRepo.getRepo()));
                /*entity.setAuthor(pomModel.getDevelopers().stream()
                                         .map(Contributor::getName)
                                         .collect(Collectors.joining(", ")));*/
                entity.setWebsite("https://github.com/" + repository);
                entity.setCategory((String) addonConfig.get("category"));
                entity.setVersion(lastReleaseVersion);
                entity.setVersions(strVersions);
                entity.setIcon(Base64.getEncoder().encodeToString(getIcon(iconsPath.resolve(name + ".png"))));
                entity.setReadmeLazyLoading(true);
                // entity.setReadme(addonRepo.getFile("README.md", String.class));
                entity.setRemovable(true);
                return entity;
            }
        } catch (Exception ae) {
            log.error("Unable to fetch addon for repo: {}", repository, ae);
        }
        return null;
    }

    private static byte[] getIcon(Path icon) throws IOException {
        byte[] rawIcon;
        if (!Files.exists(icon)) {
            URL resource = CommonUtils.class.getClassLoader().getResource("images/no-image.png");
            rawIcon = IOUtils.toByteArray(Objects.requireNonNull(resource));
        } else {
            rawIcon = Files.readAllBytes(icon);
        }
        return rawIcon;
    }
}
