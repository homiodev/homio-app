package org.homio.app.ssh;

import javax.persistence.Entity;
import org.homio.bundle.api.entity.types.CommunicationEntity;
import org.homio.bundle.api.service.EntityService;
import org.homio.bundle.api.ui.field.UIField;
import org.homio.bundle.api.ui.field.UIFieldType;

@Entity
public abstract class SshBaseEntity<T extends SshBaseEntity, S extends SshProviderService<T>> extends CommunicationEntity<T>
    implements EntityService<S, T> {

    @UIField(order = 1, hideOnEmpty = true, fullWidth = true, bg = "#334842C2", type = UIFieldType.HTML)
    public String getDescription() {
        return getJsonData("description");
    }

    public void setDescription(String value) {
        setJsonData("description", value);
    }
}
