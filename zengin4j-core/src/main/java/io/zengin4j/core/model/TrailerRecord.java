package io.zengin4j.core.model;

import io.zengin4j.core.format.RecordKind;

/// A trailer record: closes a batch with its control totals.
///
/// The values here are what the file *claims*. Comparing them against
/// what the data records actually contain is a validation concern
/// (`V-301`, `V-302`); this type does not reconcile them.
///
/// @since 0.1.0
public non-sealed interface TrailerRecord extends ZenginRecord {

    @Override
    default RecordKind kind() {
        return RecordKind.TRAILER;
    }

    /// Returns the record count the trailer declares.
    ///
    /// @return 合計件数 as declared in the file
    int recordCount();

    /// Returns the total amount the trailer declares, in whole yen.
    ///
    /// The field is `N(12)`, so the maximum representable total is
    /// ¥999,999,999,999 — a hundredfold larger than any individual amount, but
    /// still a ceiling a large batch can reach.
    ///
    /// @return 合計金額 as declared in the file
    long totalAmount();
}
