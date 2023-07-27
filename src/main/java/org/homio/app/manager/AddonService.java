package org.homio.app.manager;

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
import javax.imageio.ImageIO;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.exception.NotFoundException;
import org.homio.api.setting.SettingPluginPackageInstall;
import org.homio.api.setting.SettingPluginPackageInstall.PackageRequest;
import org.homio.app.manager.common.EntityContextImpl;
import org.homio.app.spring.ContextCreated;
import org.homio.app.spring.ContextRefreshed;
import org.homio.app.utils.color.ColorThief;
import org.homio.app.utils.color.MMCQ;
import org.homio.app.utils.color.RGBUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class AddonService implements ContextCreated, ContextRefreshed {

    // constructor parameters
    private final EntityContext entityContext;
    private Map<String, String> addonColorMap;
    private Map<String, AddonEntrypoint> addonMap;
    private Collection<AddonEntrypoint> allAddonEntrypoint;

    // true - installing, false removing
    @Getter
    private final Map<String, Boolean> packagesInProgress = new ConcurrentHashMap<>();

    @Override
    public void onContextCreated(EntityContextImpl entityContext) throws Exception {
        onContextRefresh(entityContext);
    }

    @Override
    public void onContextRefresh(EntityContext entityContext) throws Exception {
        this.allAddonEntrypoint = this.entityContext.getBeansOfType(AddonEntrypoint.class);
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
            entityContext.ui().runWithProgress(key, false, progressBar ->
                    settingPlugin.installPackage(entityContext, packageRequest, progressBar),
                ex -> packagesInProgress.remove(packageRequest.getName()));
        } else {
            entityContext.ui().sendErrorMessage("ERROR.UPDATE_IN_PROGRESS");
        }
    }

    public void unInstallPackage(SettingPluginPackageInstall settingPlugin, PackageRequest packageRequest) {
        if (!packagesInProgress.containsKey(packageRequest.getName())) {
            packagesInProgress.put(packageRequest.getName(), false);
            entityContext.ui().runWithProgress("Uninstall " + packageRequest.getName() + "/" + packageRequest.getVersion(), false,
                progressBar -> settingPlugin.unInstallPackage(entityContext, packageRequest, progressBar),
                ex -> packagesInProgress.remove(packageRequest.getName()));
        }
    }

    @SneakyThrows
    public @Nullable InputStream getImageStream(String addonID) {
        AddonEntrypoint addonEntrypoint = getAddon(addonID.contains("-") ? addonID.substring(0, addonID.indexOf("-")) : addonID);
        URL imageUrl = addonEntrypoint.getResource(addonID + ".png");
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
