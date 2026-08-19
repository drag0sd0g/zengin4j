package io.zengin4j.iso20022.api;

import module java.base;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.FieldCodec;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.ZenginRecord;
import io.zengin4j.iso20022.mapping.MappingRow;

/// Reads a declared field out of a record.
///
/// The mapper works through this rather than through the generated accessors,
/// so that a mapping row saying `header.originBankCode` is what actually
/// fetches the value. The alternative — casting to `SougouFurikomiHeader`
/// and calling `originBankCode()` — would compile whatever the declaration
/// said, which would make the declaration decoration rather than code.
///
/// The cost is that a typo in the declaration becomes a runtime lookup rather
/// than a compile error. That is paid for at build time instead: the codegen
/// reader checks every row's field against the descriptor and fails the build.
final class ZenginFields {

    private final FormatDescriptor descriptor;
    private final ZenginCharset charset;
    private final List<MappingRow> rows;

    ZenginFields(FormatDescriptor descriptor, ZenginCharset charset, List<MappingRow> rows) {
        this.descriptor = descriptor;
        this.charset = charset;
        this.rows = rows;
    }

    /// The text of a field, trimmed of its padding.
    ///
    /// @param record the record to read from
    /// @param kind   which record the field belongs to
    /// @param id     the field id
    /// @return the value
    String text(ZenginRecord record, RecordKind kind, String id) {
        FieldDescriptor field = descriptor.record(kind).field(id);
        return FieldCodec.decodeField(record.rawBytes(), 0, field, charset).trim();
    }

    /// The declared byte length of a field, which is what a name has to fit into.
    ///
    /// @param kind which record the field belongs to
    /// @param id   the field id
    /// @return the length in bytes
    int length(RecordKind kind, String id) {
        return descriptor.record(kind).field(id).length();
    }

    /// The descriptor of a field, for its character class and type.
    ///
    /// @param kind which record the field belongs to
    /// @param id   the field id
    /// @return the descriptor
    FieldDescriptor field(RecordKind kind, String id) {
        return descriptor.record(kind).field(id);
    }

    /// The row that carries a Zengin field, if one does.
    ///
    /// @param reference `header.originatorName` and the like
    /// @return the row
    Optional<MappingRow> row(String reference) {
        return rows.stream()
                .filter(row -> row.zenginField().equals(reference))
                .findFirst();
    }

    /// Every row that drops its Zengin field rather than carrying it.
    ///
    /// @return the rows, in declaration order
    List<MappingRow> droppedRows() {
        return rows.stream().filter(MappingRow::isDropped).toList();
    }

    /// @return the format being mapped
    FormatDescriptor descriptor() {
        return descriptor;
    }

    /// @return the charset the fixed-length side uses
    ZenginCharset charset() {
        return charset;
    }
}
