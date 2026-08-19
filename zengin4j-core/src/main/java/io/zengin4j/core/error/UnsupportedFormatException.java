package io.zengin4j.core.error;

/// No descriptor is registered for the 種別コード (business type code) found in
/// the header record.
///
/// This is the "absent" state of the verification protocol (§0.3): the
/// format is not implemented, as opposed to implemented-but-unverified, which
/// raises [UnverifiedFormatException] instead.
///
/// @since 0.1.0
public final class UnsupportedFormatException extends ZenginException {

    private final String typeCode;

    /// Creates a diagnostic naming the unsupported business type code.
    ///
    /// @param typeCode the two-character 種別コード read from the header
    /// @param known    a human-readable list of the type codes that are
    ///   registered, for example `"21"`
    public UnsupportedFormatException(String typeCode, String known) {
        super("no format descriptor is registered for 種別コード '" + typeCode + "'. Registered: " + known
                        + ". Register a descriptor for this format, or specify an existing format explicitly"
                        + " via ReaderOptions.",
                "種別コード '" + typeCode + "' に対応するフォーマット定義が登録されていません。登録済み: " + known
                        + "。フォーマット定義を登録するか、ReaderOptions で既存のフォーマットを明示的に指定してください。");
        this.typeCode = typeCode;
    }

    /// Returns the 種別コード that has no registered descriptor.
    ///
    /// @return the two-character business type code, never `null`
    public String typeCode() {
        return typeCode;
    }
}
