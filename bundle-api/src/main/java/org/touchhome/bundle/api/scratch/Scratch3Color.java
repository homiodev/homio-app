package org.touchhome.bundle.api.scratch;

import lombok.Getter;

import java.awt.*;

@Getter
public class Scratch3Color {
    private final String color1;
    private final String color2;
    private final String color3;

    public Scratch3Color(String color) {
        this.color1 = color;
        Color darkerColor = Color.decode(color).darker();
        this.color2 = String.format("#%02x%02x%02x", darkerColor.getRed(), darkerColor.getGreen(), darkerColor.getBlue());
        darkerColor = darkerColor.darker();
        this.color3 = String.format("#%02x%02x%02x", darkerColor.getRed(), darkerColor.getGreen(), darkerColor.getBlue());
    }
}
