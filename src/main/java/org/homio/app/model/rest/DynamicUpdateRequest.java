package org.homio.app.model.rest;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class DynamicUpdateRequest {

    @NotNull
    private String dynamicUpdateId;
    @Nullable
    private String entityID;

    public DynamicUpdateRequest(@NotNull String dynamicUpdateId) {
        this.dynamicUpdateId = dynamicUpdateId;
    }

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
        return Objects.equals(entityID, that.entityID);
    }

    @Override
    public int hashCode() {
        int result = dynamicUpdateId.hashCode();
        result = 31 * result + (entityID != null ? entityID.hashCode() : 0);
        return result;
    }
}
