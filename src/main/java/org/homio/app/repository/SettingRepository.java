package org.homio.app.repository;

import static org.homio.app.model.entity.SettingEntity.getKey;
import static org.homio.bundle.api.BundleEntrypoint.BUNDLE_PREFIX;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.homio.app.manager.common.impl.EntityContextSettingImpl;
import org.homio.app.model.entity.SettingEntity;
import org.homio.app.setting.CoreSettingPlugin;
import org.homio.app.spring.ContextRefreshed;
import org.homio.bundle.api.BundleEntrypoint;
import org.homio.bundle.api.EntityContext;
import org.homio.bundle.api.console.ConsolePlugin;
import org.homio.bundle.api.exception.ServerException;
import org.homio.bundle.api.model.OptionModel;
import org.homio.bundle.api.repository.AbstractRepository;
import org.homio.bundle.api.setting.SettingPlugin;
import org.homio.bundle.api.setting.SettingPluginOptions;
import org.homio.bundle.api.setting.SettingPluginOptionsRemovable;
import org.homio.bundle.api.setting.SettingPluginToggle;
import org.homio.bundle.api.setting.console.ConsoleSettingPlugin;
import org.homio.bundle.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;
import org.homio.bundle.api.ui.field.UIFieldType;
import org.json.JSONObject;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity>
    implements ContextRefreshed {

    private static final Map<String, String> settingToBundleMap = new HashMap<>();
    private final EntityContext entityContext;

    public SettingRepository(EntityContext entityContext) {
        super(SettingEntity.class);
        this.entityContext = entityContext;
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
            if (plugin.availableForEntity() != null) {
                entity.setPages(Collections.singleton(plugin.availableForEntity().getSimpleName()));
            }
            entity.setDefaultValue(plugin.getDefaultValue());
            entity.setOrder(plugin.order());
            entity.setAdvanced(plugin.isAdvanced());
            entity.setStorable(plugin.isStorable());
            entity.setColor(plugin.getIconColor());
            entity.setIcon(plugin.getIcon());
            if (plugin instanceof SettingPluginToggle) {
                entity.setToggleIcon(((SettingPluginToggle) plugin).getToggleIcon());
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

            if (plugin instanceof CoreSettingPlugin) {
                CoreSettingPlugin<?> settingPlugin = (CoreSettingPlugin<?>) plugin;
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
     * Search bundleId for setting.
     */
    public static String getSettingBundleName(EntityContext entityContext, Class<? extends SettingPlugin> settingPluginClass) {
        String name = settingPluginClass.getName();
        return settingToBundleMap.computeIfAbsent(name, key -> {
            if (name.startsWith(BUNDLE_PREFIX + "api.")) {
                return "api";
            }
            if (name.startsWith(BUNDLE_PREFIX)) {
                String pathName = name.substring(0, BUNDLE_PREFIX.length() + name.substring(BUNDLE_PREFIX.length()).indexOf('.'));
                BundleEntrypoint bundleEntrypoint = entityContext.getBeansOfType(BundleEntrypoint.class).stream()
                                                                 .filter(b -> b.getClass().getName().startsWith(pathName)).findAny().orElse(null);
                if (bundleEntrypoint == null) {
                    throw new ServerException("Unable find bundle entry-point for setting: " + key);
                }
                return bundleEntrypoint.getBundleId();
            }
            return null;
        });
    }

    @Override
    @Transactional
    public void onContextRefresh() {
        for (SettingPlugin settingPlugin : EntityContextSettingImpl.settingPluginsBy(p -> !p.transientState())) {
            SettingEntity settingEntity = entityContext.getEntity(getKey(settingPlugin));
            if (settingEntity == null) {
                entityContext.save(new SettingEntity().setEntityID(getKey(settingPlugin)));
            }
        }
    }
}