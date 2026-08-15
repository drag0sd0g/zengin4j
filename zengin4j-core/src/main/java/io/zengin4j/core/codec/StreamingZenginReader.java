package io.zengin4j.core.codec;

import io.zengin4j.core.charset.CodeKubun;
import io.zengin4j.core.charset.ZenginCharset;
import io.zengin4j.core.error.AmbiguousFormatException;
import io.zengin4j.core.error.FormatDescriptorException;
import io.zengin4j.core.error.MalformedFileException;
import io.zengin4j.core.error.UnsupportedEncodingVariantException;
import io.zengin4j.core.error.UnsupportedFormatException;
import io.zengin4j.core.error.UnverifiedFormatException;
import io.zengin4j.core.error.ZenginIOException;
import io.zengin4j.core.format.FieldDescriptor;
import io.zengin4j.core.format.FieldFormat;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.format.FormatId;
import io.zengin4j.core.format.RecordDescriptor;
import io.zengin4j.core.format.RecordKind;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.SeparatorStyle;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

/**
 * The streaming reader: a lazy view over a recycled buffer (§10).
 *
 * <p>Memory is constant regardless of file size (R-MEM6). Nothing is decoded
 * until a caller asks for it, and nothing is copied until a caller asks to
 * keep it.
 */
final class StreamingZenginReader implements ZenginReader {

    /**
     * Format detection reads the first three bytes: データ区分 then 種別コード.
     *
     * <p>This is the one place the reader assumes a layout before it knows
     * which layout applies — it has to, because the file identifies itself in
     * those bytes. The assumption holds for every 120-byte format defined
     * here; see docs/OPEN_QUESTIONS.md before extending it to the 200-byte
     * formats.
     */
    private static final byte HEADER_DISCRIMINATOR = '1';

    private static final int TYPE_CODE_OFFSET = 1;
    private static final int TYPE_CODE_LENGTH = 2;
    private static final int DETECTION_BYTES = TYPE_CODE_OFFSET + TYPE_CODE_LENGTH;

    /** Enough to identify the format and hold a first record of any plausible length. */
    private static final int BOOTSTRAP_BUFFER_BYTES = 8192;

    private final InputStream stream;
    private final ReaderOptions options;
    private final ZenginCharset charset;
    private final ParseMode mode;
    private final List<ZenginWarning> warnings = new ArrayList<>();
    private final ViewGeneration generation = new ViewGeneration();
    private final FormatDescriptor descriptor;
    private final int recordLength;

    private byte[] buffer;
    private int position;
    private int limit;
    private long consumedBeforeBuffer;
    private ParserState state = ParserState.EXPECT_HEADER;
    private int recordNumber;
    private boolean streamExhausted;
    private boolean finished;
    private boolean byteOrderMarkPresent;
    private boolean trailingEofByte;
    private SeparatorStyle separatorStyle;
    private boolean separatorsMixed;

    StreamingZenginReader(InputStream stream, ReaderOptions options) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.options = Objects.requireNonNull(options, "options");
        this.charset = options.charset();
        this.mode = options.mode();
        this.buffer = new byte[BOOTSTRAP_BUFFER_BYTES];
        fill();
        handleByteOrderMark();
        this.descriptor = resolveFormat();
        this.recordLength = options.recordLength().orElse(descriptor.recordLength());
        growBuffer();
        rejectUnsupportedEncodingVariant();
    }

    @Override
    public FormatDescriptor format() {
        return descriptor;
    }

    @Override
    public int recordLength() {
        return recordLength;
    }

    @Override
    public boolean hasNext() {
        if (finished) {
            return false;
        }
        skipFraming();
        if (finished || available() == 0) {
            finished = true;
            return false;
        }
        return true;
    }

    @Override
    public RecordView next() {
        if (!hasNext()) {
            throw new NoSuchElementException("no further records; check hasNext() first");
        }
        ensureAvailable(recordLength);
        generation.advance();
        recordNumber++;
        long offset = absoluteOffset();
        int start = position;
        int have = available();

        if (have < recordLength) {
            finished = true;
            position = limit;
            String reason = "record is truncated: " + have + " of " + recordLength + " bytes present";
            if (mode == ParseMode.STRICT) {
                throw new MalformedFileException(offset, recordNumber,
                        reason + ". Read with ParseMode.LENIENT to receive it as a MalformedRecord.",
                        "レコードが途中で終了しています: " + recordLength + " バイト中 " + have
                                + " バイトのみ。ParseMode.LENIENT を指定すると MalformedRecord として"
                                + "受け取れます。");
            }
            return RecordView.malformed(buffer, start, have, descriptor, reason, charset, offset,
                    recordNumber, generation);
        }

        String reason = classify(buffer[start]);
        position = start + recordLength;
        if (reason != null) {
            if (mode == ParseMode.STRICT) {
                throw new MalformedFileException(offset, recordNumber, reason,
                        "レコード構成が不正です: " + reason);
            }
            return RecordView.malformed(buffer, start, recordLength, descriptor, reason, charset, offset,
                    recordNumber, generation);
        }
        RecordDescriptor matched = descriptor.forDiscriminator(buffer[start]).orElseThrow();
        return RecordView.wellFormed(buffer, start, recordLength, descriptor, matched, charset, offset,
                recordNumber, generation);
    }

    @Override
    public FileFraming framing() {
        SeparatorStyle style;
        if (separatorsMixed) {
            style = SeparatorStyle.MIXED;
        } else {
            style = separatorStyle == null ? SeparatorStyle.NONE : separatorStyle;
        }
        return new FileFraming(byteOrderMarkPresent, style, trailingEofByte);
    }

    @Override
    public List<ZenginWarning> warnings() {
        return Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException e) {
            throw new ZenginIOException("closing the input stream", e);
        }
    }

    // ------------------------------------------------------------- start-up

    private void handleByteOrderMark() {
        if (!RecordFramer.startsWithByteOrderMark(buffer, position, limit)) {
            return;
        }
        byteOrderMarkPresent = true;
        if (options.byteOrderMark() == ByteOrderMarkPolicy.REJECT) {
            throw new MalformedFileException(0, 0,
                    "the file begins with a UTF-8 byte order mark, which is never valid in a fixed-length"
                            + " Zengin file and usually means the file has been re-encoded by a text editor."
                            + " Set ReaderOptions.byteOrderMark(STRIP) to skip it.",
                    "ファイル先頭に UTF-8 の BOM があります。全銀フォーマットでは無効であり、テキスト"
                            + "エディタで再保存された可能性があります。ReaderOptions.byteOrderMark(STRIP)"
                            + " で読み飛ばせます。");
        }
        position += RecordFramer.BYTE_ORDER_MARK.length;
        warn(ZenginWarning.BYTE_ORDER_MARK_STRIPPED,
                "skipped a UTF-8 byte order mark at the start of the file; the content may have been"
                        + " re-encoded, so check the character set of the text fields",
                "ファイル先頭の UTF-8 BOM を読み飛ばしました。文字コードが変換されている可能性があるため、"
                        + "文字項目の内容を確認してください。",
                0);
    }

    private FormatDescriptor resolveFormat() {
        FormatDescriptor resolved = options.format().isPresent()
                ? byId(options.format().get())
                : detectFromHeader();
        if (!resolved.verified()) {
            if (!options.allowUnverifiedFormats()) {
                throw new UnverifiedFormatException(resolved.id().value());
            }
            warn(ZenginWarning.UNVERIFIED_FORMAT,
                    "reading with format '" + resolved.id() + "', whose byte layout has not been confirmed"
                            + " against two independent published sources; validate the results against your"
                            + " own institution's specification",
                    "フォーマット '" + resolved.id() + "' のバイト配置は独立した 2 つの公開資料で確認されて"
                            + "いません。取引金融機関の仕様書と照合してください。",
                    0);
        }
        return resolved;
    }

    private FormatDescriptor byId(FormatId id) {
        return options.registry().byId(id).orElseThrow(() -> FormatDescriptorException.forFormat(id.value(),
                "format is not registered; registered formats: " + options.registry().describeTypeCodes()));
    }

    private FormatDescriptor detectFromHeader() {
        if (available() < DETECTION_BYTES) {
            throw new MalformedFileException(absoluteOffset(), 0,
                    "the file is too short to identify a format: " + available() + " bytes, "
                            + DETECTION_BYTES + " needed to read データ区分 and 種別コード",
                    "フォーマットを判定できません。データ区分と種別コードの読み取りに " + DETECTION_BYTES
                            + " バイト必要ですが " + available() + " バイトしかありません。");
        }
        byte first = buffer[position];
        if (first != HEADER_DISCRIMINATOR) {
            throw new MalformedFileException(absoluteOffset(), 1,
                    "a file must begin with a header record (データ区分 '1'), found '" + printable(first)
                            + "'. If this file is a fragment, name the format with"
                            + " ReaderOptions.builder().format(...).",
                    "ファイルはヘッダーレコード (データ区分 '1') で始まる必要がありますが '" + printable(first)
                            + "' が見つかりました。断片を読む場合は ReaderOptions.builder().format(...) で"
                            + "フォーマットを指定してください。");
        }
        String typeCode = charset.decode(buffer, position + TYPE_CODE_OFFSET, TYPE_CODE_LENGTH);
        List<FormatDescriptor> matches = options.registry().byTypeCode(typeCode);
        if (matches.isEmpty()) {
            throw new UnsupportedFormatException(typeCode, options.registry().describeTypeCodes());
        }
        if (matches.size() > 1) {
            throw new AmbiguousFormatException(typeCode,
                    matches.stream().map(candidate -> candidate.id().value()).toList());
        }
        return matches.get(0);
    }

    /**
     * R-C14: a file declaring EBCDIC is rejected by name. Decoding it as JIS
     * would fill every text field with plausible but wrong characters, and
     * nothing downstream would show a problem.
     */
    private void rejectUnsupportedEncodingVariant() {
        if (available() == 0 || buffer[position] != HEADER_DISCRIMINATOR) {
            return;
        }
        Optional<FieldDescriptor> field = descriptor.find(RecordKind.HEADER)
                .flatMap(header -> header.findByFormat(FieldFormat.CODE_KUBUN));
        if (field.isEmpty() || available() < field.get().endOffset()) {
            return;
        }
        FieldDescriptor codeKubun = field.get();
        String raw = charset.decode(buffer, position + codeKubun.offset(), codeKubun.length());
        CodeKubun value = CodeKubun.of(raw);
        if (value == CodeKubun.EBCDIC) {
            throw new UnsupportedEncodingVariantException(value, raw, absoluteOffset() + codeKubun.offset());
        }
    }

    // ------------------------------------------------------------- framing

    private String classify(byte discriminator) {
        Optional<RecordDescriptor> matched = descriptor.forDiscriminator(discriminator);
        if (matched.isEmpty()) {
            return "unknown データ区分 '" + printable(discriminator) + "'; format " + descriptor.id()
                    + " defines " + describeDiscriminators();
        }
        Optional<ParserState> next = state.next(matched.get().kind());
        if (next.isEmpty()) {
            return "a " + matched.get().kind() + " record cannot appear here; expected " + state.expected();
        }
        state = next.get();
        return null;
    }

    private String describeDiscriminators() {
        List<String> known = new ArrayList<>();
        for (RecordDescriptor record : descriptor.records().values()) {
            known.add("'" + (char) record.discriminator() + "' (" + record.kind() + ")");
        }
        return String.join(", ", known);
    }

    private void skipFraming() {
        while (true) {
            if (available() == 0) {
                if (streamExhausted) {
                    return;
                }
                fill();
                if (available() == 0) {
                    return;
                }
            }
            byte value = buffer[position];
            if (RecordFramer.isSeparator(value)) {
                consumeSeparatorRun();
                continue;
            }
            if (value == RecordFramer.EOF_BYTE) {
                consumeEofByte();
            }
            return;
        }
    }

    private void consumeSeparatorRun() {
        byte[] run = new byte[2];
        int count = 0;
        while (true) {
            if (available() == 0) {
                if (streamExhausted) {
                    break;
                }
                fill();
                if (available() == 0) {
                    break;
                }
            }
            byte value = buffer[position];
            if (!RecordFramer.isSeparator(value)) {
                break;
            }
            if (count < run.length) {
                run[count] = value;
            }
            count++;
            position++;
        }
        SeparatorStyle style = count <= run.length
                ? RecordFramer.classify(Arrays.copyOf(run, count)).orElse(null)
                : null;
        observeSeparator(style);
    }

    private void observeSeparator(SeparatorStyle style) {
        if (style == null) {
            markSeparatorsMixed();
            return;
        }
        if (separatorStyle == null) {
            separatorStyle = style;
        } else if (separatorStyle != style) {
            markSeparatorsMixed();
        }
    }

    private void markSeparatorsMixed() {
        if (separatorsMixed) {
            return;
        }
        separatorsMixed = true;
        warn(ZenginWarning.MIXED_SEPARATORS,
                "the file uses more than one record separator convention, so it cannot be written back"
                        + " byte for byte from a single separator setting",
                "ファイル内でレコード区切りが統一されていません。単一の区切り設定ではバイト単位で"
                        + "同一のファイルを書き戻せません。",
                absoluteOffset());
    }

    private void consumeEofByte() {
        trailingEofByte = true;
        position++;
        while (true) {
            if (available() == 0) {
                if (streamExhausted) {
                    break;
                }
                fill();
                if (available() == 0) {
                    break;
                }
            }
            if (RecordFramer.isSeparator(buffer[position])) {
                position++;
                continue;
            }
            long offset = absoluteOffset();
            if (mode == ParseMode.STRICT) {
                throw new MalformedFileException(offset, recordNumber,
                        "data follows the end-of-file marker (0x1A)",
                        "ファイル終端マーカー (0x1A) の後にデータがあります");
            }
            warn(ZenginWarning.DATA_AFTER_EOF_BYTE,
                    "ignored data following the end-of-file marker (0x1A)",
                    "ファイル終端マーカー (0x1A) 以降のデータを無視しました",
                    offset);
            break;
        }
        finished = true;
    }

    // -------------------------------------------------------------- buffer

    private int available() {
        return limit - position;
    }

    private long absoluteOffset() {
        return consumedBeforeBuffer + position;
    }

    private void ensureAvailable(int required) {
        if (available() < required && !streamExhausted) {
            fill();
        }
    }

    private void fill() {
        if (position > 0) {
            System.arraycopy(buffer, position, buffer, 0, limit - position);
            consumedBeforeBuffer += position;
            limit -= position;
            position = 0;
            // Compaction moves every retained view's bytes out from under it.
            generation.advance();
        }
        while (limit < buffer.length && !streamExhausted) {
            int read;
            try {
                read = stream.read(buffer, limit, buffer.length - limit);
            } catch (IOException e) {
                throw new ZenginIOException("reading record " + (recordNumber + 1), e);
            }
            if (read < 0) {
                streamExhausted = true;
            } else if (read == 0) {
                // A well-behaved stream cannot do this with a non-empty target,
                // but the loop must terminate regardless (INV-3).
                break;
            } else {
                limit += read;
            }
        }
    }

    private void growBuffer() {
        long desired = Math.min((long) recordLength * options.bufferRecords(), Integer.MAX_VALUE / 2);
        int target = (int) Math.max(desired, recordLength + RecordFramer.BYTE_ORDER_MARK.length + 2L);
        if (target <= buffer.length) {
            return;
        }
        byte[] bigger = new byte[target];
        System.arraycopy(buffer, position, bigger, 0, limit - position);
        consumedBeforeBuffer += position;
        limit -= position;
        position = 0;
        buffer = bigger;
        generation.advance();
    }

    private void warn(String code, String messageEn, String messageJa, long offset) {
        ZenginWarning warning = new ZenginWarning(code, messageEn, messageJa, offset);
        warnings.add(warning);
        options.warningListener().accept(warning);
    }

    private static String printable(byte value) {
        int unsigned = value & 0xFF;
        return unsigned >= 0x20 && unsigned <= 0x7E
                ? String.valueOf((char) unsigned)
                : String.format("0x%02X", unsigned);
    }
}
