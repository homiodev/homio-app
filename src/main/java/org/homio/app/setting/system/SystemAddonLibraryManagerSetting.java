package org.homio.app.setting.system;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.api.Context;
import org.homio.api.cache.CachedValue;
import org.homio.api.fs.archive.ArchiveUtil;
import org.homio.api.fs.archive.ArchiveUtil.UnzipFileIssueHandler;
import org.homio.api.repository.GitHubProject;
import org.homio.api.setting.SettingPluginPackageInstall;
import org.homio.api.util.CommonUtils;
import org.homio.app.extloader.AddonContext;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextHardwareImpl;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.hquery.Curl;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@Log4j2
public class SystemAddonLibraryManagerSetting
  implements SettingPluginPackageInstall, CoreSettingPlugin<JSONObject> {

  private static final Path addonsCopy = CommonUtils.getTmpPath().resolve("addons-copy.json");
  private static final CachedValue<Collection<PackageModel>, Context> addons =
    new CachedValue<>(Duration.ofHours(24),
      SystemAddonLibraryManagerSetting::readAddons);

  @SneakyThrows
  private static Collection<PackageModel> readAddons(Context context) {
    Collection<PackageModel> addons = new ArrayList<>();
    Set<String> urls = new HashSet<>(SystemAddonRepositoriesSetting.BUILD_IN_ADDON_REPO);
    urls.addAll(context.setting().getValue(SystemAddonRepositoriesSetting.class));
    urls.stream().filter(url -> !StringUtils.isEmpty(url)).forEach(url -> {
      try {
        addons.addAll(getAddons(url, context));
      } catch (Exception ex) {
        log.warn("Unable to fetch addons from repository: {}. Msg: {}", url, CommonUtils.getErrorMessage(ex));
      }
    });
    OBJECT_MAPPER.writeValue(addonsCopy.toFile(), addons);
    return addons;
  }

  @SneakyThrows
  private static Collection<PackageModel> getAddons(String repoURL, Context context) {
    log.info("Fetch addons for repo: {}", repoURL);
    try {
      GitHubProject addonsRepo = GitHubProject.of(repoURL);
      Path addonPath = CommonUtils.getTmpPath().resolve(addonsRepo.getRepo());
      Files.createDirectories(addonPath);
      Path iconsArchivePath = addonPath.resolve("icons.7z");
      Path iconsPath = addonPath.resolve("icons");
      String iconsURL = repoURL + "/raw/master/icons.7z";
      if (isRequireDownloadIcons(iconsArchivePath, iconsPath, iconsURL)) {
        try {
          Curl.download(iconsURL, iconsArchivePath);
          ArchiveUtil.unzip(iconsArchivePath, addonPath, UnzipFileIssueHandler.replace);
        } catch (Exception ex) {
          log.error("Unable to fetch addon icons", ex);
        }
      }

      var arch = ContextHardwareImpl.getArchitecture(context);
      Map<String, Map<String, Object>> addons = addonsRepo.getFile("addons.yml", Map.class);
      return addons.entrySet().stream()
        .map(entry -> readAddon(entry.getKey(), entry.getValue(), iconsPath, arch))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    } catch (Exception ex) {
      log.error("Unable to fetch addons for repo: {}", repoURL, ex);
      throw ex;
    }
  }

  private static boolean isRequireDownloadIcons(Path iconsArchivePath, Path iconsPath, String iconsURL) {
    if (!Files.exists(iconsPath) || !Files.exists(iconsArchivePath) || Objects.requireNonNull(iconsPath.toFile().listFiles()).length == 0) {
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

  private static PackageModel readAddon(String name, Map<String, Object> addonConfig, Path iconsPath, ContextHardwareImpl.Architecture arch) {
    String repository = (String) addonConfig.get("repository");
    try {
      log.debug("Read addon: {}", repository);
      GitHubProject addonRepo = GitHubProject.of(repository);
      List<Object> versions = (List<Object>) addonConfig.get("versions");
      if (versions != null) {
        List<String> strVersions = versions.stream().map(Object::toString).collect(Collectors.toList());
        String lastReleaseVersion = strVersions.getLast();
        String key = name.startsWith("addon-") ? name : "addon-" + name;
        PackageModel entity = new PackageModel()
          .setName(key)
          .setTitle((String) addonConfig.get("name"))
          .setDescription((String) addonConfig.get("description"));
        var platformAppender = findValidPlatformAppender(arch, entity, addonConfig);
        entity.setJarUrl(format("https://github.com/%s/releases/download/%s/%s%s.jar", repository, "%s", addonRepo.getRepo(), platformAppender));
        entity.setWebsite("https://github.com/" + repository);
        entity.setCategory((String) addonConfig.get("category"));
        entity.setVersion(lastReleaseVersion);
        entity.setVersions(strVersions);
        entity.setIcon(Base64.getEncoder().encodeToString(getIcon(iconsPath.resolve(name + ".png"))));
        entity.setReadmeLazyLoading(true);
        // entity.setReadme(addonRepo.getFile("README.md", String.class));
        return entity;
      }
    } catch (Exception ae) {
      log.error("Unable to fetch addon for repo: {}", repository, ae);
    }
    return null;
  }

  private static String findValidPlatformAppender(ContextHardwareImpl.Architecture arch, PackageModel entity, Map<String, Object> addonConfig) {
    var platforms = (List<Object>) addonConfig.get("platform");
    if (platforms != null && !platforms.contains("all")) {
      entity.setDisabled(true);
      for (Object platform : platforms) {
        if (arch.isMatch(platform.toString())) {
          entity.setDisabled(false);
          return "-" + platform;
        }
      }
    }
    return "";
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

  @Override
  public int order() {
    return 1000;
  }

  @Override
  public @NotNull GroupKey getGroupKey() {
    return GroupKey.system;
  }

  @Override
  public boolean isVisible(Context context) {
    return false;
  }

  @Override
  public PackageContext allPackages(@NotNull Context context) {
    PackageContext packageContext = new PackageContext();
    try {
      Collection<PackageModel> allPackageModels = new ArrayList<>(addons.getValue(context));
      filterMatchPackages(context, allPackageModels);
      packageContext.setPackages(allPackageModels);
    } catch (Exception ex) {
      packageContext.setPackages(List.of());
      packageContext.setError(CommonUtils.getErrorMessage(ex));
      // try fetch packages from local copy
      if (Files.exists(addonsCopy)) {
        try {
          packageContext.setPackages(OBJECT_MAPPER.readValue(addonsCopy.toFile(), new TypeReference<List<PackageModel>>() {
          }));
        } catch (IOException ignore) {
          FileUtils.deleteQuietly(addonsCopy.toFile());
        }
      }
    }
    return packageContext;
  }

  @Override
  public PackageContext installedPackages(@NotNull Context context) {
    return new PackageContext(
      null,
      ((ContextImpl) context).getAddon().getInstalledAddons()
        .stream()
        .map(this::build)
        .collect(Collectors.toSet()));
  }

  @Override
  public void installPackage(@NotNull Context context, PackageRequest request, @NotNull ProgressBar progressBar) {
    ((ContextImpl) context).getAddon().installAddon(request.getName(), request.getUrl(),
      request.getVersion(), progressBar);
  }

  @Override
  public void unInstallPackage(
    @NotNull Context context, PackageRequest packageRequest, @NotNull ProgressBar progressBar) {
    ((ContextImpl) context).getAddon().uninstallAddon(packageRequest.getName(), true);
  }

  /**
   * Remove packages if no versions available. Also remove versions that do not match an app major version
   */
  private void filterMatchPackages(Context context, Collection<PackageModel> allPackageModels) {
    int appVersion = context.setting().getApplicationMajorVersion();
    if (appVersion == 0) {
      return;
    }
    for (PackageModel model : allPackageModels) {
      if (model.getVersions() == null) {
        model.setDisabled(true);
      } else {
        model.getVersions().removeIf(packageVersion -> !AddonContext.validVersion(packageVersion, appVersion));
        if (model.getVersions().isEmpty()) {
          model.setDisabled(true);
        }
      }
    }
  }

  private PackageModel build(AddonContext addonContext) {
    return new PackageModel()
      .setName(addonContext.getAddonID())
      .setTitle(addonContext.getAddonFriendlyName())
      .setVersion(addonContext.getVersion());
  }
}
