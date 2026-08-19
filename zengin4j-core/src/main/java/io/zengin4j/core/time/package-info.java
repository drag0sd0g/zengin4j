/// Year inference for the yearless dates the formats carry (§11.2).
///
/// 振込指定日 and 引落日 are four digits: month and day. There is no year
/// anywhere in the record. The library parses them to [java.time.MonthDay]
/// and makes attaching a year an explicit, named decision, because the two
/// reasonable strategies disagree across the December–January boundary and
/// neither is right for every file.
///
/// @since 0.1.0
package io.zengin4j.core.time;
