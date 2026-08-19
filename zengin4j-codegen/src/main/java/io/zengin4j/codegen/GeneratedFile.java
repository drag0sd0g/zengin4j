package io.zengin4j.codegen;

import module java.base;

/// One file the generator produces.
///
/// @param path    where it belongs, absolute
/// @param content its full text
record GeneratedFile(Path path, String content) {
}
