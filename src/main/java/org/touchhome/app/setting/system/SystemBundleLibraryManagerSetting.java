package org.touchhome.app.setting.system;

import lombok.SneakyThrows;
import org.json.JSONObject;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleContextService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.app.utils.Curl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.setting.BundlePackageInstallSettingPlugin;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class SystemBundleLibraryManagerSetting implements BundlePackageInstallSettingPlugin, SettingPlugin<JSONObject> {

    @Override
    public int order() {
        return 1000;
    }

    @Override
    public GroupKey getGroupKey() {
        return GroupKey.system;
    }

    @Override
    public String getIconColor() {
        return "#E8BF37";
    }

    @Override
    public Collection<PackageEntity> installedBundles(EntityContext entityContext) {
        return ((EntityContextImpl) entityContext).getBundles().values().stream()
                .filter(b -> b.getBundleContext() != null)
                .map(b -> build(b.getBundleContext()))
                .collect(Collectors.toSet());
    }

    @SneakyThrows
    @Override
    public Collection<PackageEntity> allBundles(EntityContext entityContext) {
        String url = entityContext.getBean(TouchHomeProperties.class).getServerSiteURL() + "/packages";
        return Curl.get(url, List.class);
    }

    @Override
    public void install(EntityContext entityContext, PackageRequest packageRequest, String progressKey) {
        entityContext.getBean(BundleContextService.class).installBundle(packageRequest.getName(),
                packageRequest.getUrl(),
                packageRequest.getVersion());
    }

    @Override
    public void unInstall(EntityContext entityContext, PackageRequest packageRequest, String progressKey) {
        entityContext.getBean(BundleContextService.class).uninstallBundle(packageRequest.getName());
    }

    private BundlePackageInstallSettingPlugin.PackageEntity build(BundleContext bundleContext) {
        return new BundlePackageInstallSettingPlugin.PackageEntity()
                .setName(bundleContext.getBundleID())
                .setTitle(bundleContext.getPomFile().getName())
                .setVersion(bundleContext.getVersion())
                .setReadme(bundleContext.getPomFile().getDescription());
    }
}
