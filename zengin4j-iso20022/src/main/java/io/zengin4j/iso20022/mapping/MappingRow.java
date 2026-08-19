package io.zengin4j.iso20022.mapping;

import module java.base;
import io.zengin4j.core.loss.LossKind;
import io.zengin4j.core.loss.LossSeverity;

/// One declared correspondence between a Zengin field and an ISO 20022 element.
///
/// Rows are data, generated from `zengin4j-iso20022/mappings/*.yaml`
/// for the same reason format descriptors are: a mapping scattered through a
/// hundred lines of imperative code is a mapping nobody can review, and R-I19
/// requires each row to carry its own verification status into the generated
/// documentation.
///
/// A row is not executable. It records what the mapper is supposed to do, and
/// a test checks that the mapper does exactly that and nothing else — every
/// element the mapper emits has a row, and every `to-iso` row produces an
/// element. That is what makes the declaration and the code impossible to drift
/// apart, without the indirection of a rule engine that interprets paths at
/// runtime.
///
/// @param zenginField  `header.originatorName` and the like, empty when
///   the element has no fixed-length source
/// @param isoPath      the path below `Document`, empty when the field is
///   dropped
/// @param direction    which leg or legs this applies to
/// @param verified     whether the row has been checked against published
///   profile documentation (R-I19)
/// @param lossKind     the loss this row inherently causes, if any
/// @param lossSeverity how bad that loss is
/// @param whyEn        why it works this way, in English
/// @param whyJa        why it works this way, in Japanese
/// @since 0.5.0
public record MappingRow(
        String zenginField,
        String isoPath,
        MappingDirection direction,
        boolean verified,
        Optional<LossKind> lossKind,
        Optional<LossSeverity> lossSeverity,
        String whyEn,
        String whyJa) {

    /// Marks a side of the mapping that carries nothing.
    public static final String NONE = "-";

    /// Validates the row.
    ///
    /// @throws NullPointerException     if any component is null
    /// @throws IllegalArgumentException if both sides are empty, or a loss kind
    ///   appears without a severity
    public MappingRow {
        Objects.requireNonNull(zenginField, "zenginField");
        Objects.requireNonNull(isoPath, "isoPath");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(lossKind, "lossKind");
        Objects.requireNonNull(lossSeverity, "lossSeverity");
        Objects.requireNonNull(whyEn, "whyEn");
        Objects.requireNonNull(whyJa, "whyJa");
        if (zenginField.isEmpty() && isoPath.isEmpty()) {
            throw new IllegalArgumentException(
                    "a mapping row with neither a Zengin field nor an ISO path maps nothing");
        }
        if (lossKind.isPresent() != lossSeverity.isPresent()) {
            throw new IllegalArgumentException(
                    "a loss kind and a severity go together: " + lossKind + " / " + lossSeverity);
        }
    }

    /// @return true if a Zengin field feeds this row
    public boolean hasZenginField() {
        return !zenginField.isEmpty();
    }

    /// @return true if an ISO 20022 element carries this row
    public boolean hasIsoPath() {
        return !isoPath.isEmpty();
    }

    /// @return true if the Zengin field reaches no ISO element at all
    public boolean isDropped() {
        return hasZenginField() && !hasIsoPath();
    }

    /// The record the Zengin field belongs to.
    ///
    /// @return `header`, `data` or `trailer`, or empty when
    ///   the row has no Zengin field
    public Optional<String> zenginRecord() {
        int dot = zenginField.indexOf('.');
        return dot < 0 ? Optional.empty() : Optional.of(zenginField.substring(0, dot));
    }

    /// The Zengin field's id, without its record.
    ///
    /// @return the field id, or empty when the row has no Zengin field
    public Optional<String> zenginFieldId() {
        int dot = zenginField.indexOf('.');
        return dot < 0 ? Optional.empty() : Optional.of(zenginField.substring(dot + 1));
    }

    /// The last element of the ISO path.
    ///
    /// @return the element name, or empty when the row has no ISO path
    public Optional<String> isoElement() {
        if (!hasIsoPath()) {
            return Optional.empty();
        }
        int slash = isoPath.lastIndexOf('/');
        return Optional.of(slash < 0 ? isoPath : isoPath.substring(slash + 1));
    }

    @Override
    public String toString() {
        return (hasZenginField() ? zenginField : NONE) + " -> "
                + (hasIsoPath() ? isoPath : NONE);
    }
}
