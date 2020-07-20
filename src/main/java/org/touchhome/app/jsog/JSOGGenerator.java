package org.touchhome.app.jsog;


import com.fasterxml.jackson.annotation.ObjectIdGenerator;
import org.touchhome.bundle.api.model.BaseEntity;

/**
 * Use this as an object id generator and your class will serialize as jsog.
 */
public class JSOGGenerator extends ObjectIdGenerator<JSOGRef> {

    private static final long serialVersionUID = 1L;

    private final Class<?> _scope;

    public JSOGGenerator() {
        this(null);
    }

    private JSOGGenerator(Class<?> scope) {
        _scope = scope;
    }

    @Override
    public Class<?> getScope() {
        return _scope;
    }

    @Override
    public boolean canUseFor(ObjectIdGenerator<?> gen) {
        return (gen.getClass() == getClass()) && (gen.getScope() == _scope);
    }

    @Override
    public ObjectIdGenerator<JSOGRef> forScope(Class<?> scope) {
        return (_scope == scope) ? this : new JSOGGenerator(scope);
    }

    @Override
    public ObjectIdGenerator<JSOGRef> newForSerialization(Object context) {
        return new JSOGGenerator(_scope);
    }

    @Override
    public com.fasterxml.jackson.annotation.ObjectIdGenerator.IdKey key(Object key) {
        return new IdKey(getClass(), _scope, key);
    }

    @Override
    public JSOGRef generateId(Object forPojo) {
        if (forPojo instanceof BaseEntity) {
            return new JSOGRef(((BaseEntity) forPojo).getEntityID());
        }
        throw new IllegalStateException("forPojo is not a Model but: " + forPojo.getClass().getSimpleName());
    }

    @Override
    public boolean maySerializeAsObject() {
        return true;
    }

    @Override
    public boolean isValidReferencePropertyName(String name, Object parser) {
        return JSOGRef.REF_KEY.equals(name);
    }
}
