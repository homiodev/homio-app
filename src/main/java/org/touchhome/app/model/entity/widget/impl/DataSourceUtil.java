package org.touchhome.app.model.entity.widget.impl;

import lombok.Getter;
import org.touchhome.bundle.api.EntityContext;

public class DataSourceUtil {
    public static DataSourceContext getSource(EntityContext entityContext, String dataSource) {
        DataSourceContext dataSourceContext = new DataSourceContext();
        if (dataSource != null) {
            String[] vds = dataSource.split("~~~");
            if (vds.length > 2) {
                dataSourceContext.sourceClass = vds[vds.length == 4 ? 2 : 1];
                dataSourceContext.source =
                        evaluateDataSource(vds[vds.length == 4 ? 3 : 2], vds[vds.length == 4 ? 1 : 0], entityContext);

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
