package io.zengin4j.validation.calendar;

import module java.base;

/// When Japanese financial institutions move money (R-V6, R-V7).
///
/// A day is a business day unless it is a weekend, a public holiday, or in
/// the year-end closure. The third is the one that surprises people: 2 and 3
/// January are not public holidays, and financial institutions are shut anyway —
/// a transfer dated 2 January does not settle on 2 January, whatever a holiday
/// calendar says.
///
/// **The holidays are data, not an algorithm.** They come from
/// the Cabinet Office's published CSV, bundled as a resource (permitted by
/// R-M2). Two of them — 春分の日 and 秋分の日 — are fixed by an astronomical
/// determination published in February of the preceding year, so no computation
/// gives them reliably for an arbitrary future year. Substitute holidays
/// (振替休日) and bridge holidays (国民の休日) are already in the source and are
/// not derived here either.
///
/// That is why [#validUntil()] exists and why going past it throws
/// rather than guesses (R-V7). A calendar that extrapolated would be
/// confidently wrong on exactly the dates a payment file is most likely to be
/// scheduled near, and would be wrong silently.
///
/// @since 0.2.0
public final class JapaneseBankCalendar implements BusinessCalendar {

    private static final String RESOURCE = "japanese-holidays.csv";

    /// 31 December to 3 January. Only 1 January is a public holiday; the rest
    /// is 金融機関の休業日, established by the Banking Act's implementing
    /// regulations rather than by the holidays act, and just as closed.
    private static final MonthDay CLOSURE_FROM = MonthDay.of(12, 31);
    private static final MonthDay CLOSURE_UNTIL = MonthDay.of(1, 3);

    private static final JapaneseBankCalendar BUNDLED = new JapaneseBankCalendar(load());

    private final Map<LocalDate, String> holidays;
    private final LocalDate horizon;

    private JapaneseBankCalendar(Loaded loaded) {
        this.holidays = loaded.holidays();
        this.horizon = loaded.horizon();
    }

    /// The calendar built from the bundled Cabinet Office data.
    ///
    /// @return the calendar, never `null`
    public static JapaneseBankCalendar bundled() {
        return BUNDLED;
    }

    @Override
    public boolean isBankBusinessDay(LocalDate date) {
        return classify(date).isBusinessDay();
    }

    @Override
    public NonBusinessDay classify(LocalDate date) {
        Objects.requireNonNull(date, "date");
        requireWithinHorizon(date);

        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return new NonBusinessDay(NonBusinessDay.Kind.WEEKEND, Optional.of(day.toString()));
        }
        if (inYearEndClosure(date)) {
            return new NonBusinessDay(NonBusinessDay.Kind.YEAR_END_CLOSURE, Optional.empty());
        }
        String name = holidays.get(date);
        if (name != null) {
            return new NonBusinessDay(NonBusinessDay.Kind.PUBLIC_HOLIDAY, Optional.of(name));
        }
        return NonBusinessDay.BUSINESS_DAY;
    }

    @Override
    public LocalDate nextBusinessDay(LocalDate date) {
        Objects.requireNonNull(date, "date");
        LocalDate candidate = date;
        // A run of non-business days cannot exceed a fortnight even around the
        // new year; the bound is a guard against a malformed calendar, not an
        // expected path.
        for (int i = 0; i < 31; i++) {
            requireWithinHorizon(candidate);
            if (classify(candidate).isBusinessDay()) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
        throw new IllegalStateException(
                "found no business day within 31 days of " + date + "; the calendar data is wrong");
    }

    @Override
    public LocalDate validUntil() {
        return horizon;
    }

    /// The holiday's name, if the date is one.
    ///
    /// @param date the date to look up
    /// @return the name, or empty
    public Optional<String> holidayName(LocalDate date) {
        return Optional.ofNullable(holidays.get(date));
    }

    /// Whether a date falls in the year-end closure.
    ///
    /// @param date the date to test
    /// @return `true` for 31 December through 3 January
    public boolean inYearEndClosure(LocalDate date) {
        var monthDay = MonthDay.from(date);
        return monthDay.equals(CLOSURE_FROM) || monthDay.compareTo(CLOSURE_UNTIL) <= 0;
    }

    private void requireWithinHorizon(LocalDate date) {
        if (date.isAfter(horizon)) {
            throw new BeyondCalendarHorizonException(date, horizon);
        }
    }

    private record Loaded(Map<LocalDate, String> holidays, LocalDate horizon) {
    }

    private static Loaded load() {
        try (InputStream stream = JapaneseBankCalendar.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException(
                        "the bundled holiday data (" + RESOURCE + ") is missing from the artifact");
            }
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                return parse(reader, "the bundled holiday data");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the bundled holiday data", e);
        }
    }

    /// Reads holiday data from a file in the bundled format (R-V7).
    ///
    /// The bundled data expires, and a released jar cannot be re-cut every
    /// February when the Cabinet Office publishes the next year's equinoxes.
    /// This is the way out: same format, same rules, data the caller controls.
    ///
    /// The format is one `YYYY-MM-DD,name` per line, a
    /// `horizon=YYYY-MM-DD` line stating the last date the data covers,
    /// and `#` comments. **The horizon is required.** Without
    /// it the calendar could not tell "this is a business day" from "I have no
    /// data for that year", and the second answer dressed up as the first is how
    /// a payment gets dated to a day the banks are shut.
    ///
    /// @param path the CSV file
    /// @return a calendar over that data
    /// @throws UncheckedIOException  if the file cannot be read
    /// @throws IllegalArgumentException if it declares no horizon or holds an
    ///   unparseable line
    public static JapaneseBankCalendar fromCsv(Path path) {
        Objects.requireNonNull(path, "path");
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return new JapaneseBankCalendar(parse(reader, path.toString()));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read holiday data from " + path, e);
        }
    }

    private static Loaded parse(BufferedReader reader, String source) throws IOException {
        Map<LocalDate, String> holidays = new HashMap<>();
        LocalDate horizon = null;
        String line;
        int number = 0;
        while ((line = reader.readLine()) != null) {
            number++;
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                if (line.startsWith("horizon=")) {
                    horizon = LocalDate.parse(line.substring("horizon=".length()));
                    continue;
                }
                int comma = line.indexOf(',');
                if (comma <= 0) {
                    throw new IllegalArgumentException(
                            "expected 'YYYY-MM-DD,name' or 'horizon=YYYY-MM-DD'");
                }
                holidays.put(LocalDate.parse(line.substring(0, comma)), line.substring(comma + 1));
            } catch (RuntimeException malformed) {
                throw new IllegalArgumentException(source + " line " + number + ": "
                        + malformed.getMessage(), malformed);
            }
        }
        if (horizon == null) {
            throw new IllegalArgumentException(source + " declares no horizon; without one this"
                    + " calendar would answer questions it cannot answer (R-V7)");
        }
        return new Loaded(Map.copyOf(holidays), horizon);
    }
}
