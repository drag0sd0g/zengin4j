package io.zengin4j.core.codec;

import module java.base;
import io.zengin4j.core.format.RecordKind;

/// The record-sequence state machine of §12.4.
///
/// Multiple header/data/trailer groups in one file are accepted here even
/// where a particular institution forbids them; enforcing single-batch is a
/// validation rule, not a parsing rule (R-C1).
enum ParserState {

    /// Nothing read yet, or the file has only just begun.
    EXPECT_HEADER("a header record (データ区分 '1')"),

    /// Inside a batch: data records, then a trailer.
    IN_BATCH("a data record ('2') or a trailer record ('8')"),

    /// A trailer closed the batch: another header, or the end record.
    BATCH_CLOSED("a header record ('1') or the end record ('9')"),

    /// The end record was read; nothing may follow it.
    DONE("end of file");

    private final String expected;

    ParserState(String expected) {
        this.expected = expected;
    }

    /// Applies a record kind to this state.
    ///
    /// @param kind the kind of the record just read
    /// @return the next state, or empty if the record may not appear here
    Optional<ParserState> next(RecordKind kind) {
        return switch (this) {
            case EXPECT_HEADER -> kind == RecordKind.HEADER ? Optional.of(IN_BATCH) : Optional.empty();
            case IN_BATCH -> switch (kind) {
                case DATA -> Optional.of(IN_BATCH);
                case TRAILER -> Optional.of(BATCH_CLOSED);
                default -> Optional.empty();
            };
            case BATCH_CLOSED -> switch (kind) {
                case HEADER -> Optional.of(IN_BATCH);
                case END -> Optional.of(DONE);
                default -> Optional.empty();
            };
            case DONE -> Optional.empty();
        };
    }

    /// Describes what may legally appear next, for diagnostics.
    ///
    /// @return a human-readable description
    String expected() {
        return expected;
    }
}
