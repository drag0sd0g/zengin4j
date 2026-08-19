package io.zengin4j.core.codec;

/// A counter shared between a reader and the views it hands out, so that a view
/// can tell whether the buffer beneath it has moved on.
///
/// One `int` comparison per field access buys an immediate,
/// well-located failure instead of silently reading the wrong record's bytes.
final class ViewGeneration {

    private int value;

    int current() {
        return value;
    }

    int advance() {
        return ++value;
    }
}
