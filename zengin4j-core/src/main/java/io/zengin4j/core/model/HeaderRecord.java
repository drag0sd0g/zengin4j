package io.zengin4j.core.model;

import module java.base;
import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.format.RecordKind;

/// A header record: opens a batch and names the parties and the date.
///
/// The accessors here are the fields the 120-byte headers share. Anything
/// format-specific lives on the concrete type — and **the meaning of the
/// shared fields is not itself shared**. In 預金口座振替 the institution
/// named in the header is the collection destination, not the originator, and
/// the date is the 引落日 rather than a 振込指定日. Read the concrete type's
/// documentation before assuming a direction.
///
/// @since 0.1.0
public non-sealed interface HeaderRecord extends ZenginRecord {

    @Override
    default RecordKind kind() {
        return RecordKind.HEADER;
    }

    /// Returns the declared character encoding indicator.
    ///
    /// @return the コード区分 value; [CodeKubun#UNKNOWN] if the field is
    ///   absent or unrecognised
    CodeKubun codeKubun();

    /// Returns 委託者コード, the code identifying the entrusting party.
    ///
    /// @return the code as it appears in the file, including leading zeros;
    ///   empty if the format has no such field
    String originatorCode();

    /// Returns 委託者名, the name of the entrusting party.
    ///
    /// @return the name with trailing padding removed; empty if the format has
    ///   no such field
    String originatorName();

    /// Returns the date carried in the header, without a year.
    ///
    /// Named for what the date *does* rather than for what any one
    /// format calls it, because the formats disagree. In 総合振込 it is 振込指定日,
    /// the day funds reach the payees; in 預金口座振替 it is 引落日, the day the
    /// payers' accounts are debited and nothing reaches anybody. Calling both a
    /// value date would be wrong for one of them, and wrong in the direction
    /// that matters (OQ-6).
    ///
    /// Each generated record also carries an accessor under the name its own
    /// format uses — `valueDate()` on a 総合振込 header, `debitDate()`
    /// on a 預金口座振替 one — so code written against a concrete format reads in
    /// that format's own terms (R-D1).
    ///
    /// The field is four digits, `MMDD`. Use
    /// [io.zengin4j.core.time.MonthDayResolver] to attach a year, and read
    /// its documentation first: the two reasonable strategies disagree across
    /// the December–January boundary.
    ///
    /// @return the month and day, or empty if the field is absent, unset or not
    ///   a valid month and day
    Optional<MonthDay> effectiveDate();
}
