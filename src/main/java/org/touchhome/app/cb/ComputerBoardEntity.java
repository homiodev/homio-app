package org.touchhome.app.cb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.Entity;
import lombok.extern.log4j.Log4j2;
import org.touchhome.app.cb.fs.ComputerBoardFileSystem;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.storage.BaseFileSystemEntity;
import org.touchhome.bundle.api.entity.types.MicroControllerBaseEntity;
import org.touchhome.bundle.api.model.Status;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.ui.field.UIFieldIgnore;
import org.touchhome.bundle.api.ui.field.action.v1.UIInputBuilder;
import org.touchhome.bundle.api.util.TouchHomeUtils;
import org.touchhome.common.util.CommonUtils;

@Entity
@Log4j2
public class ComputerBoardEntity extends MicroControllerBaseEntity<ComputerBoardEntity>
    implements BaseFileSystemEntity<ComputerBoardEntity, ComputerBoardFileSystem> {

    public static final String PREFIX = "cbe_";
    public static final String DEFAULT_DEVICE_ENTITY_ID = PREFIX + TouchHomeUtils.APP_UUID;

    @Override
    public String getDefaultName() {
        return "Computer board";
    }

    @UIField(order = 200)
    public String getFileSystemRoot() {
        return getJsonData("fs_root", CommonUtils.getRootPath().toString());
    }

    public void setFileSystemRoot(String value) {
        setJsonData("fs_root", value);
    }

    @Override
    public void beforeDelete(EntityContext entityContext) {
        if (getEntityID().equals(DEFAULT_DEVICE_ENTITY_ID)) {
            throw new IllegalStateException("Unable to remove primary ComputerBoard entity");
        }
        super.beforeDelete(entityContext);
    }

    @Override
    public String getEntityPrefix() {
        return PREFIX;
    }

    @Override
    public String getFileSystemAlias() {
        return "PRIMARY";
    }

    @Override
    public boolean isShowInFileManager() {
        return true;
    }

    @Override
    public String getFileSystemIcon() {
        return "fas fa-computer";
    }

    @Override
    public String getFileSystemIconColor() {
        return "#23819E";
    }

    @Override
    public boolean requireConfigure() {
        return false;
    }

    @Override
    public ComputerBoardFileSystem buildFileSystem(EntityContext entityContext) {
        return new ComputerBoardFileSystem(this);
    }

    @Override
    public long getConnectionHashCode() {
        return 0;
    }

    @Override
    public void assembleActions(UIInputBuilder uiInputBuilder) {

    }

    @Override
    @JsonIgnore
    @UIFieldIgnore
    public Status getStatus() {
        return super.getStatus();
    }

    @Override
    public boolean isDisableDelete() {
        return true;
    }

    public static void ensureDeviceExists(EntityContext entityContext) {
        if (entityContext.getEntity(DEFAULT_DEVICE_ENTITY_ID) == null) {
            log.info("Save default compute board device");
            entityContext.save(new ComputerBoardEntity().setEntityID(DEFAULT_DEVICE_ENTITY_ID));
        }
    }
}
