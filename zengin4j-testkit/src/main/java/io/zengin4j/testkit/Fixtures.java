package io.zengin4j.testkit;

import io.zengin4j.core.format.FormatId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Looks up fixtures by format id.
 *
 * <p>The registry backing {@link FormatFixtures#forFormat}. Kept package-private
 * so there is one way in, and ordered so {@code zengin generate --help} lists
 * the formats the same way every time.
 */
final class Fixtures {

    private static final Map<FormatId, Supplier<FormatFixtures>> BY_ID = registry();

    private Fixtures() {
    }

    private static Map<FormatId, Supplier<FormatFixtures>> registry() {
        Map<FormatId, Supplier<FormatFixtures>> map = new LinkedHashMap<>();
        map.put(SougouFurikomiFixtures.FORMAT, SougouFurikomiFixtures::create);
        map.put(KyuyoFurikomiFixtures.KYUYO, KyuyoFurikomiFixtures::kyuyo);
        map.put(KyuyoFurikomiFixtures.SHOYO, KyuyoFurikomiFixtures::shoyo);
        map.put(KouzaFurikaeFixtures.FORMAT, KouzaFurikaeFixtures::create);
        return map;
    }

    static FormatFixtures forFormat(FormatId id) {
        Objects.requireNonNull(id, "id");
        Supplier<FormatFixtures> supplier = BY_ID.get(id);
        if (supplier == null) {
            throw new IllegalArgumentException("no fixtures for format '" + id.value()
                    + "'; the testkit produces " + names());
        }
        return supplier.get();
    }

    static List<FormatId> supported() {
        return List.copyOf(BY_ID.keySet());
    }

    private static String names() {
        return String.join(", ", BY_ID.keySet().stream().map(FormatId::value).toList());
    }
}
