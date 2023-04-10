package org.homio.app.rest.widget;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
@RequiredArgsConstructor
public class ChartDataset {

    private final String id;
    private final String entityID;
    private String label;
    private List<Float> data;
}