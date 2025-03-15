package org.homio.app.repository;

import org.homio.api.AddonEntrypoint;
import org.homio.api.Context;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.SettingPluginOptionsEnumMulti;
import org.homio.api.setting.SettingPluginOptionsRemovable;
import org.homio.api.setting.SettingPluginToggle;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.manager.common.impl.ContextSettingImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.app.spring.ContextRefreshed;
import org.json.JSONObject;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.homio.api.AddonEntrypoint.ADDON_PREFIX;
import static org.homio.app.model.entity.SettingEntity.getKey;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity>
  implements ContextRefreshed {

  private static final Map<String, String> settingToAddonMap = new HashMap<>();

  public SettingRepository() {
    super(SettingEntity.class);
  }

  public static Collection<OptionModel> getOptions(SettingPluginOptions<?> plugin, Context context, JSONObject param) {
    Collection<OptionModel> options = plugin.getOptions(context, param);
    if (plugin instanceof SettingPluginOptionsRemovable) {
      for (OptionModel option : options) {
        if (((SettingPluginOptionsRemovable<?>) plugin).removableOption(option)) {
          option.json(json -> json.put("removable", true));
        }
      }
    }
    return options;
  }

  public static void fulfillEntityFromPlugin(SettingEntity entity, Context context, SettingPlugin<?> plugin) {
    if (plugin == null) {
      plugin = ContextSettingImpl.settingPluginsByPluginKey.get(entity.getEntityID());
    }
    if (plugin != null) {
      Class<? extends BaseEntity> availableForEntity = plugin.availableForEntity();
      if (availableForEntity != null) {
        entity.setPages(Collections.singleton(availableForEntity.getSimpleName()));
      }
      entity.setDefaultValue(plugin.getDefaultValue());
      entity.setOrder(plugin.order());
      entity.setAdvanced(plugin.isAdvanced());
      entity.setStorable(plugin.isStorable());
      entity.setIcon(plugin.getIcon());
      if (plugin instanceof SettingPluginToggle) {
        entity.setToggleIcon(((SettingPluginToggle) plugin).getToggleIcon());
      }
      if (plugin instanceof SettingPluginButton) {
        entity.setValue(((SettingPluginButton) plugin).getText(context));
        entity.setPrimary(((SettingPluginButton) plugin).isPrimary());
      }
      entity.setSettingType(plugin.getSettingType());
      entity.setReverted(plugin.isReverted() ? true : null);
      entity.setParameters(plugin.getParameters(context, entity.getValue()));
      entity.setDisabled(plugin.isDisabled(context) ? true : null);
      entity.setRequired(plugin.isRequired());
      if (plugin instanceof SettingPluginOptions) {
        entity.setLazyLoad(((SettingPluginOptions<?>) plugin).lazyLoad());
      }
      if (plugin instanceof SettingPluginOptionsEnumMulti<?>) {
        entity.setMultiSelect(true);
      }
      if (entity.isStorable()) {
        boolean isSelectBox = entity.getSettingType().equals(UIFieldType.SelectBoxButton.name())
                              || entity.getSettingType().equals(UIFieldType.SelectBox.name());
        if (isSelectBox && !entity.isLazyLoad()) {
          entity.setAvailableValues(SettingRepository.getOptions((SettingPluginOptions<?>) plugin, context, null));
        }
      }

      if (plugin instanceof CoreSettingPlugin<?> settingPlugin) {
        entity.setGroupKey(settingPlugin.getGroupKey().name());
        entity.setSubGroupKey(settingPlugin.getSubGroupKey());
      }

      if (plugin instanceof DynamicConsoleHeaderSettingPlugin) {
        entity.setName(((DynamicConsoleHeaderSettingPlugin<?>) plugin).getTitle());
      }

      if (plugin instanceof ConsoleSettingPlugin) {
        String[] pages = ((ConsoleSettingPlugin<?>) plugin).pages();
        if (pages != null && pages.length > 0) {
          entity.setPages(new HashSet<>(Arrays.asList(pages)));
        }
        ConsolePlugin.RenderType[] renderTypes =
          ((ConsoleSettingPlugin<?>) plugin).renderTypes();
        if (renderTypes != null && renderTypes.length > 0) {
          entity.setRenderTypes(new HashSet<>(Arrays.asList(renderTypes)));
        }
      }
    }
  }

  /**
   * Search addonID for setting.
   */
  public static String getSettingAddonName(Context context, Class<? extends SettingPlugin> settingPluginClass) {
    String name = settingPluginClass.getName();
    return settingToAddonMap.computeIfAbsent(name, key -> {
      if (name.startsWith(ADDON_PREFIX)) {
        String pathName = name.substring(0, ADDON_PREFIX.length() + name.substring(ADDON_PREFIX.length()).indexOf('.'));
        AddonEntrypoint addonEntrypoint = context.getBeansOfType(AddonEntrypoint.class).stream()
          .filter(b -> b.getClass().getName().startsWith(pathName)).findAny().orElse(null);
        if (addonEntrypoint == null) {
          throw new ServerException("Unable find addon entry-point for setting: " + key);
        }
        return addonEntrypoint.getAddonID();
      }
      return null;
    });
  }

  @Override
  public void onContextRefresh(Context context) {
    for (SettingPlugin settingPlugin : ContextSettingImpl.settingPluginsBy(p -> !p.transientState())) {
      SettingEntity settingEntity = context.db().get(getKey(settingPlugin));
      if (settingEntity == null) {
        SettingEntity entity = new SettingEntity();
        entity.setEntityID(getKey(settingPlugin));
        context.db().save(entity);
      }
    }
  }
}
