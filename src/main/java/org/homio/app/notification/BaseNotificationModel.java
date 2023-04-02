package org.homio.app.notification;

import static org.apache.commons.lang3.StringUtils.defaultString;

import java.util.Date;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@Accessors(chain = true)
public class BaseNotificationModel<T extends BaseNotificationModel> implements Comparable<T> {

    private String entityID;
    private String title;
    private Object value;
    private Date creationTime = new Date();

    public BaseNotificationModel(String entityID) {
        if (entityID == null) {
            throw new IllegalArgumentException("entityId is null");
        }
        this.entityID = entityID;
        this.title = entityID;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        T that = (T) o;
        return Objects.equals(entityID, that.getEntityID());
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityID);
    }

    @Override
    public int compareTo(@NotNull T other) {
        boolean eqEntity = this.entityID.equals(other.getEntityID());
        if (eqEntity) {
            return 0;
        }
        int compareValue =
                defaultString(this.title, this.entityID)
                        .compareTo(defaultString(other.getTitle(), other.getEntityID()));
        if (compareValue == 0) {
            compareValue = String.valueOf(this.value).compareTo(String.valueOf(other.getValue()));
        }
        return compareValue == 0 ? this.entityID.compareTo(other.getEntityID()) : compareValue;
    }

    @Override
    public String toString() {
        return defaultString(title, "") + (value != null ? " | " + value : "");
    }
}
