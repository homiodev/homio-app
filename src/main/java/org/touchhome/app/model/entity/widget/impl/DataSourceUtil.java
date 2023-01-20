package org.touchhome.app.model.entity.widget.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.touchhome.bundle.api.EntityContext;

public class DataSourceUtil {

    /**
     * Samples:  0 = "entityByClass", 1 = "HasAggregateValueFromSeries", 2 = "wgv_0x00158d0002a4dd24_state_left", 3 = "wg_z2m_0x00158d0002a4dd24", 4 = "wg_zigbee"
     */
    public static DataSourceContext getSource(EntityContext entityContext, String dataSource) {
        DataSourceContext dataSourceContext = new DataSourceContext();
        if (StringUtils.isNotEmpty(dataSource)) {
            List<String> vds = Arrays.asList(dataSource.split("~~~"));
            Collections.reverse(vds);
            if (vds.size() > 2) {
                dataSourceContext.sourceClass = vds.get(1);
                dataSourceContext.source = evaluateDataSource(vds.get(0), vds.get(2), entityContext);

            } else {
                throw new IllegalArgumentException("Unable to parse dataSource");
            }
        }
        return dataSourceContext;
    }

    private static Object evaluateDataSource(String dsb, String source, EntityContext entityContext) {
        switch (dsb) {
            case "bean":
                return entityContext.getBean(source, Object.class);
            case "entityByClass":
                return entityContext.getEntity(source);
        }
        return null;
    }

    @Getter
    public static class DataSourceContext {

        private Object source;
        private String sourceClass;
    }
}
