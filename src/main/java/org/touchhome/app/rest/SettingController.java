package org.touchhome.app.rest;

import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static org.touchhome.bundle.api.util.Constants.ADMIN_ROLE;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.touchhome.app.manager.BundleService;
import org.touchhome.app.manager.common.EntityContextImpl;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.manager.common.impl.EntityContextUIImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.repository.SettingRepository;
import org.touchhome.app.spring.ContextRefreshed;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.entity.UserEntity;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.setting.SettingPlugin;
import org.touchhome.bundle.api.setting.SettingPluginOptions;
import org.touchhome.bundle.api.setting.SettingPluginOptionsRemovable;
import org.touchhome.bundle.api.setting.SettingPluginPackageInstall;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;
import org.touchhome.bundle.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;
import org.touchhome.bundle.api.ui.field.UIFieldType;
import org.touchhome.common.exception.ServerException;
import org.touchhome.common.util.Lang;

@RestController
@RequestMapping(value = "/rest/setting", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SettingController implements ContextRefreshed {

  private final BundleService bundleService;

  private Map<String, Set<String>> settingToPages;
  private Set<SettingEntity> descriptionSettings;

  private final EntityContextImpl entityContext;
  private Map<Class<? extends SettingPlugin<?>>, SettingEntity> transientSettings;
  // true - installing, false removing
  private final Map<String, Boolean> packagesInProgress = new ConcurrentHashMap<>();

  @Override
  public void onContextRefresh() {
    this.transientSettings = new HashMap<>();
    for (SettingPlugin<?> settingPlugin : EntityContextSettingImpl.settingPluginsByPluginKey.values()) {
      if (settingPlugin.transientState()) {
        this.transientSettings.put((Class<? extends SettingPlugin<?>>) settingPlugin.getClass(),
            SettingRepository.createSettingEntityFromPlugin(settingPlugin, new SettingEntity(), entityContext));
      }
      if (settingPlugin instanceof ConsoleSettingPlugin &&
          !settingPlugin.getClass().getSimpleName().startsWith("Console")) {
        throw new ServerException(
            "Console plugin class <" + settingPlugin.getClass().getName() + "> must starts with name 'Console'");
      }
    }
  }

  @GetMapping("/{entityID}/options")
  public Collection<OptionModel> loadSettingAvailableValues(@PathVariable("entityID") String entityID,
      @RequestParam(value = "param0", required = false) String param0) {
    SettingPluginOptions<?> settingPlugin =
        (SettingPluginOptions<?>) EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    return SettingRepository.getOptions(settingPlugin, entityContext, new JSONObject().put("param0", param0));
  }

  @GetMapping("/{entityID}/packages/all")
  public SettingPluginPackageInstall.PackageContext loadAllPackages(@PathVariable("entityID") String entityID)
      throws Exception {
    SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      SettingPluginPackageInstall.PackageContext packageContext =
          ((SettingPluginPackageInstall) settingPlugin).allPackages(entityContext);
      for (Map.Entry<String, Boolean> entry : packagesInProgress.entrySet()) {
        SettingPluginPackageInstall.PackageModel singlePackage = packageContext.getPackages().stream()
            .filter(p -> p.getName().equals(entry.getKey())).findFirst().orElse(null);
        if (singlePackage != null) {
          if (entry.getValue()) {
            singlePackage.setInstalling(true);
          } else {
            singlePackage.setRemoving(true);
          }
        }
      }
      return packageContext;
    }
    return null;
  }

  @GetMapping("/{entityID}/packages")
  public SettingPluginPackageInstall.PackageContext loadInstalledPackages(@PathVariable("entityID") String entityID)
      throws Exception {
    SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      return ((SettingPluginPackageInstall) settingPlugin).installedPackages(entityContext);
    }
    return null;
  }

  @DeleteMapping("/{entityID}/packages")
  @Secured(ADMIN_ROLE)
  public void unInstallPackage(@PathVariable("entityID") String entityID,
      @RequestBody SettingPluginPackageInstall.PackageRequest packageRequest) {
    SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      if (!packagesInProgress.containsKey(packageRequest.getName())) {
        packagesInProgress.put(packageRequest.getName(), false);
        entityContext.ui().runWithProgress("Uninstall " + packageRequest.getName() + "/" + packageRequest.getVersion(),
            false, progressBar -> ((SettingPluginPackageInstall) settingPlugin).unInstallPackage(entityContext,
                packageRequest, progressBar),
            ex -> packagesInProgress.remove(packageRequest.getName()));
      }
    }
  }

  @PostMapping("/{entityID}/packages")
  @Secured(ADMIN_ROLE)
  public void installPackage(@PathVariable("entityID") String entityID,
      @RequestBody SettingPluginPackageInstall.PackageRequest packageRequest) {
    SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      if (!packagesInProgress.containsKey(packageRequest.getName())) {
        packagesInProgress.put(packageRequest.getName(), true);
        entityContext.ui().runWithProgress("Install " + packageRequest.getName() + "/" + packageRequest.getVersion(),
            false,
            progressBar -> ((SettingPluginPackageInstall) settingPlugin).installPackage(entityContext, packageRequest,
                progressBar),
            ex -> packagesInProgress.remove(packageRequest.getName()));
      }
    }
  }

  @Secured(ADMIN_ROLE)
  @PostMapping(value = "/{entityID}", consumes = "text/plain")
  public <T> void updateSetting(@PathVariable("entityID") String entityID, @RequestBody(required = false) String value) {
    SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin != null) {
      entityContext.setting().setValueRaw((Class<? extends SettingPlugin<T>>) settingPlugin.getClass(), value, false);
    }
  }

  @Secured(ADMIN_ROLE)
  @DeleteMapping(value = "/{entityID}", consumes = "text/plain")
  public void removeSettingValue(@PathVariable("entityID") String entityID, @RequestBody String value) throws Exception {
    SettingPlugin<?> settingPlugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginOptionsRemovable) {
      ((SettingPluginOptionsRemovable<?>) settingPlugin).removeOption(entityContext, value);
    }
  }

  @GetMapping("/name")
  public List<OptionModel> getSettingNames() {
    return OptionModel.entityList(entityContext.findAll(SettingEntity.class));
  }

  public List<SettingEntity> getSettings() {
    List<SettingEntity> settings = entityContext.findAll(SettingEntity.class);
    assembleTransientSettings(settings);

    UserEntity userEntity = entityContext.getUser(true);

    if (settingToPages == null) {
      settingToPages = new HashMap<>();
      this.updateSettingToPages(settings);
    }

    for (Iterator<SettingEntity> iterator = settings.iterator(); iterator.hasNext(); ) {
      SettingEntity settingEntity = iterator.next();
      SettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
      if (plugin != null) {

        // fulfill pages
        if (settingToPages.containsKey(settingEntity.getEntityID())) {
          settingEntity.setPages(settingToPages.get(settingEntity.getEntityID()));
        }

        // hide secured values if requires
        if (plugin.isSecuredValue() && !userEntity.isAdmin()) {
          settingEntity.setValue("********");
        }
        settingEntity.setVisible(plugin.isVisible(entityContext));
      } else {
        iterator.remove();
      }
    }

    if (descriptionSettings == null) {
      descriptionSettings = new HashSet<>();
      updateSettingDescription(settings);
    }

    settings.addAll(descriptionSettings);
    Collections.sort(settings);
    return settings;
  }

  private void assembleTransientSettings(List<SettingEntity> settings) {
    for (Map.Entry<Class<? extends SettingPlugin<?>>, SettingEntity> entry : transientSettings.entrySet()) {
      SettingEntity settingEntity = entry.getValue();
      settingEntity.setValue(entityContext.setting().getRawValue((Class) entry.getKey()));
      SettingRepository.fulfillEntityFromPlugin(settingEntity, entityContext, null);
      if (DynamicConsoleHeaderContainerSettingPlugin.class.isAssignableFrom(entry.getKey())) {
        settingEntity.setSettingTypeRaw("Container");
        List<SettingEntity> options = EntityContextSettingImpl.dynamicHeaderSettings.get(entry.getKey());
        settingEntity.getParameters().put("dynamicOptions", options);
      } else if (SettingPluginPackageInstall.class.isAssignableFrom(entry.getKey())) {
        settingEntity.setSettingTypeRaw("BundleInstaller");
      }
      for (String key : settingEntity.getJsonData().keySet()) {
        settingEntity.getParameters().put(key, settingEntity.getJsonData().get(key));
      }

      settings.add(settingEntity);
    }
  }

  private void updateSettingToPages(List<SettingEntity> settings) {
    // fulfill console pages
    for (SettingEntity settingEntity : settings) {
      SettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
      if (plugin instanceof ConsoleSettingPlugin) {
        for (Map.Entry<String, ConsolePlugin<?>> entry : EntityContextUIImpl.consolePluginsMap.entrySet()) {
          if (((ConsoleSettingPlugin<?>) plugin).acceptConsolePluginPage(entry.getValue())) {
            settingToPages.computeIfAbsent(settingEntity.getEntityID(),
                    s -> new HashSet<>())
                .add(StringUtils.defaultString(entry.getValue().getParentTab(), entry.getKey()));
          }
        }
      }
    }
  }

  /**
   * add setting descriptions
   */
  private void updateSettingDescription(List<SettingEntity> settings) {
    Set<String> bundleSettings = settings.stream().map(e -> {
      SettingPlugin<?> plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(e.getEntityID());
      return SettingRepository.getSettingBundleName(entityContext, plugin.getClass());
    }).filter(Objects::nonNull).collect(Collectors.toSet());

    for (BundleEntryPoint bundleEntrypoint : bundleService.getBundles()) {
      if (bundleSettings.contains(bundleEntrypoint.getBundleId())) {
        // find if description exists inside lang.json
        String descriptionKey = bundleEntrypoint.getBundleId() + ".setting.description";
        String description = Lang.findPathText(descriptionKey);
        if (description != null) {
          SettingEntity settingEntity = new SettingEntity() {
            @Override
            public String getBundle() {
              return bundleEntrypoint.getBundleId();
            }
          };
          settingEntity
              .setSettingType(UIFieldType.Description)
              .setVisible(true)
              .setEntityID(SettingEntity.PREFIX + bundleEntrypoint.getBundleId() + "_Description")
              .setValue(descriptionKey).setOrder(1);
          this.descriptionSettings.add(settingEntity);
        }
      }
    }
  }
}
