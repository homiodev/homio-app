package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.manager.common.impl.EntityContextSettingImpl;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.BundleEntryPoint;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.console.ConsolePlugin;
import org.touchhome.bundle.api.repository.AbstractRepository;
import org.touchhome.bundle.api.setting.BundleSettingPlugin;
import org.touchhome.bundle.api.setting.BundleSettingPluginToggle;
import org.touchhome.bundle.api.setting.console.BundleConsoleSettingPlugin;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.touchhome.app.model.entity.SettingEntity.getKey;
import static org.touchhome.bundle.api.BundleEntryPoint.BUNDLE_PREFIX;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity> {

    private final static Map<String, String> settingToBundleMap = new HashMap<>();
    private final EntityContext entityContext;

    public SettingRepository(EntityContext entityContext) {
        super(SettingEntity.class, SettingEntity.PREFIX);
        this.entityContext = entityContext;
    }

    public static SettingEntity createSettingEntityFromPlugin(BundleSettingPlugin settingPlugin, SettingEntity settingEntity, EntityContext entityContext) {
        settingEntity.computeEntityID(() -> getKey(settingPlugin));
        if (settingPlugin.transientState()) {
            settingEntity.setEntityID(getKey(settingPlugin));
            fulfillEntityFromPlugin(settingEntity, entityContext);
        }
        return settingEntity;
    }

    public static void fulfillEntityFromPlugin(SettingEntity entity, EntityContext entityContext) {
        BundleSettingPlugin plugin = EntityContextSettingImpl.settingPluginsByPluginKey.get(entity.getEntityID());
        if (plugin != null) {
            entity.setBundle(getSettingBundleName(entityContext, plugin.getClass()));
            entity.setDefaultValue(plugin.getDefaultValue());
            entity.setOrder(plugin.order());
            entity.setAdvanced(plugin.isAdvanced());
            entity.setColor(plugin.getIconColor());
            entity.setIcon(plugin.getIcon());
            if (plugin instanceof BundleSettingPluginToggle) {
                entity.setToggleIcon(((BundleSettingPluginToggle) plugin).getToggleIcon());
            }
            entity.setSettingType(plugin.getSettingType());
            entity.setReverted(plugin.isReverted() ? true : null);
            entity.setParameters(plugin.getParameters(entityContext, entity.getValue()));
            entity.setDisabled(plugin.isDisabled(entityContext) ? true : null);
            entity.setRequired(plugin.isRequired());
            if (entity.getSettingType() == BundleSettingPlugin.SettingType.SelectBox) {
                entity.setAvailableValues(plugin.loadAvailableValues(entityContext));
            }

            if (plugin instanceof SettingPlugin) {
                SettingPlugin settingPlugin = (SettingPlugin) plugin;
                entity.setGroupKey(settingPlugin.getGroupKey().name());
                entity.setGroupIcon(settingPlugin.getGroupKey().getIcon());
                entity.setSubGroupKey(settingPlugin.getSubGroupKey());
            }

            if (plugin instanceof BundleConsoleSettingPlugin) {
                String[] pages = ((BundleConsoleSettingPlugin) plugin).pages();
                if (pages != null && pages.length > 0) {
                    entity.setPages(new HashSet<>(Arrays.asList(pages)));
                }
                ConsolePlugin.RenderType[] renderTypes = ((BundleConsoleSettingPlugin) plugin).renderTypes();
                if (renderTypes != null && renderTypes.length > 0) {
                    entity.setRenderTypes(new HashSet<>(Arrays.asList(renderTypes)));
                }
            }
        }
    }

    /**
     * Search bundleId for setting.
     */
    public static String getSettingBundleName(EntityContext entityContext, Class<? extends BundleSettingPlugin> settingPluginClass) {
        String name = settingPluginClass.getName();
        return settingToBundleMap.computeIfAbsent(name, key -> {
            if (name.startsWith(BUNDLE_PREFIX)) {
                String pathName = name.substring(0, BUNDLE_PREFIX.length() + name.substring(BUNDLE_PREFIX.length()).indexOf('.'));
                BundleEntryPoint bundleEntrypoint = entityContext.getBeansOfType(BundleEntryPoint.class)
                        .stream().filter(b -> b.getClass().getName().startsWith(pathName)).findAny().orElse(null);
                if (bundleEntrypoint == null) {
                    throw new IllegalStateException("Unable find bundle entry-point for setting: ");
                }
                return bundleEntrypoint.getBundleId();
            }
            return null;
        });
    }

    @Transactional
    public void postConstruct() {
        for (BundleSettingPlugin settingPlugin : EntityContextSettingImpl.settingPluginsByPluginKey.values()) {
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
            BundleSettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(entity.getEntityID());
            if (plugin == null) {
                entityContext.delete(entity);
            }
        }
    }*/

    @Override
    public void updateEntityAfterFetch(SettingEntity entity) {
        fulfillEntityFromPlugin(entity, entityContext);
    }
}
