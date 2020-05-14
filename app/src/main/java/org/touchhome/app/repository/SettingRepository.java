package org.touchhome.app.repository;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.touchhome.app.manager.common.InternalManager;
import org.touchhome.app.model.entity.SettingEntity;
import org.touchhome.app.setting.SettingPlugin;
import org.touchhome.bundle.api.BundleSettingPlugin;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.repository.AbstractRepository;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

import static org.touchhome.bundle.api.BundleContext.BUNDLE_PREFIX;

@Repository
public class SettingRepository extends AbstractRepository<SettingEntity> {

    private final EntityContext entityContext;

    public SettingRepository(EntityContext entityContext) {
        super(SettingEntity.class, SettingEntity.PREFIX);
        this.entityContext = entityContext;
    }

    public static SettingEntity createSettingEntityFromPlugin(BundleSettingPlugin settingPlugin, SettingEntity settingEntity) {
        settingEntity.computeEntityID(() -> getKey(settingPlugin));
        String name = settingPlugin.getClass().getName();
        if (name.startsWith(BUNDLE_PREFIX)) {
            String bundle = name.substring(BUNDLE_PREFIX.length(), name.indexOf('.', BUNDLE_PREFIX.length()));
            settingEntity.setBundle(bundle);
        }
        if (settingPlugin.transientState()) {
            settingEntity.setEntityID(getKey(settingPlugin));
            fulfillEntityFromPlugin(settingEntity);
        }
        return settingEntity;
    }

    private static void fulfillEntityFromPlugin(SettingEntity entity) {
        BundleSettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(entity.getEntityID());
        entity.setDefaultValue(plugin.getDefaultValue());
        entity.setOrder(plugin.order());
        entity.setAdvanced(plugin.isAdvanced());
        entity.setColor(plugin.getIconColor());
        entity.setIcon(plugin.getIcon());
        entity.setToggleIcon(plugin.getToggleIcon());
        entity.setSettingType(plugin.getSettingType());
        entity.setAvailableValues(new LinkedHashSet<>(Arrays.asList(plugin.getAvailableValues())));

        if (plugin instanceof SettingPlugin) {
            SettingPlugin settingPlugin = (SettingPlugin) plugin;
            entity.setGroupKey(settingPlugin.getGroupKey().name());
            entity.setGroupIcon(settingPlugin.getGroupKey().getIcon());
            entity.setSubGroupKey(settingPlugin.getSubGroupKey());
        }
    }

    public static String getKey(BundleSettingPlugin settingPlugin) {
        return SettingEntity.PREFIX + settingPlugin.getClass().getSimpleName();
    }

    @Transactional
    public void postConstruct(Collection<BundleSettingPlugin> settingPlugins) {
        List<SettingEntity> entities = listAll();
        deleteRemovedSettings(entities);
        for (BundleSettingPlugin settingPlugin : settingPlugins) {
            if (!settingPlugin.transientState()) {
                createOrUpdateSetting(entities, settingPlugin);
            }
        }
    }

    @Override
    public void updateEntityAfterFetch(SettingEntity entity) {
        fulfillEntityFromPlugin(entity);
    }

    private void createOrUpdateSetting(List<SettingEntity> entities, BundleSettingPlugin settingPlugin) {
        SettingEntity settingEntity = entityContext.getEntityOrDefault(getKey(settingPlugin), new SettingEntity());
        settingEntity.computeEntityID(() -> getKey(settingPlugin));

        entities.stream().filter(p -> p.getEntityID().equals(settingEntity.getEntityID())).findAny()
                .ifPresent(settingEntity1 -> settingEntity.setValue(settingEntity1.getValue()));

        createSettingEntityFromPlugin(settingPlugin, settingEntity);

        entityContext.save(settingEntity);
    }

    private void deleteRemovedSettings(List<SettingEntity> entities) {
        for (SettingEntity entity : entities) {
            BundleSettingPlugin plugin = InternalManager.settingPluginsByPluginKey.get(entity.getEntityID());
            if(plugin == null) {
                this.deleteByEntityID(entity.getEntityID());
            }
        }
    }
}
