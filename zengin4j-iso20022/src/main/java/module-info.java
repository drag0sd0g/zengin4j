/**
 * Bidirectional mapping between Zengin records and the ISO 20022 messages of
 * the ZEDI profile, with mandatory loss reporting.
 *
 * <p>Empty until Epic 7. This is the only module permitted an XML dependency
 * (R-M3); all JAXB usage must stay inside it.
 *
 * @since 0.1.0
 */
@SuppressWarnings("module")
module io.zengin4j.iso20022 {
    requires transitive io.zengin4j.core;
    requires transitive io.zengin4j.validation;
}
