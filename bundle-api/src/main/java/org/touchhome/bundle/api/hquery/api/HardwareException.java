package org.touchhome.bundle.api.hquery.api;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class HardwareException extends RuntimeException {
    private List<String> inputs;
    private int retValue;
}
