package org.touchhome.app.setting.system;

import org.json.JSONObject;
import org.touchhome.app.config.TouchHomeProperties;
import org.touchhome.app.extloader.BundleContext;
import org.touchhome.app.extloader.BundleContextService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.app.utils.Curl;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.ProgressBar;
import org.touchhome.bundle.api.setting.SettingPluginPackageInstall;

import java.util.List;
import java.util.stream.Collectors;

public class SystemBundleLibraryManagerSetting implements SettingPluginPackageInstall, CoreSettingPlugin<JSONObject> {

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
    public PackageContext allPackages(EntityContext entityContext) {
        String url = entityContext.getBean(TouchHomeProperties.class).getServerSiteURL() + "/packages";
        PackageContext packageContext = new PackageContext();
        try {
            packageContext.setPackages(Curl.get(url, List.class));
        } catch (Exception ex) {
            packageContext.setError(ex.getMessage());
        }
        return packageContext;
    }

    @Override
    public PackageContext installedPackages(EntityContext entityContext) {
        return new PackageContext(null, ((EntityContextImpl) entityContext).getBundles().values().stream()
                .filter(b -> b.getBundleContext() != null)
                .map(b -> build(b.getBundleContext()))
                .collect(Collectors.toSet()));

    }

    @Override
    public void installPackage(EntityContext entityContext, PackageRequest packageRequest, ProgressBar progressBar) {
        entityContext.getBean(BundleContextService.class).installBundle(packageRequest.getName(),
                packageRequest.getUrl(),
                packageRequest.getVersion());
    }

    @Override
    public void unInstallPackage(EntityContext entityContext, PackageRequest packageRequest, ProgressBar progressBar) {
        entityContext.getBean(BundleContextService.class).uninstallBundle(packageRequest.getName());
    }

    private PackageModel build(BundleContext bundleContext) {
        return new PackageModel()
                .setName(bundleContext.getBundleID())
                .setTitle(bundleContext.getPomFile().getName())
                .setVersion(bundleContext.getVersion())
                .setReadme(bundleContext.getPomFile().getDescription());
    }
}
