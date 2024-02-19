package org.homio.app.model.entity.widget.impl.video.sourceResolver;

import org.homio.api.util.DataSourceUtil;
import org.homio.app.utils.HardwareUtils;
import org.springframework.stereotype.Component;

@Component
public class URLWidgetVideoSourceResolver implements WidgetVideoSourceResolver {

    @Override
    public VideoEntityResponse resolveDataSource(String valueDataSource) {
        String dataSource = DataSourceUtil.getSelection(valueDataSource).getValue();
        if (dataSource.startsWith("http") || dataSource.startsWith("$DEVICE_URL")) {
            return new VideoEntityResponse(valueDataSource, dataSource, dataSource, HardwareUtils.getVideoType(dataSource));
        }
        return null;
    }
}
