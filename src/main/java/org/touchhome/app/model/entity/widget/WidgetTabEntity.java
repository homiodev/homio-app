package org.touchhome.app.model.entity.widget;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Set;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.common.exception.ServerException;

@Getter
@Setter
@Entity
public final class WidgetTabEntity extends BaseEntity<WidgetTabEntity> implements Comparable<WidgetTabEntity> {

  public static final String PREFIX = "wtab_";
  public static final String GENERAL_WIDGET_TAB_NAME = PREFIX + "main";

  @Getter
  @JsonIgnore
  @OneToMany(fetch = FetchType.LAZY, mappedBy = "widgetTabEntity")
  private Set<WidgetBaseEntity> widgetBaseEntities;

  @Override
  public int compareTo(@NotNull WidgetTabEntity o) {
    return this.getCreationTime().compareTo(o.getCreationTime());
  }

  @Override
  protected void validate() {
    if (getName() == null || getName().length() < 2 || getName().length() > 10) {
      throw new ServerException("Widget tab name must be between 2..10 characters");
    }
  }

  @Override
  public void beforeDelete(EntityContext entityContext) {
    if (this.getEntityID().equals(GENERAL_WIDGET_TAB_NAME)) {
      throw new ServerException("ERROR.REMOVE_MAIN_TAB");
    }
    if (!widgetBaseEntities.isEmpty()) {
      throw new ServerException("ERROR.REMOVE_NON_EMPTY_TAB");
    }
  }

  @Override
  public String getEntityPrefix() {
    return PREFIX;
  }
}
