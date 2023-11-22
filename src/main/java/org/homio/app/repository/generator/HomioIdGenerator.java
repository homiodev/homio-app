package org.homio.app.repository.generator;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.homio.api.entity.BaseEntity;
import org.homio.app.model.var.VariableBackup;

import java.util.UUID;

public class HomioIdGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        if (object instanceof BaseEntity) {
            String entityID = ((BaseEntity) object).getEntityID();
            if (entityID == null) {
                String prefix = ((BaseEntity) object).getEntityPrefix();
                entityID = prefix + UUID.randomUUID();

            }
            return entityID;
        } else if (object instanceof VariableBackup) {

        }
        return UUID.randomUUID().toString();
    }
}
