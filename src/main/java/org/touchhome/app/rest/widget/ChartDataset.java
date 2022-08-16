package org.touchhome.app.rest.widget;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@RequiredArgsConstructor
public class ChartDataset {
    private final String id;
    private String label;
    private List<Float> data;
}
