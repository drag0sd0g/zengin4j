package io.zengin4j.cli.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import io.zengin4j.cli.internal.RecordAlignment.Change;
import io.zengin4j.cli.internal.RecordAlignment.Pair;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The alignment, which is the whole reason {@code diff} is useful.
 *
 * <p>The behaviour worth pinning is the one positional pairing gets wrong:
 * inserting a record near the top must not report every later record as
 * changed. That is the edit somebody most wants to see clearly, and the naive
 * implementation is at its worst exactly there.
 */
class RecordAlignmentTest {

    private static List<byte[]> records(String... lines) {
        return java.util.Arrays.stream(lines)
                .map(line -> line.getBytes(StandardCharsets.ISO_8859_1))
                .toList();
    }

    private static List<Change> changes(List<Pair> pairs) {
        return pairs.stream().map(Pair::change).toList();
    }

    @Test
    void identicalFilesAreAllSame() {
        List<Pair> pairs = RecordAlignment.align(records("a", "b", "c"), records("a", "b", "c"));

        assertThat(changes(pairs)).containsExactly(Change.SAME, Change.SAME, Change.SAME);
    }

    @Test
    void anInsertionNearTheTopDoesNotShiftEverythingAfterIt() {
        List<Pair> pairs = RecordAlignment.align(
                records("header", "a", "b", "c", "trailer"),
                records("header", "NEW", "a", "b", "c", "trailer"));

        assertThat(changes(pairs))
                .as("positional pairing would call a, b, c and trailer all changed")
                .containsExactly(Change.SAME, Change.ADDED, Change.SAME, Change.SAME,
                        Change.SAME, Change.SAME);
    }

    @Test
    void aDeletionNearTheTopBehavesTheSameWay() {
        List<Pair> pairs = RecordAlignment.align(
                records("header", "gone", "a", "b", "trailer"),
                records("header", "a", "b", "trailer"));

        assertThat(changes(pairs)).containsExactly(Change.SAME, Change.REMOVED, Change.SAME,
                Change.SAME, Change.SAME);
    }

    @Test
    void anEditedRecordIsOneChangeNotADeletionAndAnInsertion() {
        List<Pair> pairs = RecordAlignment.align(
                records("header", "a", "trailer"),
                records("header", "A", "trailer"));

        assertThat(changes(pairs)).containsExactly(Change.SAME, Change.CHANGED, Change.SAME);
        Pair changed = pairs.get(1);
        assertThat(changed.leftNumber()).isEqualTo(2);
        assertThat(changed.rightNumber()).isEqualTo(2);
    }

    /**
     * The case that motivated pairing whole runs rather than adjacent entries:
     * the backtrack emits four removals then four additions, and matching only
     * neighbours reported eight events where a reader sees four edits.
     */
    @Test
    void aRunOfEditsIsReportedAsEditsNotAsAdditionsAndRemovals() {
        List<Pair> pairs = RecordAlignment.align(
                records("header", "a", "b", "c", "d", "trailer"),
                records("header", "A", "B", "C", "D", "trailer"));

        assertThat(changes(pairs)).containsExactly(Change.SAME,
                Change.CHANGED, Change.CHANGED, Change.CHANGED, Change.CHANGED, Change.SAME);
    }

    @Test
    void anUnevenRunPairsWhatItCanAndReportsTheRest() {
        List<Pair> pairs = RecordAlignment.align(
                records("header", "a", "b", "trailer"),
                records("header", "A", "B", "C", "trailer"));

        assertThat(changes(pairs)).containsExactly(Change.SAME,
                Change.CHANGED, Change.CHANGED, Change.ADDED, Change.SAME);
    }

    @Test
    void anEmptyFileOnEitherSideIsHandled() {
        assertThat(changes(RecordAlignment.align(List.of(), records("a", "b"))))
                .containsExactly(Change.ADDED, Change.ADDED);
        assertThat(changes(RecordAlignment.align(records("a", "b"), List.of())))
                .containsExactly(Change.REMOVED, Change.REMOVED);
        assertThat(RecordAlignment.align(List.of(), List.of())).isEmpty();
    }

    @Test
    void recordNumbersReferToTheFileTheRecordCameFrom() {
        List<Pair> pairs = RecordAlignment.align(
                records("a", "b", "c"),
                records("a", "c"));

        Pair removed = pairs.stream()
                .filter(pair -> pair.change() == Change.REMOVED)
                .findFirst().orElseThrow();
        assertThat(removed.leftNumber()).isEqualTo(2);
        assertThat(removed.rightNumber()).as("it is in neither position of the second file")
                .isZero();
    }

    // ------------------------------------------------------------------ size

    /**
     * A big file with one edit costs almost nothing.
     *
     * <p>The table is {@code O(n·m)}, so aligning two 8,000-record files
     * naively needs 64 million cells and dies under any ordinary heap.
     * Stripping the records that match at both ends first is what makes the
     * realistic case — a handful of payments changed in a file of thousands —
     * affordable, and this is the test that says so.
     */
    @Test
    void aLargeFileWithOneInsertionAlignsWithoutBuildingAHugeTable() {
        List<byte[]> before = many(8000);
        List<byte[]> after = new java.util.ArrayList<>(before);
        after.add(4000, "inserted".getBytes(StandardCharsets.ISO_8859_1));

        List<Pair> pairs = RecordAlignment.align(before, after);

        assertThat(pairs).hasSize(8001);
        assertThat(changes(pairs).stream().filter(c -> c == Change.ADDED).count()).isEqualTo(1);
        assertThat(pairs.get(4000).change()).isEqualTo(Change.ADDED);
        assertThat(pairs.get(4000).rightNumber()).isEqualTo(4001);
    }

    @Test
    void identicalLargeFilesAreAllSameAndCostNothing() {
        List<byte[]> records = many(20_000);

        List<Pair> pairs = RecordAlignment.align(records, records);

        assertThat(pairs).hasSize(20_000);
        assertThat(pairs).allMatch(pair -> pair.change() == Change.SAME);
    }

    /**
     * Files that differ almost everywhere are refused, not attempted.
     *
     * <p>An {@code OutOfMemoryError} here would escape as an uncaught
     * {@code Error} and exit the JVM with status 1 — which in this tool means
     * "the files differ", so a crash would be indistinguishable from a
     * successful comparison that found changes.
     */
    @Test
    void filesThatDifferEverywhereAreRefusedRatherThanAttempted() {
        List<byte[]> before = many(5000);
        List<byte[]> after = new java.util.ArrayList<>();
        for (int i = 0; i < 5000; i++) {
            after.add(("other-" + i).getBytes(StandardCharsets.ISO_8859_1));
        }

        assertThatExceptionOfType(RecordAlignment.TooLargeToAlignException.class)
                .isThrownBy(() -> RecordAlignment.align(before, after))
                .withMessageContaining("differ across")
                .withMessageContaining("zengin inspect");
    }

    /** The stated limit is the one the code enforces. */
    @Test
    void theLimitIsWhatTheMessageClaims() {
        assertThat(RecordAlignment.MAX_TABLE_CELLS).isEqualTo(16L * 1024 * 1024);
    }

    private static List<byte[]> many(int count) {
        List<byte[]> records = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            records.add(("record-" + i).getBytes(StandardCharsets.ISO_8859_1));
        }
        return records;
    }

    @Test
    void everyRecordIsAccountedForExactlyOnce() {
        List<byte[]> before = records("a", "b", "c", "d");
        List<byte[]> after = records("a", "x", "c", "d", "e");

        List<Pair> pairs = RecordAlignment.align(before, after);

        long fromBefore = pairs.stream()
                .filter(pair -> pair.change() != Change.ADDED).count();
        long fromAfter = pairs.stream()
                .filter(pair -> pair.change() != Change.REMOVED).count();
        assertThat(fromBefore).isEqualTo(before.size());
        assertThat(fromAfter).isEqualTo(after.size());
    }
}
