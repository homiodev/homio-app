package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.BundleConsoleSettingPlugin;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.repository.AbstractRepository;

import static org.touchhome.bundle.api.BundleEntrypoint.BUNDLE_PREFIX;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity> {

    private final EntityContext entityContext;

    public SettingRepository(EntityContext entityContext) {
        super(SettingEntity.class, SettingEntity.PREFIX);
        this.entityContext = entityContext;
    }

    public static SettingEntity createSettingEntityFromPlugin(BundleSettingPlugin settingPlugin, SettingEntity settingEntity, EntityContext entityContext) {
        settingEntity.computeEntityID(() -> getKey(settingPlugin));
        String name = settingPlugin.getClass().getName();
        if (name.startsWith(BUNDLE_PREFIX)) {
            String bundle = name.substring(BUNDLE_PREFIX.length(), name.indexOf('.', BUNDLE_PREFIX.length()));
            settingEntity.setBundle(bundle);
        }
        if (settingPlugin.transientState()) {
            settingEntity.setEntityID(getKey(settingPlugin));
            fulfillEntityFromPlugin(settingEntity, entityContext);
        }
        return settingEntity;
    }

    private static void fulfillEntityFromPlugin(SettingEntity entity, EntityContext entityContext) {
        BundleSettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(entity.getEntityID());
        entity.setDefaultValue(plugin.getDefaultValue());
        entity.setOrder(plugin.order());
        entity.setAdvanced(plugin.isAdvanced());
        entity.setColor(plugin.getIconColor());
        entity.setIcon(plugin.getIcon());
        entity.setToggleIcon(plugin.getToggleIcon());
        entity.setSettingType(plugin.getSettingType());
        entity.setParameters(plugin.getParameters(entityContext, entity.getValue()));
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
            entity.setPages(((BundleConsoleSettingPlugin) plugin).pages());
        }
    }

    public static String getKey(BundleSettingPlugin settingPlugin) {
        return SettingEntity.PREFIX + settingPlugin.getClass().getSimpleName();
    }

    @Transactional
    public void postConstruct() {
        for (BundleSettingPlugin settingPlugin : InternalManager.settingPluginsByPluginKey.values()) {
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

    @Override
    public void updateEntityAfterFetch(SettingEntity entity) {
        fulfillEntityFromPlugin(entity, entityContext);
    }

    @Transactional
    public void deleteRemovedSettings() {
        for (SettingEntity entity : listAll()) {
            BundleSettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(entity.getEntityID());
            if (plugin == null) {
                entityContext.delete(entity);
            }
        }
    }
}
