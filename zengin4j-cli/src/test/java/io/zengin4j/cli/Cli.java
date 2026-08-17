package io.zengin4j.cli;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the real command line and captures what it produced.
 *
 * <p>Drives {@link Zengin#run}, which means the real parser, the real command
 * objects and the real exit-code mapping — everything except the
 * {@code System.exit} call. A CLI tested only by spawning processes is a CLI
 * whose edge cases go untested, because each case costs a JVM start.
 */
record Cli(int status, String out, String err) {
    static Cli run(String... args) {
        StringWriter outBuffer = new StringWriter();
        StringWriter errBuffer = new StringWriter();
        PrintWriter out = new PrintWriter(outBuffer);
        PrintWriter err = new PrintWriter(errBuffer);

        int status = Zengin.run(args, out, err);

        out.flush();
        err.flush();
        return new Cli(status, outBuffer.toString(), errBuffer.toString());
    }

    /** Writes a synthetic file and returns its path, failing the test if generation did. */
    static Path generate(Path directory, String name, String... extra) throws Exception {
        Path file = directory.resolve(name);
        String[] args = new String[extra.length + 2];
        args[0] = "generate";
        System.arraycopy(extra, 0, args, 1, extra.length);
        args[args.length - 1] = "--out=" + file;

        Cli result = run(args);
        if (result.status() != ExitCode.OK.value()) {
            throw new AssertionError("generate failed: " + result.err());
        }
        if (!Files.exists(file)) {
            throw new AssertionError("generate reported success but wrote nothing");
        }
        return file;
    }

    /** Everything the command printed, wherever it printed it. */
    String all() {
        return out + err;
    }
}
