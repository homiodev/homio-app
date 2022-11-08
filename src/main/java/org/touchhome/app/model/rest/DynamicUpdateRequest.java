package org.touchhome.app.model.rest;

import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DynamicUpdateRequest {

  @NotNull
  private String dynamicUpdateId;
  @NotNull
  private String type;
  @Nullable
  private String entityID;

  @Override
  public boolean equals(Object o) {
      if (this == o) {
          return true;
      }
      if (o == null || getClass() != o.getClass()) {
          return false;
      }

    DynamicUpdateRequest that = (DynamicUpdateRequest) o;

      if (!dynamicUpdateId.equals(that.dynamicUpdateId)) {
          return false;
      }
      if (!type.equals(that.type)) {
          return false;
      }
    return Objects.equals(entityID, that.entityID);
  }

  @Override
  public int hashCode() {
    int result = dynamicUpdateId.hashCode();
    result = 31 * result + type.hashCode();
    result = 31 * result + (entityID != null ? entityID.hashCode() : 0);
    return result;
  }
}
