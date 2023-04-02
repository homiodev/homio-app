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
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.IOUtils;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Model;
import org.homio.app.extloader.BundleContext;
import org.homio.app.extloader.BundleContextService;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.fs.archive.ArchiveUtil;
import org.homio.bundle.api.fs.archive.ArchiveUtil.UnzipFileIssueHandler;
import org.homio.bundle.api.model.CachedValue;
import org.homio.bundle.api.repository.GitHubProject;
import org.homio.bundle.api.setting.SettingPluginPackageInstall;
import org.homio.bundle.api.ui.field.ProgressBar;
import org.homio.bundle.api.util.CommonUtils;
import org.homio.bundle.api.util.Curl;
import org.json.JSONObject;

@Log4j2
public class SystemBundleLibraryManagerSetting
    implements SettingPluginPackageInstall, CoreSettingPlugin<JSONObject> {

    private static final CachedValue<Collection<PackageModel>, EntityContext> addons = new CachedValue<>(Duration.ofHours(24),
        SystemBundleLibraryManagerSetting::readAddons);

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public GroupKey getGroupKey() {
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
            packageContext.setPackages(addons.getValue(entityContext));
        } catch (Exception ex) {
            packageContext.setError(ex.getMessage());
        }
        return packageContext;
    }

    @Override
    public PackageContext installedPackages(EntityContext entityContext) {
        return new PackageContext(
            null,
            ((EntityContextImpl) entityContext)
                .getBundles().values().stream()
                .filter(b -> b.getBundleContext() != null)
                .map(b -> build(b.getBundleContext()))
                .collect(Collectors.toSet()));
    }

    @Override
    public void installPackage(EntityContext entityContext, PackageRequest packageRequest, ProgressBar progressBar) {
        entityContext.getBean(BundleContextService.class).installBundle(
            packageRequest.getName(),
            packageRequest.getUrl(),
            packageRequest.getVersion());
    }

    @Override
    public void unInstallPackage(
        EntityContext entityContext, PackageRequest packageRequest, ProgressBar progressBar) {
        entityContext.getBean(BundleContextService.class).uninstallBundle(packageRequest.getName());
    }

    private PackageModel build(BundleContext bundleContext) {
        return new PackageModel()
            .setName(bundleContext.getBundleID())
            .setTitle(bundleContext.getPomFile().getName())
            .setVersion(bundleContext.getVersion())
            .setReadme(bundleContext.getPomFile().getDescription());
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
        log.info("Try fetch addons for repo: {}", repoURL);
        Collection<PackageModel> list = new ArrayList<>();
        try {
            GitHubProject addonsRepo = GitHubProject.of(repoURL);
            Path addonPath = CommonUtils.getTmpPath().resolve(addonsRepo.getRepo());
            Path iconsArchivePath = addonPath.resolve("icons.7z");
            Path iconsPath = addonPath.resolve("icons");
            String iconsURL = repoURL + "/raw/master/icons.7z";
            if (isRequireDownloadIcons(iconsArchivePath, iconsPath, iconsURL)) {
                Curl.download(iconsURL, iconsArchivePath);
                ArchiveUtil.unzip(iconsArchivePath, addonPath, UnzipFileIssueHandler.replace);
            }

            Map<String, Map<String, String>> addons = addonsRepo.getFile("addons.yaml", Map.class);
            ForkJoinPool customThreadPool = new ForkJoinPool(addons.size());
            return customThreadPool.submit(() ->
                addons.entrySet().parallelStream()
                      .map(entry -> readAddon(entry.getKey(), entry.getValue(), iconsPath))
                      .filter(Objects::nonNull)
                      .collect(Collectors.toList())).get();
        } catch (Exception ex) {
            log.error("Unable to fetch addons for repo: {}", repoURL, ex);
        }
        return list;
    }

    private static boolean isRequireDownloadIcons(Path iconsArchivePath, Path iconsPath, String iconsURL) throws IOException {
        if (!Files.exists(iconsPath) || !Files.exists(iconsArchivePath)) {return true;}
        try {
            if (Files.size(iconsArchivePath) != Curl.getFileSize(iconsURL)) {return true;}
        } catch (Exception ignore) {} // ssl handshake error
        return false;
    }

    private static PackageModel readAddon(String name, Map<String, String> addonConfig, Path iconsPath) {
        String repository = addonConfig.get("repository");
        try {
            log.info("Read addon: {}", repository);
            GitHubProject addonRepo = GitHubProject.of(repository);
            String lastReleaseVersion = addonConfig.get("version");
            if (lastReleaseVersion != null) {
                String jarFile = addonRepo.getRepo() + ".jar";
                Model pomModel = addonRepo.getPomModel();
                PackageModel entity = new PackageModel();
                entity.setJarUrl(format("https://github.com/%s/releases/download/%s/%s.jar", repository, lastReleaseVersion, addonRepo.getRepo()));
                entity.setTitle(pomModel.getDescription());
                entity.setName(pomModel.getArtifactId());
                entity.setAuthor(pomModel.getDevelopers().stream()
                                         .map(Contributor::getName)
                                         .collect(Collectors.joining(", ")));
                entity.setWebsite("https://github.com/" + repository);
                entity.setCategory(pomModel.getProperties().getProperty("category"));
                entity.setVersion(lastReleaseVersion);
                entity.setIcon(Base64.getEncoder().encodeToString(getIcon(iconsPath.resolve(name + ".png"))));
                entity.setReadme(addonRepo.getFile("README.md", String.class));
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
            URL resource = CommonUtils.class.getClassLoader().getResource("image/no-image.png");
            rawIcon = IOUtils.toByteArray(Objects.requireNonNull(resource));
        } else {
            rawIcon = Files.readAllBytes(icon);
        }
        return rawIcon;
    }
}
