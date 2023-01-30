package org.touchhome.app.json.jsog;

import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import com.fasterxml.jackson.annotation.ObjectIdResolver;
import com.fasterxml.jackson.annotation.SimpleObjectIdResolver;
import java.util.HashMap;
import java.util.Map;
import org.touchhome.bundle.api.entity.BaseEntity;

/**
 * Need in deserialization cases when json may contains more than one instance of same entity
 */
public class JSOGResolver extends SimpleObjectIdResolver {

    private Map<ObjectIdGenerator.IdKey, Object> _items;

    public void bindItem(ObjectIdGenerator.IdKey id, Object ob) {
        if (this._items == null) {
            this._items = new HashMap<>();
        }

        this._items.put(id, ob);
        JSOGRef key = (JSOGRef) id.key;
        ((BaseEntity) ob).setEntityID(key.ref);
    }

    public Object resolveId(ObjectIdGenerator.IdKey id) {
        return this._items == null ? null : this._items.get(id);
    }

    public boolean canUseFor(ObjectIdResolver resolverType) {
        return resolverType.getClass() == this.getClass();
    }

    public ObjectIdResolver newForDeserialization(Object context) {
        return new JSOGResolver();
    }

}
