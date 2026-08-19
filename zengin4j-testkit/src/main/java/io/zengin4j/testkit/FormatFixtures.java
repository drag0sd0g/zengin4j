package io.zengin4j.testkit;

import module java.base;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.model.SeparatorStyle;

/// Synthetic records for one bundled format (UC-6).
///
/// **Every value produced through this interface is invented**
/// (R-L1, P1). Bank `9999`, branch `999` and accounts beginning with
/// `9` are outside the ranges Japanese institutions use; names come from a
/// fixed list of obviously fictional katakana.
///
/// The four bundled formats do not share a record layout, and this interface
/// deliberately does not pretend they do. What it shares is the *shape of the
/// question* — give me a header, some payments, a trailer that adds up —
/// while each implementation maps those to the field ids its own descriptor
/// declares. 給与振込 is not 総合振込 with three fields renamed, and a fixture
/// generator that assumed otherwise would produce files the library would never
/// write.
///
/// The direction of a payment is likewise the implementation's business:
/// 預金口座振替 debits the accounts its data records name, where 総合振込 credits
/// them. [#data(String, long, String)] names a counterparty, and which side
/// of the transaction that is depends on the format.
///
/// @since 0.3.0
public interface FormatFixtures {

    /// Returns the format these fixtures produce.
    ///
    /// @return the format id, never `null`
    FormatId formatId();

    /// Returns the descriptor these fixtures encode against.
    ///
    /// @return the descriptor, never `null`
    FormatDescriptor descriptor();

    /// Returns reader options that will accept these fixtures.
    ///
    /// Includes `allowUnverifiedFormats(true)`: every bundled
    /// descriptor is provisional, and the reader refuses one otherwise.
    ///
    /// @return reader options, never `null`
    ReaderOptions readerOptions();

    /// Builds a header record.
    ///
    /// @return the record bytes
    byte[] header();

    /// Builds the format's worked-example data record.
    ///
    /// @return the record bytes
    byte[] data();

    /// Builds a data record naming a counterparty.
    ///
    /// @param name          the counterparty name, in half-width katakana
    /// @param amount        the amount in yen
    /// @param accountNumber the seven-digit account number
    /// @return the record bytes
    byte[] data(String name, long amount, String accountNumber);

    /// Builds a data record whose name the standard would refuse.
    ///
    /// The escape hatch a validator's test suite needs: a record carrying a
    /// long vowel mark, a small kana or anything else the field rules forbid, so
    /// that the rule which reports it has something to report.
    ///
    /// @param name          the counterparty name, valid or not
    /// @param amount        the amount in yen
    /// @param accountNumber the seven-digit account number
    /// @return the record bytes
    /// @since 0.4.0
    byte[] dataUnchecked(String name, long amount, String accountNumber);

    /// Builds a trailer record.
    ///
    /// Where a format's trailer carries result totals — 預金口座振替 has four
    /// more counters than the others — they are left at zero, which is what an
    /// instruction file contains before an institution fills them in.
    ///
    /// @param recordCount the number of data records the batch holds
    /// @param totalAmount the sum of their amounts
    /// @return the record bytes
    byte[] trailer(int recordCount, long totalAmount);

    /// Builds an end record.
    ///
    /// @return the record bytes
    byte[] end();

    /// Builds a one-batch, one-payment file with CRLF separators.
    ///
    /// @return the file bytes
    byte[] file();

    /// Builds a one-batch, one-payment file.
    ///
    /// @param separator       what to write between records
    /// @param trailingEofByte whether to append `0x1A`
    /// @return the file bytes
    byte[] file(SeparatorStyle separator, boolean trailingEofByte);

    /// Builds a file with a chosen number of identical payments.
    ///
    /// @param payments        how many data records to write
    /// @param separator       what to write between records
    /// @param trailingEofByte whether to append `0x1A`
    /// @return the file bytes
    byte[] file(int payments, SeparatorStyle separator, boolean trailingEofByte);

    /// Returns fixtures for a bundled format.
    ///
    /// @param id the format
    /// @return the fixtures
    /// @throws IllegalArgumentException if no fixtures exist for that format,
    ///   naming the ones that do
    static FormatFixtures forFormat(FormatId id) {
        return Fixtures.forFormat(id);
    }

    /// Returns every format these fixtures can produce, in a stable order.
    ///
    /// @return the supported format ids, never empty
    static List<FormatId> supported() {
        return Fixtures.supported();
    }
}
