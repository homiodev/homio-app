package org.homio.addon.tuya.internal.util;

import java.math.BigDecimal;

public class ConversionUtil {

    /**
     * Convert a Tuya color string in hexadecimal notation to {@link HSBType}
     *
     * @param hexColor the input string
     * @return the corresponding state
     */
    public static HSBType hexColorDecode(String hexColor) {
        if (hexColor.length() == 12) {
            // 2 bytes H: 0-360, 2 bytes each S,B, 0-1000
            double h = Integer.parseInt(hexColor.substring(0, 4), 16);
            double s = Integer.parseInt(hexColor.substring(4, 8), 16) / 10.0;
            double b = Integer.parseInt(hexColor.substring(8, 12), 16) / 10.0;
            if (h == 360) {
                h = 0;
            }

            return new HSBType(new DecimalType(h), new PercentType(new BigDecimal(s)),
                    new PercentType(new BigDecimal(b)));
        } else if (hexColor.length() == 14) {
            // 1 byte each RGB: 0-255, 2 byte H: 0-360, 1 byte each SB: 0-255
            int r = Integer.parseInt(hexColor.substring(0, 2), 16);
            int g = Integer.parseInt(hexColor.substring(2, 4), 16);
            int b = Integer.parseInt(hexColor.substring(4, 6), 16);

            return HSBType.fromRGB(r, g, b);
        } else {
            throw new IllegalArgumentException("Unknown color format");
        }
    }

    /**
     * Convert a {@link HSBType} to a Tuya color string in hexadecimal notation
     *
     * @param hsb The input state
     * @return the corresponding hexadecimal String
     */
    public static String hexColorEncode(HSBType hsb, boolean oldColorMode) {
        if (!oldColorMode) {
            return String.format("%04x%04x%04x", hsb.getHue().intValue(),
                    (int) (hsb.getSaturation().doubleValue() * 10), (int) (hsb.getBrightness().doubleValue() * 10));
        } else {
            return String.format("%02x%02x%02x%04x%02x%02x", (int) (hsb.getRed().doubleValue() * 2.55),
                    (int) (hsb.getGreen().doubleValue() * 2.55), (int) (hsb.getBlue().doubleValue() * 2.55),
                    hsb.getHue().intValue(), (int) (hsb.getSaturation().doubleValue() * 2.55),
                    (int) (hsb.getBrightness().doubleValue() * 2.55));
        }
    }

    /**
     * Convert the brightness value from Tuya to {@link PercentType}
     *
     * @param value the input value
     * @param min the minimum value (usually 0 or 10)
     * @param max the maximum value (usually 255 or 1000)
     * @return the corresponding PercentType (PercentType.ZERO if value is <= min)
     */
    public static PercentType brightnessDecode(double value, double min, double max) {
        if (value <= min) {
            return PercentType.ZERO;
        } else if (value >= max) {
            return PercentType.HUNDRED;
        } else {
            return new PercentType(new BigDecimal(100.0 * value / (max - min)));
        }
    }

    /**
     * Converts a {@link PercentType} to a Tuya brightness value
     *
     * @param value the input value
     * @param min the minimum value (usually 0 or 10)
     * @param max the maximum value (usually 255 or 1000)
     * @return the int closest to the converted value
     */
    public static int brightnessEncode(PercentType value, double min, double max) {
        return (int) Math.round(value.doubleValue() * (max - min) / 100.0);
    }
}
