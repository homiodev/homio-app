package org.touchhome.app.model.entity;

import org.hibernate.mapping.PersistentClass;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.entity.StorageEntity;
import org.touchhome.bundle.api.fs.VendorFileSystem;
import org.touchhome.bundle.api.ui.UISidebarChildren;
import org.touchhome.bundle.api.ui.field.action.impl.DynamicContextMenuAction;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import java.util.Map;
import java.util.Set;

@Entity
@DiscriminatorValue(PersistentClass.NOT_NULL_DISCRIMINATOR_MAPPING)
@UISidebarChildren(icon = "", color = "", allowCreateItem = false)
public class StorageFallbackEntity extends StorageEntity<StorageFallbackEntity, StorageFallbackEntity.StorageFallbackFileSystem> {
    @Override
    public String getEntityPrefix() {
        return "storage_fallback";
    }

    static class StorageFallbackFileSystem extends VendorFileSystem {

        public StorageFallbackFileSystem(BaseEntity entity, EntityContext entityContext) {
            super(entity, entityContext);
        }

        @Override
        protected void onEntityUpdated() {

        }

        @Override
        public long getTotalSpace() {
            return 0;
        }

        @Override
        public long getUsedSpace() {
            return 0;
        }

        @Override
        public void upload(String[] parentPath, String fileName, byte[] content, boolean append) {

        }

        @Override
        public boolean delete(String[] path) {
            return false;
        }

        @Override
        public void dispose() {

        }
    }

    @Override
    public boolean requireConfigure() {
        return false;
    }

    @Override
    public StorageFallbackFileSystem getFileSystem(EntityContext entityContext) {
        return null;
    }

    @Override
    public Map<String, StorageFallbackFileSystem> getFileSystemMap() {
        return null;
    }

    @Override
    public long getConnectionHashCode() {
        return 0;
    }

    @Override
    public Set<? extends DynamicContextMenuAction> getActions(EntityContext entityContext) {
        return null;
    }
}
