package io.zengin4j.core.codec;

import io.zengin4j.core.error.MalformedFieldException;
import io.zengin4j.core.format.FormatDescriptor;
import io.zengin4j.core.model.Batch;
import io.zengin4j.core.model.DataRecord;
import io.zengin4j.core.model.EndRecord;
import io.zengin4j.core.model.FileFraming;
import io.zengin4j.core.model.HeaderRecord;
import io.zengin4j.core.model.MalformedRecord;
import io.zengin4j.core.model.TrailerRecord;
import io.zengin4j.core.model.ZenginRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Groups the record stream into batches, materialising as it goes.
 */
final class MaterialisingBatchReader implements BatchReader {

    private final ZenginReader reader;
    private final ParseMode mode;
    private final List<MalformedRecord> unbatched = new ArrayList<>();

    private Optional<EndRecord> endRecord = Optional.empty();
    private HeaderRecord carried;
    private Batch pending;
    private boolean drained;

    MaterialisingBatchReader(ZenginReader reader, ParseMode mode) {
        this.reader = reader;
        this.mode = mode;
    }

    @Override
    public FormatDescriptor format() {
        return reader.format();
    }

    @Override
    public boolean hasNext() {
        advance();
        return pending != null;
    }

    @Override
    public Batch next() {
        advance();
        if (pending == null) {
            throw new NoSuchElementException("no further batches; check hasNext() first");
        }
        Batch result = pending;
        pending = null;
        return result;
    }

    @Override
    public Optional<EndRecord> endRecord() {
        return endRecord;
    }

    @Override
    public List<MalformedRecord> unbatched() {
        return Collections.unmodifiableList(new ArrayList<>(unbatched));
    }

    @Override
    public FileFraming framing() {
        return reader.framing();
    }

    @Override
    public List<ZenginWarning> warnings() {
        return reader.warnings();
    }

    @Override
    public void close() {
        reader.close();
    }

    private void advance() {
        if (pending != null || drained) {
            return;
        }
        pending = readBatch();
        if (pending == null) {
            drained = true;
        }
    }

    private Batch readBatch() {
        HeaderRecord header = carried;
        carried = null;
        List<DataRecord> data = new ArrayList<>();
        List<MalformedRecord> malformed = new ArrayList<>();

        while (reader.hasNext()) {
            ZenginRecord record = materialize(reader.next());
            switch (record) {
                case HeaderRecord found -> {
                    if (header == null) {
                        header = found;
                    } else {
                        // Only reachable in lenient mode with a batch that has
                        // no trailer; hand the header to the next batch.
                        carried = found;
                        return new Batch(header, data, Optional.empty(), malformed);
                    }
                }
                case DataRecord found -> data.add(found);
                case TrailerRecord found -> {
                    return new Batch(header, data, Optional.of(found), malformed);
                }
                case EndRecord found -> endRecord = Optional.of(found);
                case MalformedRecord found -> {
                    if (header == null) {
                        unbatched.add(found);
                    } else {
                        malformed.add(found);
                    }
                }
            }
        }
        return header == null ? null : new Batch(header, data, Optional.empty(), malformed);
    }

    private ZenginRecord materialize(RecordView view) {
        try {
            return view.materialize();
        } catch (MalformedFieldException e) {
            if (mode == ParseMode.STRICT) {
                throw e;
            }
            return new MalformedRecord(view.format().id(), view.recordNumber(), view.byteOffset(),
                    view.rawBytes(), e.messageEn());
        }
    }
}
