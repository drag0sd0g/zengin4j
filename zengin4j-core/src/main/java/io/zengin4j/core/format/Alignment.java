package io.zengin4j.core.format;

/**
 * Which end of a fixed-width field the value sits against.
 *
 * @since 0.1.0
 */
public enum Alignment {
    /** Value at the start of the field, padding after it. */
    LEFT,

    /** Value at the end of the field, padding before it. */
    RIGHT
}
