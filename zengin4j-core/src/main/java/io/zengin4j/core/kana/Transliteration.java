package io.zengin4j.core.kana;

import module java.base;
import io.zengin4j.core.loss.LossReport;
import io.zengin4j.core.loss.LossSeverity;

/// What a transliteration produced, and what it cost.
///
/// The two travel together on purpose. Text that has been through this engine
/// looks like text that has not, so a caller who wants only the string can have
/// it — and a caller who needs to know whether a payee was renamed has the
/// answer in the same object rather than in a log.
///
/// ```java
/// Transliteration result = KanaTransliterator.toHalfWidth("ガクブチ ジロウ", options);
/// if (result.loss().hasAtLeast(LossSeverity.MATERIAL)) {
///     // somebody's name reads differently now; say so before sending
/// }
/// ```
///
/// @param text the transliterated text
/// @param loss what changed on the way
/// @since 0.4.0
public record Transliteration(String text, LossReport loss) {

    /// Validates the components.
    public Transliteration {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(loss, "loss");
    }

    /// A result that lost nothing.
    ///
    /// @param text the text, unchanged in any way that matters
    /// @return the result
    public static Transliteration lossless(String text) {
        return new Transliteration(text, LossReport.lossless());
    }

    /// Whether nothing was lost.
    ///
    /// @return `true` if the loss report is empty
    public boolean isLossless() {
        return loss.isLossless();
    }

    /// Whether anything material or worse happened.
    ///
    /// The question worth asking before sending a file: informational losses
    /// are widening and case, material ones are a name that reads differently.
    ///
    /// @return `true` if any loss is `MATERIAL` or `CRITICAL`
    public boolean isMateriallyChanged() {
        return loss.hasAtLeast(LossSeverity.MATERIAL);
    }

    @Override
    public String toString() {
        return text;
    }
}
