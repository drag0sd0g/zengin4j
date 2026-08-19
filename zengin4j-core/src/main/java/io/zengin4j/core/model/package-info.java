/// The domain model (§11).
///
/// Format-shaped, not idealised (R-D1). The concrete record types are
/// generated from the descriptors and carry exactly the fields their record
/// carries, in the order it carries them. Every record keeps its raw bytes, so
/// filler and reserved space survive a round trip verbatim (R-D5).
///
/// Types are used only where they are unambiguous and lossless: `long`
/// for yen, [java.time.MonthDay] for `MMDD`, raw `String`
/// where leading zeros or padding carry meaning (R-D4).
///
/// The fallback `Generic*` records serve descriptors registered at
/// runtime. They require the descriptor to use the conventional field ids —
/// `originatorCode`, `amount`, `recordCount`,
/// `totalAmount` and so on — because those are what the role interfaces
/// promise. A descriptor that cannot satisfy them is reported when the record
/// is materialised.
///
/// @since 0.1.0
package io.zengin4j.core.model;
