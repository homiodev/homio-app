package org.touchhome.app.repository;

import lombok.SneakyThrows;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.setting.CoreSettingPlugin;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.exception.ServerException;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.setting.*;
import org.touchhome.bundle.api.setting.console.ConsoleSettingPlugin;
import org.touchhome.bundle.api.setting.console.header.dynamic.DynamicConsoleHeaderSettingPlugin;

import java.util.*;

import static org.touchhome.app.model.entity.SettingEntity.getKey;
import static org.touchhome.bundle.api.BundleEntryPoint.BUNDLE_PREFIX;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity> {

    private final static Map<String, String> settingToBundleMap = new HashMap<>();
    private final EntityContext entityContext;

    public SettingRepository(EntityContext entityContext) {
        super(SettingEntity.class);
        this.entityContext = entityContext;
    }

    public static SettingEntity createSettingEntityFromPlugin(SettingPlugin<?> settingPlugin, SettingEntity settingEntity, EntityContext entityContext) {
        settingEntity.computeEntityID(() -> getKey(settingPlugin));
        if (settingPlugin.transientState()) {
            settingEntity.setEntityID(getKey(settingPlugin));
            fulfillEntityFromPlugin(settingEntity, entityContext, settingPlugin);
        }
        return settingEntity;
    }

    public static Collection<OptionModel> getOptions(SettingPluginOptions<?> plugin, EntityContext entityContext) {
        Collection<OptionModel> options = plugin.getOptions(entityContext);
        if (plugin instanceof SettingPluginOptionsRemovable) {
            for (OptionModel option : options) {
                if (((SettingPluginOptionsRemovable<?>) plugin).removableOption(option)) {
                    option.getJson().put("removable", true);
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
            entity.setBundle(getSettingBundleName(entityContext, plugin.getClass()));
            entity.setDefaultValue(plugin.getDefaultValue());
            entity.setOrder(plugin.order());
            entity.setAdvanced(plugin.isAdvanced());
            entity.setStorable(plugin.isStorable());
            entity.setColor(plugin.getIconColor());
            entity.setIcon(getIcon(plugin));
            if (plugin instanceof SettingPluginToggle) {
                entity.setToggleIcon(((SettingPluginToggle) plugin).getToggleIcon());
            }
            entity.setSettingType(plugin.getSettingType());
            entity.setReverted(plugin.isReverted() ? true : null);
            entity.setParameters(plugin.getParameters(entityContext, entity.getValue()));
            entity.setDisabled(plugin.isDisabled(entityContext) ? true : null);
            entity.setRequired(plugin.isRequired());
            if (entity.isStorable()) {
                if (entity.getSettingType().equals(SettingPlugin.SettingType.SelectBoxButton.name())
                        || entity.getSettingType().equals(SettingPlugin.SettingType.SelectBox.name())) {
                    entity.setAvailableValues(SettingRepository.getOptions((SettingPluginOptions<?>) plugin, entityContext));
                }
            }

            if (plugin instanceof CoreSettingPlugin) {
                CoreSettingPlugin<?> settingPlugin = (CoreSettingPlugin<?>) plugin;
                entity.setGroupKey(settingPlugin.getGroupKey().name());
                entity.setGroupIcon(settingPlugin.getGroupKey().getIcon());
                entity.setSubGroupKey(settingPlugin.getSubGroupKey());
            }

            if (plugin instanceof DynamicConsoleHeaderSettingPlugin) {
                entity.setTitle(((DynamicConsoleHeaderSettingPlugin<?>) plugin).getTitle());
            }

            if (plugin instanceof ConsoleSettingPlugin) {
                String[] pages = ((ConsoleSettingPlugin<?>) plugin).pages();
                if (pages != null && pages.length > 0) {
                    entity.setPages(new HashSet<>(Arrays.asList(pages)));
                }
                ConsolePlugin.RenderType[] renderTypes = ((ConsoleSettingPlugin<?>) plugin).renderTypes();
                if (renderTypes != null && renderTypes.length > 0) {
                    entity.setRenderTypes(new HashSet<>(Arrays.asList(renderTypes)));
                }
            }
        }
    }

    @SneakyThrows
    private static String getIcon(SettingPlugin<?> plugin) {
        if (plugin instanceof SettingPluginButton) {
            return ((SettingPluginButton) plugin).getIcon();
        }
        if (plugin instanceof ConsoleSettingPlugin) {
            return ((ConsoleSettingPlugin<?>) plugin).getIcon();
        }
        if (plugin instanceof SettingPluginToggle) {
            return ((SettingPluginToggle) plugin).getIcon();
        }
        if (plugin instanceof SettingPluginOptionsFileExplorer) {
            return ((SettingPluginOptionsFileExplorer) plugin).getIcon();
        }
        return null;
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
                BundleEntryPoint bundleEntrypoint = entityContext.getBeansOfType(BundleEntryPoint.class)
                        .stream().filter(b -> b.getClass().getName().startsWith(pathName)).findAny().orElse(null);
                if (bundleEntrypoint == null) {
                    throw new ServerException("Unable find bundle entry-point for setting: ");
                }
                return bundleEntrypoint.getBundleId();
            }
            return null;
        });
    }

    @Transactional
    public void postConstruct() {
        for (SettingPlugin<?> settingPlugin : EntityContextSettingImpl.settingPluginsByPluginKey.values()) {
            if (!settingPlugin.transientState()) {
                SettingEntity settingEntity = entityContext.getEntity(getKey(settingPlugin));
                if (settingEntity == null) {
                    settingEntity = new SettingEntity();
                    createSettingEntityFromPlugin(settingPlugin, settingEntity, entityContext);
                    entityContext.save(settingEntity);
                }
            }
        }
    }

    /*@Transactional
    public void deleteRemovedSettings() {
        for (SettingEntity entity : listAll()) {
            SettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(entity.getEntityID());
            if (plugin == null) {
                entityContext.delete(entity);
            }
        }
    }*/
}
