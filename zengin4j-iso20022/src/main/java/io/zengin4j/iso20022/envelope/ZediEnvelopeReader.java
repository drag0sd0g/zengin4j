package io.zengin4j.iso20022.envelope;

import module java.base;
import io.zengin4j.core.error.ZenginIOException;
import io.zengin4j.iso20022.xml.MalformedXmlException;
import io.zengin4j.iso20022.xml.XmlElement;
import io.zengin4j.iso20022.xml.XmlParser;

/// Splits a ZEDI file into independently parseable messages.
///
/// **A ZEDI file is not a single XML document.** The profile
/// concatenates the `head.001` business application header with the
/// message body at XML-declaration granularity, so a file that carries one
/// payment instruction contains two XML declarations, and one that carries three
/// groups contains six. Handing any of them to an XML parser fails on the second
/// declaration, which is why generic ISO 20022 tooling cannot read these files
/// at all.
///
/// This reader scans the bytes for declaration boundaries, cuts the file into
/// segments at them, and parses each segment on its own.
///
/// ## Why the scan is safe
///
/// The obvious worry is a false boundary: a `<?xml` sequence occurring
/// inside a message rather than starting one, which would cut a document in
/// half. Three things bound it, and the third is what actually settles it.
///
/// - Character content cannot contain a literal `<`. Well-formed XML
///   requires it escaped as `<`, so a beneficiary name or a
///   remittance line that happens to read `<?xml` is not those bytes
///   by the time it reaches a file.
/// - The base64 alphabet — `A–Z a–z 0–9 + / =` — does not include
///   `<`, so the encoded 金融EDI payload cannot contain the sequence
///   however large it is (R-I8).
/// - Neither of those covers comments or CDATA sections, where `<?xml`
///   *is* legal. The profile uses neither, but "the profile does not
///   do that" is an assumption and not a guarantee. So the split is
///   **checked rather than assumed**: every segment must parse
///   as a well-formed document, and a false boundary necessarily produces
///   one that does not — it would end mid-element. The diagnostic names this
///   possibility explicitly.
///
/// See `docs/adr/0032-splitting-on-declaration-boundaries.md`.
///
/// @since 0.5.0
public final class ZediEnvelopeReader {

    /// The bytes that open an XML declaration. ASCII, and the same in UTF-8.
    private static final byte[] DECLARATION = "<?xml".getBytes(StandardCharsets.US_ASCII);

    private ZediEnvelopeReader() {
    }

    /// Reads a file.
    ///
    /// @param path the file to read
    /// @return the messages it contains, with their exact bytes
    /// @throws ZenginIOException     if the file cannot be read
    /// @throws MalformedXmlException if a segment is not well-formed XML
    public static ZediFile read(Path path) {
        Objects.requireNonNull(path, "path");
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new ZenginIOException("reading " + path, e);
        }
    }

    /// Reads a stream to its end.
    ///
    /// @param input the stream; the caller closes it
    /// @return the messages it contains, with their exact bytes
    /// @throws ZenginIOException     if the stream cannot be read
    /// @throws MalformedXmlException if a segment is not well-formed XML
    public static ZediFile read(InputStream input) {
        Objects.requireNonNull(input, "input");
        try {
            return read(input.readAllBytes());
        } catch (IOException e) {
            throw new ZenginIOException("reading a ZEDI stream", e);
        }
    }

    /// Reads a file already in memory.
    ///
    /// @param bytes the file's bytes
    /// @return the messages it contains, with their exact bytes
    /// @throws MalformedXmlException if a segment is not well-formed XML, or the
    ///   file contains no XML declaration at all
    public static ZediFile read(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        List<Integer> boundaries = findDeclarationBoundaries(bytes);
        if (boundaries.isEmpty()) {
            throw new MalformedXmlException(-1,
                    "no XML declaration anywhere in " + bytes.length + " bytes, so this is not "
                            + "a ZEDI file. A Zengin fixed-length file goes to ZenginReaders "
                            + "instead.",
                    bytes.length + " バイト中に XML 宣言がありません。ZEDI ファイルではありません。"
                            + "全銀固定長ファイルは ZenginReaders で読み取ってください。",
                    null);
        }

        byte[] preamble = Arrays.copyOfRange(bytes, 0, boundaries.getFirst());
        List<Segment> segments = new ArrayList<>(boundaries.size());
        for (int i = 0; i < boundaries.size(); i++) {
            int start = boundaries.get(i);
            int end = i + 1 < boundaries.size() ? boundaries.get(i + 1) : bytes.length;
            segments.add(parse(bytes, start, end, i));
        }
        return new ZediFile(preamble, pair(segments));
    }

    /// Every offset at which an XML declaration starts.
    ///
    /// @param bytes the file's bytes
    /// @return the offsets, ascending
    static List<Integer> findDeclarationBoundaries(byte[] bytes) {
        List<Integer> found = new ArrayList<>();
        outer:
        for (int i = 0; i + DECLARATION.length <= bytes.length; i++) {
            for (int j = 0; j < DECLARATION.length; j++) {
                if (bytes[i + j] != DECLARATION[j]) {
                    continue outer;
                }
            }
            found.add(i);
        }
        return found;
    }

    private static Segment parse(byte[] bytes, int start, int end, int index) {
        byte[] slice = Arrays.copyOfRange(bytes, start, end);
        try {
            return new Segment(XmlParser.parse(slice), slice);
        } catch (MalformedXmlException notWellFormed) {
            throw new MalformedXmlException(start,
                    "segment " + (index + 1) + " of this ZEDI file is not a well-formed document "
                            + "on its own. Either the file is damaged, or a '<?xml' sequence "
                            + "inside a comment or CDATA section was mistaken for the start of "
                            + "the next message. " + notWellFormed.messageEn(),
                    "この ZEDI ファイルのセグメント " + (index + 1)
                            + " は単独では整形式の XML ではありません。ファイルが破損しているか、"
                            + "コメントまたは CDATA 内の '<?xml' を次のメッセージの開始と"
                            + "誤認した可能性があります。" + notWellFormed.messageJa(),
                    notWellFormed);
        }
    }

    /// Pairs each body with the header in front of it.
    ///
    /// Pairing by root element rather than by position, so a bare body — a
    /// `pain.001` with no header, which is what most test fixtures and
    /// some senders produce — reads as a message with no header instead of being
    /// mistaken for a header with no body.
    private static List<ZediMessage> pair(List<Segment> segments) {
        List<ZediMessage> messages = new ArrayList<>();
        Segment pendingHeader = null;

        for (Segment segment : segments) {
            if (segment.isHeader()) {
                if (pendingHeader != null) {
                    throw danglingHeader();
                }
                pendingHeader = segment;
                continue;
            }
            messages.add(pendingHeader == null
                    ? ZediMessage.read(null, null, segment.root(), segment.bytes())
                    : ZediMessage.read(BusinessApplicationHeader.from(pendingHeader.root()),
                            pendingHeader.bytes(), segment.root(), segment.bytes()));
            pendingHeader = null;
        }

        if (pendingHeader != null) {
            throw danglingHeader();
        }
        return messages;
    }

    private static MalformedXmlException danglingHeader() {
        return new MalformedXmlException(-1,
                "a business application header is not followed by a message body. Every "
                        + "head.001 in the profile introduces exactly one body (R-I5), so a "
                        + "header standing alone means the file was truncated or two were "
                        + "concatenated by mistake.",
                "ビジネスアプリケーションヘッダーの後に電文本体がありません。プロファイルでは "
                        + "head.001 は必ず 1 件の本体を伴います (R-I5)。ファイルの切り詰め、"
                        + "または誤った連結が考えられます。",
                null);
    }

    /// A parsed slice of the file, and the bytes it was parsed from.
    private record Segment(XmlElement root, byte[] bytes) {
        boolean isHeader() {
            return root.name().equals(BusinessApplicationHeader.ROOT);
        }
    }
}
