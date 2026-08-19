package io.zengin4j.core.format;

import module java.base;

/// The record roles a Zengin file is built from, discriminated by データ区分,
/// the first byte of every record.
///
/// @since 0.1.0
public enum RecordKind {

    /// ヘッダーレコード — データ区分 `1`. Opens a batch.
    HEADER("header"),

    /// データレコード — データ区分 `2`. One payment or collection.
    DATA("data"),

    /// トレーラーレコード — データ区分 `8`. Closes a batch with control totals.
    TRAILER("trailer"),

    /// エンドレコード — データ区分 `9`. Closes the file.
    END("end"),

    /// Not a record role in the format: the kind reported for a record the
    /// reader could not interpret. Never appears in a descriptor.
    MALFORMED(null);

    private final String descriptorKey;

    RecordKind(String descriptorKey) {
        this.descriptorKey = descriptorKey;
    }

    /// Maps a descriptor's `records` key to a kind.
    ///
    /// @param key the key, for example `"header"`
    /// @return the kind, or empty if the key names no record role
    public static Optional<RecordKind> fromDescriptorKey(String key) {
        for (RecordKind kind : values()) {
            if (key.equals(kind.descriptorKey)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }

    /// Returns the key this kind is written as in a descriptor.
    ///
    /// @return the descriptor key
    /// @throws UnsupportedOperationException if called on [#MALFORMED],
    ///   which no descriptor may declare
    public String descriptorKey() {
        if (descriptorKey == null) {
            throw new UnsupportedOperationException("MALFORMED is not a declarable record kind");
        }
        return descriptorKey;
    }
}
