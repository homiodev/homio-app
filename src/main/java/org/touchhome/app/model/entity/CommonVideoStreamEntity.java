package org.touchhome.app.model.entity;

import javax.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.app.service.video.CommonUriStreamHandler;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.RestartHandlerOnChange;
import org.touchhome.bundle.api.netty.NettyUtils;
import org.touchhome.bundle.api.ui.field.UIField;
import org.touchhome.bundle.api.video.AbilityToStreamHLSOverFFMPEG;
import org.touchhome.bundle.api.video.BaseFFMPEGVideoStreamEntity;

@Setter
@Getter
@Entity
@Accessors(chain = true)
public class CommonVideoStreamEntity extends BaseFFMPEGVideoStreamEntity<CommonVideoStreamEntity, CommonUriStreamHandler>
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
  public CommonUriStreamHandler createVideoHandler(EntityContext entityContext) {
    return new CommonUriStreamHandler(this, entityContext);
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
    setServerPort(NettyUtils.findFreeBootstrapServerPort());
  }
}
