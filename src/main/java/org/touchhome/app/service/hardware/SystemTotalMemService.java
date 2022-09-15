package org.touchhome.app.service.hardware;

import org.json.JSONObject;
import org.springframework.stereotype.Component;
import org.touchhome.app.manager.common.EntityContextStorage;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.entity.widget.ability.HasGetStatusValue;

import java.util.function.Consumer;

import static org.touchhome.app.utils.InternalUtil.GB_DIVIDER;

@Component
public class SystemTotalMemService implements HasGetStatusValue {

    @Override
    public Object getStatusValue(GetStatusValueRequest request) {
        return EntityContextStorage.TOTAL_MEMORY / GB_DIVIDER;
    }

    @Override
    public String getGetStatusDescription() {
        return "SYS.TOTAL_MEM";
    }

    @Override
    public void addUpdateValueListener(EntityContext entityContext, String key, JSONObject dynamicParameters,
                                       Consumer<Object> listener) {

    }

    @Override
    public String getEntityID() {
        return "TOTAL_MEM";
    }
}
