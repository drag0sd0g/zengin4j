package io.zengin4j.cli.internal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Lines two files' records up so a diff can compare like with like.
 *
 * <p>Positional pairing — record 3 against record 3 — is the obvious approach
 * and the wrong one: inserting a single payment near the top makes every later
 * record look changed, which is precisely the edit somebody most wants to see
 * clearly. So this is a longest-common-subsequence alignment over whole
 * records, the same thing a text diff does over lines.
 *
 * <p>Records are matched on their <em>bytes</em>. Two records that differ
 * anywhere are different records, which keeps the alignment honest: the pairing
 * step never has to guess what "the same payment" means, and guessing wrong
 * would attribute a changed amount to the wrong beneficiary.
 *
 * @since 0.3.0
 */
public final class RecordAlignment {

    /** What happened to a record between the two files. */
    public enum Change {

        /** Present in both, unchanged. */
        SAME,

        /** Present only in the second file. */
        ADDED,

        /** Present only in the first file. */
        REMOVED,

        /** Aligned with a record in the same position that differs. */
        CHANGED
    }

    /**
     * One aligned pair.
     *
     * @param change      what happened
     * @param leftNumber  the record number in the first file, or {@code 0}
     * @param rightNumber the record number in the second file, or {@code 0}
     * @param left        the first file's bytes, or {@code null} when added
     * @param right       the second file's bytes, or {@code null} when removed
     */
    public record Pair(Change change, int leftNumber, int rightNumber, byte[] left, byte[] right) {
    }

    private RecordAlignment() {
    }

    /**
     * Aligns two lists of records.
     *
     * <p>A removal immediately followed by an addition is reported as one
     * {@link Change#CHANGED} pair rather than two entries: an edited payment is
     * one event, and showing it as a deletion plus an insertion makes the
     * reader do the pairing themselves.
     *
     * @param before the first file's records, in order
     * @param after  the second file's records, in order
     * @return the aligned pairs, in order
     */
    public static List<Pair> align(List<byte[]> before, List<byte[]> after) {
        // Records that match at both ends need no alignment, and stripping them
        // is what makes this affordable in practice: the usual edit changes a
        // handful of payments in a file of thousands, leaving a middle small
        // enough that the table below is trivial. Without it, an 8,000-record
        // file needs a 64-million-cell table and dies.
        int head = 0;
        int limit = Math.min(before.size(), after.size());
        while (head < limit && Arrays.equals(before.get(head), after.get(head))) {
            head++;
        }
        int tail = 0;
        while (tail < limit - head
                && Arrays.equals(before.get(before.size() - 1 - tail),
                        after.get(after.size() - 1 - tail))) {
            tail++;
        }

        List<byte[]> middleBefore = before.subList(head, before.size() - tail);
        List<byte[]> middleAfter = after.subList(head, after.size() - tail);

        List<Pair> pairs = new ArrayList<>(before.size() + after.size());
        for (int i = 0; i < head; i++) {
            pairs.add(new Pair(Change.SAME, i + 1, i + 1, before.get(i), after.get(i)));
        }
        pairs.addAll(alignMiddle(middleBefore, middleAfter, head));
        for (int i = 0; i < tail; i++) {
            int leftNumber = before.size() - tail + i + 1;
            int rightNumber = after.size() - tail + i + 1;
            pairs.add(new Pair(Change.SAME, leftNumber, rightNumber,
                    before.get(leftNumber - 1), after.get(rightNumber - 1)));
        }
        return pairEdits(pairs);
    }

    /**
     * Aligns the part that actually differs.
     *
     * @param offset how many identical records were stripped from the front, so
     *               the reported record numbers stay those of the whole file
     */
    private static List<Pair> alignMiddle(List<byte[]> before, List<byte[]> after, int offset) {
        if (before.isEmpty() && after.isEmpty()) {
            return List.of();
        }
        requireAffordable(before.size(), after.size());

        int[][] lengths = lcs(before, after);
        Deque<Pair> backwards = new ArrayDeque<>();

        int i = before.size();
        int j = after.size();
        while (i > 0 && j > 0) {
            if (Arrays.equals(before.get(i - 1), after.get(j - 1))) {
                backwards.push(new Pair(Change.SAME, offset + i, offset + j,
                        before.get(i - 1), after.get(j - 1)));
                i--;
                j--;
            } else if (lengths[i - 1][j] >= lengths[i][j - 1]) {
                backwards.push(new Pair(Change.REMOVED, offset + i, 0, before.get(i - 1), null));
                i--;
            } else {
                backwards.push(new Pair(Change.ADDED, 0, offset + j, null, after.get(j - 1)));
                j--;
            }
        }
        while (i > 0) {
            backwards.push(new Pair(Change.REMOVED, offset + i, 0, before.get(i - 1), null));
            i--;
        }
        while (j > 0) {
            backwards.push(new Pair(Change.ADDED, 0, offset + j, null, after.get(j - 1)));
            j--;
        }
        return new ArrayList<>(backwards);
    }

    /**
     * How many table cells this implementation will allocate.
     *
     * <p>Sixteen million {@code int}s is 64 MB — large enough that no realistic
     * payment file reaches it after the common prefix and suffix are stripped,
     * and small enough to fail with a sentence rather than with an
     * {@link OutOfMemoryError}. The distinction matters more than the number: an
     * {@code OutOfMemoryError} escapes as an uncaught {@code Error}, which exits
     * with the same status as "the files differ".
     */
    static final long MAX_TABLE_CELLS = 16L * 1024 * 1024;

    private static void requireAffordable(int before, int after) {
        long cells = (long) (before + 1) * (after + 1);
        if (cells > MAX_TABLE_CELLS) {
            throw new TooLargeToAlignException(before, after, cells);
        }
    }

    /**
     * Raised when two files differ too extensively to align field by field.
     *
     * <p>Note what it takes to get here: the common prefix and suffix have
     * already been stripped, so this means thousands of records differ, and a
     * field-level diff of thousands of changed payments is not a thing anybody
     * reads. The useful answer at that size is the counts, which
     * {@code zengin validate} and {@code zengin inspect} give per file.
     *
     * @since 0.3.0
     */
    public static final class TooLargeToAlignException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        TooLargeToAlignException(int before, int after, long cells) {
            super("these files differ across " + before + " and " + after + " records, which needs "
                    + "an alignment table of " + cells + " cells — more than this implementation "
                    + "allocates (" + MAX_TABLE_CELLS + "). Records matching at the start and end "
                    + "have already been discounted, so the files differ almost everywhere; "
                    + "compare them per file with `zengin inspect` instead.");
        }
    }

    /**
     * Turns removals and additions that occupy the same gap into edits.
     *
     * <p>The backtrack emits them in <em>runs</em> — four removals then four
     * additions, not four alternating pairs — so matching only immediate
     * neighbours would report eight events where a reader sees four edited
     * payments. Each run of differences is therefore taken as a whole and its
     * removals paired positionally with its additions, leaving whichever side
     * is longer to report the genuine additions or deletions.
     *
     * <p>Positional pairing within a run is a heuristic, and the same one every
     * line-based diff uses for "changed". It can mis-pair when several records
     * change at once in different ways; the field-level output makes that
     * visible rather than hiding it, since a mis-pairing shows up as a record
     * where every field differs.
     */
    private static List<Pair> pairEdits(List<Pair> pairs) {
        List<Pair> merged = new ArrayList<>(pairs.size());
        int index = 0;
        while (index < pairs.size()) {
            if (pairs.get(index).change() == Change.SAME) {
                merged.add(pairs.get(index));
                index++;
                continue;
            }

            int runEnd = index;
            while (runEnd < pairs.size() && pairs.get(runEnd).change() != Change.SAME) {
                runEnd++;
            }
            List<Pair> removed = new ArrayList<>();
            List<Pair> added = new ArrayList<>();
            for (Pair pair : pairs.subList(index, runEnd)) {
                (pair.change() == Change.REMOVED ? removed : added).add(pair);
            }

            int edits = Math.min(removed.size(), added.size());
            for (int i = 0; i < edits; i++) {
                merged.add(new Pair(Change.CHANGED,
                        removed.get(i).leftNumber(), added.get(i).rightNumber(),
                        removed.get(i).left(), added.get(i).right()));
            }
            merged.addAll(removed.subList(edits, removed.size()));
            merged.addAll(added.subList(edits, added.size()));
            index = runEnd;
        }
        return merged;
    }

    /**
     * The classic LCS table.
     *
     * <p>{@code O(n·m)} in time and space. A Zengin file is thousands of
     * records rather than millions, so the simple algorithm is the right one —
     * and it is the one whose behaviour a reader of this code can predict.
     */
    private static int[][] lcs(List<byte[]> before, List<byte[]> after) {
        int[][] lengths = new int[before.size() + 1][after.size() + 1];
        for (int i = 1; i <= before.size(); i++) {
            for (int j = 1; j <= after.size(); j++) {
                lengths[i][j] = Arrays.equals(before.get(i - 1), after.get(j - 1))
                        ? lengths[i - 1][j - 1] + 1
                        : Math.max(lengths[i - 1][j], lengths[i][j - 1]);
            }
        }
        return lengths;
    }
}
