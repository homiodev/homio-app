package org.touchhome.app.json;

import lombok.Data;

@Data
public class RouteJSON {

    private String url;
    private String type;
    private Class<?> itemType;
    private boolean allowCreateNewItems;

    public RouteJSON(String url) {
        this.url = url;
    }

    @Override
    public String toString() {
        return "RouteJSON{" +
                "url='" + url + '\'' +
                '}';
    }
}
