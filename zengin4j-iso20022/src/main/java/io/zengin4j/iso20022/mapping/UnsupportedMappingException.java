package io.zengin4j.iso20022.mapping;

import module java.base;
import io.zengin4j.core.error.ZenginException;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.iso20022.envelope.MessageId;

/// No mapping is bundled between this format and this message.
///
/// Names what is available, because the likely causes are a typo and an
/// expectation the library does not meet, and the two need different answers.
///
/// @since 0.5.0
public final class UnsupportedMappingException extends ZenginException {

    /// Creates the diagnostic.
    ///
    /// @param format    the Zengin format asked for
    /// @param message   the ISO 20022 message asked for
    /// @param supported the pairs a mapping exists for
    public UnsupportedMappingException(FormatId format, MessageId message,
            Collection<String> supported) {
        super("no mapping between " + format.value() + " and " + message.value()
                        + ". This library maps: " + supported,
                format.value() + " と " + message.value() + " の間のマッピングはありません。"
                        + "対応しているのは " + supported + " です。");
    }
}
