package io.zengin4j.iso20022.api;

import io.zengin4j.core.model.ZenginFile;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.iso20022.envelope.ZediFile;
import io.zengin4j.iso20022.loss.MappingLossReport;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A file converted to ISO 20022 and back, with the loss from both legs.
 *
 * <p>R-I18 calls this the honest demonstration that conversion is not
 * bijective, and it is meant to be run rather than argued about: convert a real
 * file, convert it back, and look at what changed. A beneficiary name loses its
 * kanji on the way out and does not get them back; a year is added on the way
 * out and dropped on the way in; an {@code EndToEndId} that fits in
 * thirty-five characters does not fit in ten.
 *
 * <p>{@link #isByteIdentical()} is therefore usually false, and that is the
 * point. When it is true it is worth knowing — it means the file used nothing
 * the mapping cannot carry.
 *
 * @param original     the file that went in
 * @param intermediate what it became in ISO 20022
 * @param result       what came back
 * @param loss         both legs, in order
 * @since 0.5.0
 */
public record RoundTripResult(
        ZenginFile original,
        ZediFile intermediate,
        ZenginFile result,
        MappingLossReport loss) {

    /**
     * Validates the result.
     *
     * @throws NullPointerException if any component is null
     */
    public RoundTripResult {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(intermediate, "intermediate");
        Objects.requireNonNull(result, "result");
        Objects.requireNonNull(loss, "loss");
    }

    /**
     * Whether the file that came back is the file that went in.
     *
     * <p>Compares the records' raw bytes rather than their decoded values, for
     * the same reason the reader keeps raw bytes at all: filler and fields this
     * library does not interpret are part of what a bank receives.
     *
     * @return true if every record's bytes match, in order
     */
    public boolean isByteIdentical() {
        List<ZenginRecord> before = original.recordsInOrder();
        List<ZenginRecord> after = result.recordsInOrder();
        if (before.size() != after.size()) {
            return false;
        }
        for (int i = 0; i < before.size(); i++) {
            if (!Arrays.equals(before.get(i).rawBytes(), after.get(i).rawBytes())) {
                return false;
            }
        }
        return true;
    }

    /** @return true if nothing was lost on either leg */
    public boolean isLossless() {
        return loss.isLossless();
    }
}
