/// Synthetic fixtures and deterministic generators for testing code that
/// consumes zengin4j.
///
/// This is a published artifact rather than a test-scoped one (R-M4):
/// downstream projects need it on their own test class path.
///
/// **Every value produced by this module is invented.** No real
/// payment data, account identifier or institution name appears anywhere in it
/// (R-L1, P1).
///
/// @since 0.1.0
module io.zengin4j.testkit {
    requires transitive io.zengin4j.core;

    exports io.zengin4j.testkit;
}
