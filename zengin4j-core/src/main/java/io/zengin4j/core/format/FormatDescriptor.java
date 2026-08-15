package io.zengin4j.core.format;

import io.zengin4j.core.error.FormatDescriptorException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The complete byte layout of one Zengin file format, plus the provenance of
 * that layout.
 *
 * <p><strong>Read {@link #verified()} before trusting the offsets.</strong>
 * A descriptor is {@code verified: false} until its layout has been confirmed
 * against at least two independent published sources cited in
 * {@code docs/SOURCES.md} (R-0.1). Every descriptor shipped in 0.1.0 is
 * unverified, and the reader refuses to use one unless the caller opts in.
 *
 * <p>Immutable and thread-safe: build one, share it (R-T1).
 *
 * @param id           the stable identifier
 * @param nameJa       the Japanese product name, for example {@code 総合振込}
 * @param nameEn       the English gloss
 * @param typeCode     種別コード, the two-digit business type code
 * @param recordLength the record length in bytes; a property of the format,
 *                     never a global constant (R-C4)
 * @param verified     whether the layout is confirmed by two independent
 *                     cited sources
 * @param sources      citations supporting the layout
 * @param note         an optional remark about the state of the definition
 * @param records      the record layouts, keyed by role
 * @since 0.1.0
 */
public record FormatDescriptor(
        FormatId id,
        String nameJa,
        String nameEn,
        String typeCode,
        int recordLength,
        boolean verified,
        List<String> sources,
        Optional<String> note,
        Map<RecordKind, RecordDescriptor> records) {

    /** Minimum number of independent sources required to claim verification (R-0.1). */
    public static final int REQUIRED_SOURCES_FOR_VERIFICATION = 2;

    /**
     * Validates the descriptor and defensively copies its collections.
     *
     * @throws FormatDescriptorException if the descriptor is inconsistent or
     *                                   claims verification without sources
     */
    public FormatDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(nameJa, "nameJa");
        Objects.requireNonNull(nameEn, "nameEn");
        Objects.requireNonNull(typeCode, "typeCode");
        Objects.requireNonNull(note, "note");
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        Objects.requireNonNull(records, "records");

        if (recordLength < 1) {
            throw FormatDescriptorException.forFormat(id.value(),
                    "record length must be positive, found " + recordLength);
        }
        if (typeCode.isBlank()) {
            throw FormatDescriptorException.forFormat(id.value(), "type code must not be blank");
        }
        if (verified && sources.size() < REQUIRED_SOURCES_FOR_VERIFICATION) {
            throw FormatDescriptorException.forFormat(id.value(),
                    "verified: true requires at least " + REQUIRED_SOURCES_FOR_VERIFICATION
                            + " independent cited sources (R-0.1), found " + sources.size()
                            + ". Cite them in docs/SOURCES.md and list them under 'sources:'.");
        }
        if (!records.containsKey(RecordKind.HEADER)) {
            throw FormatDescriptorException.forFormat(id.value(),
                    "a format must declare a header record: it carries the 種別コード the reader"
                            + " dispatches on");
        }

        Map<RecordKind, RecordDescriptor> copy = new EnumMap<>(RecordKind.class);
        Map<Byte, RecordKind> byDiscriminator = new HashMap<>();
        for (Map.Entry<RecordKind, RecordDescriptor> entry : records.entrySet()) {
            RecordKind kind = entry.getKey();
            RecordDescriptor record = entry.getValue();
            if (record.kind() != kind) {
                throw FormatDescriptorException.forFormat(id.value(),
                        "record declared under '" + kind + "' reports kind '" + record.kind() + "'");
            }
            if (!record.formatId().equals(id)) {
                throw FormatDescriptorException.forFormat(id.value(),
                        "record '" + kind + "' belongs to format '" + record.formatId() + "'");
            }
            if (record.recordLength() != recordLength) {
                throw FormatDescriptorException.forFormat(id.value(),
                        "record '" + kind + "' is " + record.recordLength() + " bytes but the format is "
                                + recordLength + " bytes");
            }
            RecordKind clash = byDiscriminator.put(record.discriminator(), kind);
            if (clash != null) {
                throw FormatDescriptorException.forFormat(id.value(),
                        "records '" + clash + "' and '" + kind + "' share データ区分 '"
                                + (char) record.discriminator() + "'");
            }
            copy.put(kind, record);
        }
        records = Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the record layout for a データ区分 byte.
     *
     * @param discriminator the first byte of a record
     * @return the layout, or empty if no record kind uses that byte
     */
    public Optional<RecordDescriptor> forDiscriminator(byte discriminator) {
        for (RecordDescriptor record : records.values()) {
            if (record.discriminator() == discriminator) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the record layout for a role.
     *
     * @param kind the role
     * @return the layout, or empty if this format does not declare it
     */
    public Optional<RecordDescriptor> find(RecordKind kind) {
        return Optional.ofNullable(records.get(kind));
    }

    /**
     * Returns the record layout for a role, failing if it is absent.
     *
     * @param kind the role
     * @return the layout
     * @throws FormatDescriptorException if this format does not declare it
     */
    public RecordDescriptor record(RecordKind kind) {
        return find(kind).orElseThrow(() -> FormatDescriptorException.forFormat(id.value(),
                "format declares no '" + kind + "' record"));
    }
}
