package org.homio.app.repository.generator;

import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;
import org.homio.api.entity.BaseEntity;
import org.homio.api.entity.device.DeviceBaseEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomioIdGenerator implements IdentifierGenerator {

  // hack to persist entity with defined id
  public static Map<String, Pair<String, String>> PERSIST_IDS = new ConcurrentHashMap<>();

  private static String getName(BaseEntity object) {
    if (object instanceof DeviceBaseEntity dbe) {
      return (String) dbe.getJsonData().remove("name");
    }
    return object.getName();
  }

  @Override
  public Object generate(SharedSessionContractImplementor session, Object object) {
    if (object instanceof BaseEntity baseEntity) {
      String name = getName(baseEntity);
      var data = name == null ? null : PERSIST_IDS.remove(name);
      if (data != null) {
        baseEntity.setEntityID(data.getLeft());
        baseEntity.setName(data.getRight());
      }
      String entityID = baseEntity.getEntityID();
      if (entityID == null) {
        String prefix = baseEntity.getEntityPrefix();
        entityID = prefix + UUID.randomUUID();

      }
      return entityID;
    }
    return UUID.randomUUID().toString();
  }
}
