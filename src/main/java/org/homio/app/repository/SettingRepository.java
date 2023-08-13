package org.homio.app.repository;

import static org.homio.api.AddonEntrypoint.ADDON_PREFIX;
import static org.homio.app.model.entity.SettingEntity.getKey;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.homio.api.AddonEntrypoint;
import org.homio.api.EntityContext;
import org.homio.api.console.ConsolePlugin;
import org.homio.api.entity.BaseEntity;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.setting.SettingPlugin;
import org.homio.api.setting.SettingPluginButton;
import org.homio.api.setting.SettingPluginOptions;
import org.homio.api.setting.SettingPluginOptionsRemovable;
import org.homio.api.setting.SettingPluginToggle;
import org.homio.api.setting.console.ConsoleSettingPlugin;
import org.homio.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.api.ui.field.UIFieldType;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.app.spring.ContextRefreshed;
import org.json.JSONObject;
import org.springframework.stereotype.Repository;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity>
    implements ContextRefreshed {

    private static final Map<String, String> settingToAddonMap = new HashMap<>();

    public SettingRepository() {
        super(SettingEntity.class);
    }

    public static Collection<OptionModel> getOptions(SettingPluginOptions<?> plugin, EntityContext entityContext, JSONObject param) {
        Collection<OptionModel> options = plugin.getOptions(entityContext, param);
        if (plugin instanceof SettingPluginOptionsRemovable) {
            for (OptionModel option : options) {
                if (((SettingPluginOptionsRemovable<?>) plugin).removableOption(option)) {
                    option.json(json -> json.put("removable", true));
                }
            }
        }
        return options;
    }

    public static void fulfillEntityFromPlugin(SettingEntity entity, EntityContext entityContext, SettingPlugin<?> plugin) {
        if (plugin == null) {
            plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entity.getEntityID());
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
            Icon icon = plugin.getIcon();
            entity.setColor(icon == null ? null : icon.getColor());
            entity.setIcon(icon == null ? null : icon.getIcon());
            if (plugin instanceof SettingPluginToggle) {
                entity.setToggleIcon(((SettingPluginToggle) plugin).getToggleIcon().getIcon());
            }
            if (plugin instanceof SettingPluginButton) {
                entity.setValue(((SettingPluginButton) plugin).getText());
                entity.setPrimary(((SettingPluginButton) plugin).isPrimary());
            }
            entity.setSettingType(plugin.getSettingType());
            entity.setReverted(plugin.isReverted() ? true : null);
            entity.setParameters(plugin.getParameters(entityContext, entity.getValue()));
            entity.setDisabled(plugin.isDisabled(entityContext) ? true : null);
            entity.setRequired(plugin.isRequired());
            if (plugin instanceof SettingPluginOptions) {
                entity.setLazyLoad(((SettingPluginOptions<?>) plugin).lazyLoad());
            }
            if (entity.isStorable()) {
                if (entity.getSettingType().equals(UIFieldType.SelectBoxButton.name())
                    || entity.getSettingType().equals(UIFieldType.SelectBox.name())) {
                    entity.setAvailableValues(SettingRepository.getOptions((SettingPluginOptions<?>) plugin, entityContext, null));
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
    public static String getSettingAddonName(EntityContext entityContext, Class<? extends SettingPlugin> settingPluginClass) {
        String name = settingPluginClass.getName();
        return settingToAddonMap.computeIfAbsent(name, key -> {
            if (name.startsWith(ADDON_PREFIX)) {
                String pathName = name.substring(0, ADDON_PREFIX.length() + name.substring(ADDON_PREFIX.length()).indexOf('.'));
                AddonEntrypoint addonEntrypoint = entityContext.getBeansOfType(AddonEntrypoint.class).stream()
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
    public void onContextRefresh(EntityContext entityContext) {
        for (SettingPlugin settingPlugin : EntityContextSettingImpl.settingPluginsBy(p -> !p.transientState())) {
            SettingEntity settingEntity = entityContext.getEntity(getKey(settingPlugin));
            if (settingEntity == null) {
                SettingEntity entity = new SettingEntity();
                entity.setEntityID(getKey(settingPlugin));
                entityContext.save(entity);
            }
        }
    }
}
