package org.touchhome.bundle.api.scratch;

public enum ArgumentType {
    /**
     * Numeric value with angle picker
     */
    angle,

    /**
     * Boolean value with hexagonal placeholder
     */
    Boolean,

    /**
     * Numeric value with color picker
     */
    color,

    /**
     * Numeric value with text field
     */
    number,

    /**
     * String value with text field
     */
    string,

    /**
     * String value with matrix field
     */
    matrix,

    /**
     * MIDI note number with note picker (piano) field
     */
    note,

    reference,

    variable
}
