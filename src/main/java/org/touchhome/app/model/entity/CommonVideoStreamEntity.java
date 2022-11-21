package org.touchhome.app.model.entity;

import javax.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.service.video.CommonVideoService;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.RestartHandlerOnChange;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.video.AbilityToStreamHLSOverFFMPEG;
import org.touchhome.bundle.api.video.BaseFFMPEGVideoStreamEntity;
import org.touchhome.bundle.api.video.BaseVideoService;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class CommonVideoStreamEntity extends BaseFFMPEGVideoStreamEntity<CommonVideoStreamEntity, CommonVideoService>
    implements AbilityToStreamHLSOverFFMPEG<CommonVideoStreamEntity> {

  public static final String PREFIX = "vidc_";

  @Override
  @UIField(order = 5, label = "url", inlineEdit = true, required = true)
  @RestartHandlerOnChange
  public String getIeeeAddress() {
    return super.getIeeeAddress();
  }

  @Override
  public String getFolderName() {
    return "video";
  }

  @Override
  public String getDefaultName() {
    return null;
  }

  @Override
  public String toString() {
    return getIeeeAddress();
  }

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }

  @Override
  protected void beforePersist() {
    setSnapshotOutOptions("-update 1~~~-frames:v 1");
    setServerPort(BaseVideoService.findFreeBootstrapServerPort());
  }

  @Override
  public Class<CommonVideoService> getEntityServiceItemClass() {
    return CommonVideoService.class;
  }

  @Override
  public CommonVideoService createService(EntityContext entityContext) {
    return new CommonVideoService(entityContext, this);
  }
}
