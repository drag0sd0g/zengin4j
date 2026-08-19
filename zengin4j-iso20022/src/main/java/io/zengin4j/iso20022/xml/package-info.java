/// A small XML reader and writer, sufficient for the ZEDI profile and nothing
/// more.
///
/// Exported deliberately. The mapping carries a subset of pain.001, and no
/// row of it is confirmed yet, so a caller who needs an element the mapping does
/// not carry reaches it here rather than forking the library. Written by hand
/// against java.xml so this module needs no runtime dependency (ADR-0031).
///
/// @since 0.5.0
package io.zengin4j.iso20022.xml;
