/**
 * Bidirectional mapping between Zengin records and the ISO 20022 messages of
 * the ZEDI profile, with mandatory loss reporting.
 *
 * <p>The XML is read and written by hand against {@code java.xml}, so this
 * module has no runtime dependencies either — see
 * {@code docs/adr/0031-hand-written-iso20022-xml.md}. R-M3 permits it an XML
 * dependency; it turns out not to need one.
 *
 * @since 0.5.0
 */
// javac warns that a module name component should avoid terminal digits, in case
// a trailing number is mistaken for a version. Here "20022" is the standard's
// name, not a version, and every package in this module is already called that.
// Suppressed deliberately: a warning nobody is permitted to fix is a warning
// everyone learns to scroll past, which is how the last one survived.
@SuppressWarnings("module")
module io.zengin4j.iso20022 {
    requires transitive io.zengin4j.core;
    requires transitive io.zengin4j.validation;
    requires java.xml;

    exports io.zengin4j.iso20022.api;
    exports io.zengin4j.iso20022.envelope;
    exports io.zengin4j.iso20022.loss;
    exports io.zengin4j.iso20022.mapping;
    exports io.zengin4j.iso20022.mapping.generated;
    exports io.zengin4j.iso20022.pain001;

    // The mapping covers a subset of pain.001, and no row of it is confirmed
    // against two independent sources yet. A caller who needs an element the
    // mapping does not carry should be able to reach it rather than fork the
    // library, so the element tree is part of the API rather than an
    // implementation detail.
    exports io.zengin4j.iso20022.xml;
}
