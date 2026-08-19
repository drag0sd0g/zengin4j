/// Reading and writing the ZEDI envelope — the concatenation of a
/// `head.001` business application header with the message it introduces.
///
/// [io.zengin4j.iso20022.envelope.ZediEnvelopeReader] is the reason
/// this library exists in the form it does: a ZEDI file contains several XML
/// declarations and is therefore not a single well-formed document, so a
/// standard XML parser cannot read one at all.
///
/// @since 0.5.0
package io.zengin4j.iso20022.envelope;
