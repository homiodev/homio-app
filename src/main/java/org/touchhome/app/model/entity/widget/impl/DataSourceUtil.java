package org.touchhome.app.model.entity.widget.impl;

import lombok.Getter;
import org.touchhome.bundle.api.EntityContext;

public class DataSourceUtil {
    public static DataSourceContext getSource(EntityContext entityContext, String dataSource) {
        DataSourceContext dataSourceContext = new DataSourceContext();
        if (dataSource != null) {
            String[] vds = dataSource.split("~~~");
            dataSourceContext.sourceClass = vds.length > 1 ? vds[1] : "";
            dataSourceContext.source = evaluateDataSource(vds, entityContext);
        }
        return dataSourceContext;
    }

    private static Object evaluateDataSource(String[] vds, EntityContext entityContext) {
        if (vds.length > 2) {
            switch (vds[2]) {
                case "bean":
                    return entityContext.getBean(vds[0], Object.class);
                case "entityByClass":
                    return entityContext.getEntity(vds[0]);
            }
        }
        return null;
    }

    @Getter
    public static class DataSourceContext {
        private Object source;
        private String sourceClass;
    }
}
