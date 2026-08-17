package io.zengin4j.testkit;

import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.codec.ReaderOptions;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.SeparatorStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What every format's fixtures do the same way.
 *
 * <p>Assembling records into a file, wiring reader options and encoding through
 * {@link SyntheticRecords} are identical whatever the layout. Choosing which
 * field ids to populate is not, and stays with each subclass — that division is
 * the point, because the tempting shortcut is to assume the layouts differ only
 * in names.
 *
 * @since 0.3.0
 */
abstract class AbstractFormatFixtures implements FormatFixtures {

    private final FormatDescriptor descriptor;
    private final ZenginCharset charset;

    AbstractFormatFixtures(FormatDescriptor descriptor, ZenginCharset charset) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.charset = Objects.requireNonNull(charset, "charset");
    }

    /** Field values for the header, keyed by field id. */
    abstract Map<String, String> headerValues();

    /** Field values for a data record, keyed by field id. */
    abstract Map<String, String> dataValues(String name, long amount, String accountNumber);

    /** Field values for the trailer, keyed by field id. */
    abstract Map<String, String> trailerValues(int recordCount, long totalAmount);

    /** The counterparty name, amount and account the no-argument {@link #data()} uses. */
    abstract String exampleName();

    abstract long exampleAmount();

    abstract String exampleAccount();

    @Override
    public final FormatId formatId() {
        return descriptor.id();
    }

    @Override
    public final FormatDescriptor descriptor() {
        return descriptor;
    }

    final ZenginCharset charset() {
        return charset;
    }

    @Override
    public final ReaderOptions readerOptions() {
        // The format is deliberately not pinned: these files carry a 種別コード
        // and detection is part of what fixtures exercise.
        return ReaderOptions.builder()
                .charset(charset)
                .allowUnverifiedFormats(true)
                .warningListener(warning -> {
                    // Fixtures are used in tests and by `zengin generate`; the
                    // unverified-format warning is expected here and would only
                    // add noise. Callers who want it can build their own options.
                })
                .build();
    }

    @Override
    public final byte[] header() {
        return SyntheticRecords.encode(descriptor.record(RecordKind.HEADER), charset, headerValues());
    }

    @Override
    public final byte[] data() {
        return data(exampleName(), exampleAmount(), exampleAccount());
    }

    @Override
    public final byte[] data(String name, long amount, String accountNumber) {
        return SyntheticRecords.encode(descriptor.record(RecordKind.DATA), charset,
                dataValues(name, amount, accountNumber));
    }

    @Override
    public final byte[] trailer(int recordCount, long totalAmount) {
        return SyntheticRecords.encode(descriptor.record(RecordKind.TRAILER), charset,
                trailerValues(recordCount, totalAmount));
    }

    @Override
    public final byte[] end() {
        return SyntheticRecords.encode(descriptor.record(RecordKind.END), charset, Map.of());
    }

    @Override
    public final byte[] file() {
        return file(SeparatorStyle.CRLF, false);
    }

    @Override
    public final byte[] file(SeparatorStyle separator, boolean trailingEofByte) {
        return SyntheticRecords.file(
                List.of(header(), data(), trailer(1, exampleAmount()), end()),
                separator, trailingEofByte);
    }

    @Override
    public final byte[] file(int payments, SeparatorStyle separator, boolean trailingEofByte) {
        List<byte[]> records = new ArrayList<>(payments + 3);
        records.add(header());
        for (int i = 0; i < payments; i++) {
            records.add(data());
        }
        records.add(trailer(payments, exampleAmount() * payments));
        records.add(end());
        return SyntheticRecords.file(records, separator, trailingEofByte);
    }
}
