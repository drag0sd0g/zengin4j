package io.zengin4j.codegen;

import java.nio.file.Path;

/**
 * One file the generator produces.
 *
 * @param path    where it belongs, absolute
 * @param content its full text
 */
record GeneratedFile(Path path, String content) {
}
