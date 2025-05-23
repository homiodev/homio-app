package org.homio.app.manager;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.exception.NotFoundException;
import org.homio.api.setting.SettingPluginPackageInstall;
import org.homio.api.setting.SettingPluginPackageInstall.PackageRequest;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.utils.color.ColorThief;
import org.homio.app.utils.color.MMCQ;
import org.homio.app.utils.color.RGBUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Log4j2
@Component
@RequiredArgsConstructor
public class AddonService implements ContextCreated, ContextRefreshed {

  // constructor parameters
  private final Context context;
  // true - installing, false removing
  @Getter
  private final Map<String, Boolean> packagesInProgress = new ConcurrentHashMap<>();
  private Map<String, String> addonColorMap;
  private Map<String, AddonEntrypoint> addonMap;
  private Collection<AddonEntrypoint> allAddonEntrypoint;

  @Override
  public void onContextCreated(ContextImpl context) throws Exception {
    onContextRefresh(context);
  }

  @Override
  public void onContextRefresh(Context context) throws Exception {
    this.allAddonEntrypoint = this.context.getBeansOfType(AddonEntrypoint.class);
    this.addonMap = allAddonEntrypoint.stream().collect(Collectors.toMap(AddonEntrypoint::getAddonID, s -> s));
    this.addonColorMap = new HashMap<>();

    for (AddonEntrypoint addonEntrypoint : allAddonEntrypoint) {
      try {
        URL imageURL = addonEntrypoint.getAddonImageURL();
        if (imageURL == null) {
          throw new IllegalStateException("Unable to find image for addon: " + addonEntrypoint.getAddonID());
        }
        BufferedImage img = ImageIO.read(imageURL);
        MMCQ.CMap result = ColorThief.getColorMap(img, 5);
        MMCQ.VBox dominantColor = result.vboxes.get(addonEntrypoint.getAddonImageColorIndex().ordinal());
        int[] rgb = dominantColor.avg(false);
        addonColorMap.put(addonEntrypoint.getAddonID(), RGBUtil.toRGBHexString(rgb));
      } catch (Exception ex) {
        log.error("Unable to start app due error in addon: <{}>", addonEntrypoint.getAddonID(), ex);
        throw ex;
      }
    }
  }

  public AddonEntrypoint findAddonEntrypoint(String addonID) {
    return addonMap.get(addonID);
  }

  public AddonEntrypoint getAddon(String addonID) {
    AddonEntrypoint addonEntrypoint = addonMap.get(addonID);
    if (addonEntrypoint == null) {
      throw new NotFoundException("Unable to find addon: " + addonID);
    }
    return addonEntrypoint;
  }

  public String getAddonColor(String addonID) {
    return addonColorMap.get(addonID);
  }

  public Collection<AddonEntrypoint> getAddons() {
    return allAddonEntrypoint;
  }

  public void installPackage(SettingPluginPackageInstall settingPlugin, PackageRequest packageRequest) {
    if (!packagesInProgress.containsKey(packageRequest.getName())) {
      packagesInProgress.put(packageRequest.getName(), true);
      String key = "Install " + packageRequest.getName() + "/" + packageRequest.getVersion();
      context.ui().progress().run(key, false, progressBar ->
          settingPlugin.installPackage(context, packageRequest, progressBar),
        ex -> packagesInProgress.remove(packageRequest.getName()));
    } else {
      context.ui().toastr().error("W.ERROR.UPDATE_IN_PROGRESS");
    }
  }

  public void unInstallPackage(SettingPluginPackageInstall settingPlugin, PackageRequest packageRequest) {
    if (!packagesInProgress.containsKey(packageRequest.getName())) {
      packagesInProgress.put(packageRequest.getName(), false);
      context.ui().progress().run("Uninstall " + packageRequest.getName() + "/" + packageRequest.getVersion(), false,
        progressBar -> settingPlugin.unInstallPackage(context, packageRequest, progressBar),
        ex -> packagesInProgress.remove(packageRequest.getName()));
    }
  }

  @SneakyThrows
  public @Nullable InputStream getImageStream(String addonID) {
    AddonEntrypoint addonEntrypoint = getAddon(addonID.contains("-") ? addonID.substring(0, addonID.indexOf("-")) : addonID);
    URL imageUrl = addonEntrypoint.getResource("images/%s.png".formatted(addonID));
    if (imageUrl == null) {
      imageUrl = addonEntrypoint.getAddonImageURL();
    }
    if (imageUrl == null) {
      return null;
    }
    return imageUrl.openStream();
  }

  public List<AddonJson> getAllAddonJson() {
    List<AddonJson> addons = new ArrayList<>();
    for (AddonEntrypoint addonEntrypoint : getAddons()) {
      addons.add(new AddonJson(addonEntrypoint.getAddonID(),
        getAddonColor(addonEntrypoint.getAddonID()), 0));
    }
    Collections.sort(addons);
    return addons;
  }

  public record AddonJson(String id, String color, int order) implements Comparable<AddonJson> {

    @Override
    public int compareTo(@NotNull AddonJson o) {
      return Integer.compare(order, o.order);
    }
  }
}
