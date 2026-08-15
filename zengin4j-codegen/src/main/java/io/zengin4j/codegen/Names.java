package io.zengin4j.codegen;

import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;

/**
 * Naming rules shared by the source and documentation generators.
 *
 * <p>One rule underlies all of them: <strong>a record component is named after
 * its field id</strong>. That is what lets the role interfaces be satisfied
 * without the generator inferring anything — a descriptor whose header
 * declares {@code originatorCode} produces a record with
 * {@code originatorCode()}, which is exactly what {@code HeaderRecord}
 * promises.
 *
 * <p>The exception is a field carrying a declared interpretation whose typed
 * accessor would collide with the raw one. There the component takes a
 * {@code Raw} suffix and the plain name belongs to the typed accessor:
 * {@code valueDateRaw()} returns {@code "0930"}, {@code valueDate()} returns a
 * {@code MonthDay}.
 */
final class Names {

    private Names() {
    }

    static String typeName(FormatId formatId, RecordKind kind) {
        return formatId.toTypeNamePrefix() + capitalise(kind.name().toLowerCase(java.util.Locale.ROOT));
    }

    static String factoryTypeName(FormatId formatId) {
        return formatId.toTypeNamePrefix() + "Records";
    }

    static String componentName(FieldDescriptor field) {
        return keepsRawComponent(field) ? field.id() + "Raw" : field.id();
    }

    static String componentType(FieldDescriptor field) {
        if (field.hasFormat(FieldFormat.AMOUNT)) {
            return "long";
        }
        if (field.hasFormat(FieldFormat.COUNT)) {
            return "int";
        }
        return "String";
    }

    /**
     * Reports whether the field's value survives as raw text.
     *
     * <p>{@code MMDD} and コード区分 keep their raw form because the typed
     * value cannot round-trip: {@code "0000"} and {@code "1332"} both decode
     * to no date at all. An amount does not need one — a {@code long} plus the
     * field length reproduces the zero-padded digits exactly.
     */
    static boolean keepsRawComponent(FieldDescriptor field) {
        return field.hasFormat(FieldFormat.MMDD) || field.hasFormat(FieldFormat.CODE_KUBUN);
    }

    static String constantPrefix(FieldDescriptor field) {
        StringBuilder result = new StringBuilder();
        String id = field.id();
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (Character.isUpperCase(c) && i > 0 && !Character.isUpperCase(id.charAt(i - 1))) {
                result.append('_');
            }
            result.append(Character.toUpperCase(c));
        }
        return result.toString();
    }

    static String capitalise(String value) {
        return value.isEmpty() ? value : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
