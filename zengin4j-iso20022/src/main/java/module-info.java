/**
 * Bidirectional mapping between Zengin records and the ISO 20022 messages of
 * the ZEDI profile, with mandatory loss reporting.
 *
 * <p>Empty until Epic 7. This is the only module permitted an XML dependency
 * (R-M3); all JAXB usage must stay inside it.
 *
 * @since 0.1.0
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
}
