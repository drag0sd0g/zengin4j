/// One class per subcommand, plus the options they share.
///
/// Each command does its own work and returns an exit status; none of them
/// calls `System.exit`, so each is a plain object a test can drive.
/// `ReadingOptions` is mixed into every command that opens a file, so
/// `--charset` and `--format` cannot drift apart between them.
///
/// **Every command that prints record contents masks sensitive fields
/// unless `--unsafe-print` is given** (R-CLI4).
///
/// @since 0.3.0
package io.zengin4j.cli.command;
