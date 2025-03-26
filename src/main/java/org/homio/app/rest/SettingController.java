package org.homio.app.rest;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.SettingPluginOptionsRemovable;
import org.homio.api.setting.SettingPluginPackageInstall;
import org.homio.api.setting.SettingType;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderContainerSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.api.util.Lang;
import org.homio.app.manager.AddonService;
import org.homio.app.manager.common.ContextImpl;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.app.manager.common.impl.ContextUIImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.repository.SettingRepository;
import org.homio.app.spring.ContextRefreshed;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.homio.api.util.Constants.ROLE_ADMIN_AUTHORIZE;
import static org.homio.app.model.entity.SettingEntity.getKey;
import static org.homio.app.repository.SettingRepository.fulfillEntityFromPlugin;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(value = "/rest/setting", produces = APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SettingController implements ContextRefreshed {

  private final AddonService addonService;
  private final ContextImpl context;
  private Map<String, Set<String>> settingToPages;
  private Set<SettingEntity> descriptionSettings;
  private Map<Class<? extends SettingPlugin<?>>, SettingEntity> transientSettings;

  @Override
  public void onContextRefresh(Context context) {
    this.transientSettings = new HashMap<>();
    for (SettingPlugin<?> settingPlugin : ContextSettingImpl.settingPluginsByPluginKey.values()) {
      if (settingPlugin.transientState()) {
        SettingEntity entity = new SettingEntity();
        entity.setEntityID(getKey(settingPlugin));
        this.transientSettings.put((Class<? extends SettingPlugin<?>>) settingPlugin.getClass(), entity);
      }
      if (settingPlugin instanceof ConsoleSettingPlugin
          && !settingPlugin.getClass().getSimpleName().startsWith("Console")) {
        throw new ServerException("Console plugin class <" + settingPlugin.getClass().getName() + "> must starts with name 'Console'");
      }
    }
  }

  @GetMapping("/{entityID}/options")
  public Collection<OptionModel> loadSettingAvailableValues(@PathVariable("entityID") String entityID,
                                                            @RequestParam(value = "param0", required = false) String param0) {
    try {
      SettingPluginOptions<?> settingPlugin = (SettingPluginOptions<?>) ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
      return SettingRepository.getOptions(settingPlugin, context, new JSONObject().put("param0", param0));
    } catch (Exception ex) {
      return List.of();
    }
  }

  @GetMapping("/{entityID}/package/all")
  public SettingPluginPackageInstall.PackageContext loadAllPackages(@PathVariable("entityID") String entityID) throws Exception {
    SettingPlugin<?> settingPlugin = ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      SettingPluginPackageInstall.PackageContext packageContext =
        ((SettingPluginPackageInstall) settingPlugin).allPackages(context);
      for (Map.Entry<String, Boolean> entry : addonService.getPackagesInProgress().entrySet()) {
        SettingPluginPackageInstall.PackageModel singlePackage =
          packageContext.getPackages().stream()
            .filter(p -> p.getName().equals(entry.getKey()))
            .findFirst()
            .orElse(null);
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

  @GetMapping("/{entityID}/package")
  public SettingPluginPackageInstall.PackageContext loadInstalledPackages(@PathVariable("entityID") String entityID) throws Exception {
    SettingPlugin<?> settingPlugin = ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      return ((SettingPluginPackageInstall) settingPlugin).installedPackages(context);
    }
    return null;
  }

  @DeleteMapping("/{entityID}/package")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public void unInstallPackage(@PathVariable("entityID") String entityID, @RequestBody SettingPluginPackageInstall.PackageRequest packageRequest) {
    SettingPlugin<?> settingPlugin = ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      addonService.unInstallPackage((SettingPluginPackageInstall) settingPlugin, packageRequest);
    }
  }

  @PostMapping("/{entityID}/package")
  @PreAuthorize(ROLE_ADMIN_AUTHORIZE)
  public void installPackage(@PathVariable("entityID") String entityID, @RequestBody SettingPluginPackageInstall.PackageRequest packageRequest) {
    SettingPlugin<?> settingPlugin = ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginPackageInstall) {
      addonService.installPackage((SettingPluginPackageInstall) settingPlugin, packageRequest);
    }
  }

  @SneakyThrows
  @PostMapping(value = "/{entityID}", consumes = "text/plain")
  public <T> void updateSetting(@PathVariable("entityID") String entityID, @RequestBody(required = false) String value) {
    SettingPlugin<?> settingPlugin = ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin != null) {
      settingPlugin.assertUserAccess(context, context.user().getLoggedInUser());
      context.setting().setValueRaw((Class<? extends SettingPlugin<T>>) settingPlugin.getClass(), value, false);
    } else {
      for (SettingPlugin<?> plugin : ContextSettingImpl.settingPluginsByPluginKey.values()) {
        if (plugin instanceof DynamicConsoleHeaderContainerSettingPlugin dynamicPlugin) {
          AtomicBoolean found = new AtomicBoolean();
          var consumer = new DynamicConsoleHeaderContainerSettingPlugin.DynamicSettingConsumer() {
            @Override
            public <T> void addDynamicSetting(@NotNull DynamicConsoleHeaderSettingPlugin<T> dynamicSetting) {
              if (getKey(dynamicSetting).equals(entityID)) {
                found.set(true);
                dynamicPlugin.setValue(context, dynamicSetting, value);
              }
            }
          };
          dynamicPlugin.assembleDynamicSettings(context, consumer);
          if (found.get()) {
            return;
          }
        }
      }
      throw new ServerException("Setting plugin not found for entityID: " + entityID);
    }
  }

  @DeleteMapping(value = "/{entityID}", consumes = "text/plain")
  public void removeSettingValue(@PathVariable("entityID") String entityID, @RequestBody String value) throws Exception {
    SettingPlugin<?> settingPlugin = ContextSettingImpl.settingPluginsByPluginKey.get(entityID);
    if (settingPlugin instanceof SettingPluginOptionsRemovable) {
      ((SettingPluginOptionsRemovable<?>) settingPlugin).removeOption(context, value);
    }
  }

  @GetMapping("/name")
  public List<OptionModel> getSettingNames() {
    return context.toOptionModels(context.db().findAll(SettingEntity.class));
  }

  public Set<SettingEntity> getSettings() {
    List<SettingEntity> settings = context.db().findAll(SettingEntity.class);
    assembleTransientSettings(settings);
    for (SettingEntity setting : settings) {
      fulfillEntityFromPlugin(setting, context, null);
    }

    boolean isAdmin = context.user().isAdminLoggedUser();

    if (settingToPages == null) {
      settingToPages = new HashMap<>();
      this.updateSettingToPages(settings);
    }

    for (Iterator<SettingEntity> iterator = settings.iterator(); iterator.hasNext(); ) {
      SettingEntity settingEntity = iterator.next();
      SettingPlugin<?> plugin = ContextSettingImpl.settingPluginsByPluginKey.get(
        settingEntity.getEntityID());
      if (plugin != null) {

        // fulfill pages
        if (settingToPages.containsKey(settingEntity.getEntityID())) {
          settingEntity.setPages(settingToPages.get(settingEntity.getEntityID()));
        }

        // hide secured values if requires
        if (plugin.isSecuredValue() && !isAdmin) {
          settingEntity.setValue("********");
        }
        settingEntity.setVisible(plugin.isVisible(context));
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
    return new HashSet<>(settings);
  }

  private void assembleTransientSettings(List<SettingEntity> settings) {
    for (Map.Entry<Class<? extends SettingPlugin<?>>, SettingEntity> entry :
      transientSettings.entrySet()) {
      SettingEntity settingEntity = entry.getValue();
      settingEntity.setContext(context);
      settingEntity.setValue(context.setting().getRawValue((Class) entry.getKey()));
      settings.add(settingEntity);
    }
  }

  private void updateSettingToPages(List<SettingEntity> settings) {
    // fulfill console pages
    for (SettingEntity settingEntity : settings) {
      SettingPlugin<?> plugin = ContextSettingImpl.settingPluginsByPluginKey.get(settingEntity.getEntityID());
      if (plugin instanceof ConsoleSettingPlugin) {
        for (Map.Entry<String, ConsolePlugin<?>> entry :
          ContextUIImpl.consolePluginsMap.entrySet()) {
          if (((ConsoleSettingPlugin<?>) plugin)
            .acceptConsolePluginPage(entry.getValue())) {
            settingToPages
              .computeIfAbsent(settingEntity.getEntityID(), s -> new HashSet<>())
              .add(Objects.toString(entry.getValue().getParentTab(), entry.getKey()));
          }
        }
      }
    }
  }

  /**
   * add setting descriptions
   */
  private void updateSettingDescription(List<SettingEntity> settings) {
    Set<String> addonSettings =
      settings.stream().map(e -> {
          SettingPlugin<?> plugin = ContextSettingImpl.settingPluginsByPluginKey.get(e.getEntityID());
          return SettingRepository.getSettingAddonName(context, plugin.getClass());
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    for (AddonEntrypoint addonEntrypoint : addonService.getAddons()) {
      if (addonSettings.contains(addonEntrypoint.getAddonID())) {
        // find if description exists inside lang.json
        String descriptionKey = addonEntrypoint.getAddonID() + ".setting.description";
        String description = Lang.findPathText(descriptionKey);
        if (description != null) {
          SettingEntity settingEntity = new SettingEntity() {
            @Override
            public String getAddonID() {
              return addonEntrypoint.getAddonID();
            }
          };
          settingEntity
            .setSettingType(SettingType.Description)
            .setVisible(true)
            .setValue(descriptionKey)
            .setOrder(1);
          settingEntity.setEntityID(SettingEntity.PREFIX + addonEntrypoint.getAddonID() + "_Description");
          this.descriptionSettings.add(settingEntity);
        }
      }
    }
  }
}
