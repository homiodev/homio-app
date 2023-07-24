package org.homio.addon.tuya.internal.util;

import java.awt.Color;

public class ConversionUtil {

    /**
     * Convert a Tuya color string in hexadecimal notation to hex string
     *
     * @param hexColor the input string
     * @return the corresponding state
     */
    public static String hexColorDecode(String hexColor) {
        if (hexColor.length() == 12) {
            // 2 bytes H: 0-360, 2 bytes each S,B, 0-1000
            float h = Integer.parseInt(hexColor.substring(0, 4), 16);
            float s = Integer.parseInt(hexColor.substring(4, 8), 16) / 10F;
            float b = Integer.parseInt(hexColor.substring(8, 12), 16) / 10F;
            if (h == 360) {
                h = 0;
            }
            int rgb = Color.HSBtoRGB(h, s, b);
            return String.format("#%06X", (rgb & 0xFFFFFF));
        } else if (hexColor.length() == 14) {
            // 1 byte each RGB: 0-255, 2 byte H: 0-360, 1 byte each SB: 0-255
            int r = Integer.parseInt(hexColor.substring(0, 2), 16);
            int g = Integer.parseInt(hexColor.substring(2, 4), 16);
            int b = Integer.parseInt(hexColor.substring(4, 6), 16);

            return String.format("#%02X%02X%02X", r, g, b);
        } else {
            throw new IllegalArgumentException("Unknown color format");
        }
    }
}
