package io.zengin4j.iso20022.api;

import io.zengin4j.core.loss.LossSeverity;
import io.zengin4j.iso20022.loss.MappingLossReport;
import java.util.Objects;

/**
 * What a conversion produced, and what it could not carry.
 *
 * <p>The two are inseparable by construction. R-I14 forbids an API that returns
 * the output alone, and this is how: there is no accessor anywhere in this
 * module that hands back a converted file without its report attached, so a
 * caller who wants to ignore the loss has to write a line that says so.
 *
 * @param <T>    the converted artefact
 * @param output the conversion's output
 * @param loss   what the conversion could not carry
 * @since 0.5.0
 */
public record MappingResult<T>(T output, MappingLossReport loss) {

    /**
     * Validates the result.
     *
     * @throws NullPointerException if either component is null
     */
    public MappingResult {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(loss, "loss");
    }

    /** @return true if nothing was lost */
    public boolean isLossless() {
        return loss.isLossless();
    }

    /**
     * Whether anything reached a severity.
     *
     * @param threshold the severity to test
     * @return true if any entry is at or above it
     */
    public boolean hasAtLeast(LossSeverity threshold) {
        return loss.hasAtLeast(threshold);
    }

    @Override
    public String toString() {
        return "MappingResult[" + output.getClass().getSimpleName() + ", " + loss + "]";
    }
}
