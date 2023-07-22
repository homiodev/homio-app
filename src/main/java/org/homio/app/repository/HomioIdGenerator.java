package org.homio.app.repository;

import java.util.UUID;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.homio.api.entity.BaseEntity;

public class HomioIdGenerator implements IdentifierGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        if (object instanceof BaseEntity) {
            String entityID = ((BaseEntity<?>) object).getEntityID();
            if (entityID == null) {
                String prefix = ((BaseEntity<?>) object).getEntityPrefix();
                entityID = prefix + UUID.randomUUID();

            }
            return entityID;
        }
        return UUID.randomUUID().toString();
    }

   /* @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue, EventType eventType) {
        UUID.randomUUID();
        return super.generate(session, owner, currentValue, eventType);
    }*/
}
