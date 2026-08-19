package io.zengin4j.core.error;

/// A required character set is not present in the running JVM.
///
/// The Japanese encodings this library uses live in the `jdk.charsets`
/// module. A JVM assembled with `jlink` that omits it will decode nothing
/// useful, so the condition is reported by name at first use rather than
/// surfacing as an obscure `UnsupportedCharsetException` from deep inside
/// a decode loop.
///
/// @since 0.1.0
public final class CharsetUnavailableException extends ZenginException {

    private final String charsetName;

    /// Creates a diagnostic naming the missing character set.
    ///
    /// @param charsetName the JDK charset name that could not be resolved
    /// @param cause       the underlying lookup failure, may be `null`
    public CharsetUnavailableException(String charsetName, Throwable cause) {
        super("charset '" + charsetName + "' is not available in this JVM. It is provided by the"
                        + " jdk.charsets module; add it to your jlink image or run on a full JDK.",
                "文字セット '" + charsetName + "' がこの JVM で利用できません。jdk.charsets モジュールが"
                        + "必要です。jlink イメージに追加するか、通常の JDK で実行してください。",
                cause);
        this.charsetName = charsetName;
    }

    /// Returns the JDK charset name that could not be resolved.
    ///
    /// @return the charset name, never `null`
    public String charsetName() {
        return charsetName;
    }
}
