package com.daviddunn.retirementplanner.persistence;

import com.fasterxml.jackson.core.*;
import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;

/** Token-preserving root metadata replacement; never a financial tree round trip. */
public final class PlanConfigurationTransaction {
    private PlanConfigurationTransaction() { }

    public static byte[] replace(byte[] source, String field, byte[] value) throws IOException {
        var factory = new JsonFactory();
        var output = new ByteArrayOutputStream();
        try (var parser = factory.createParser(source); var generator = factory.createGenerator(output)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) throw new IOException("Expected plan object.");
            generator.writeStartObject();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (parser.currentToken() != JsonToken.FIELD_NAME) throw new IOException("Invalid plan fields.");
                String name = parser.currentName(); parser.nextToken();
                if (field.equals(name)) parser.skipChildren();
                else {
                    generator.writeFieldName(name);
                    copyValue(parser, generator);
                }
            }
            if (parser.nextToken() != null) throw new IOException("Unexpected trailing plan data.");
            if (value != null) {
                generator.writeFieldName(field);
                generator.writeRawValue(new String(value, java.nio.charset.StandardCharsets.UTF_8));
            }
            generator.writeEndObject();
        }
        return output.toByteArray();
    }

    private static void copyValue(JsonParser parser, JsonGenerator generator) throws IOException {
        int depth = 0;
        do {
            var token = parser.currentToken();
            if (token == null) throw new IOException("Truncated plan.");
            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) depth++;
            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) depth--;
            if (token.isNumeric()) generator.writeNumber(parser.getText()); else generator.copyCurrentEvent(parser);
            if (depth > 0) parser.nextToken();
        } while (depth > 0);
    }

    static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    static Lock lock(Path target) throws IOException {
        Path lock = target.resolveSibling(target.getFileName() + ".rp-lock");
        AtomicFileWriter.regularOrMissing(lock);
        var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lease = channel.tryLock();
            if (lease == null) throw new IOException("Another process is saving this plan.");
            return new Lock(channel, lease);
        } catch (IOException | RuntimeException failure) { channel.close(); throw new IOException("Could not lock plan for safe saving.", failure); }
    }
    record Lock(FileChannel channel, FileLock lease) implements AutoCloseable {
        @Override public void close() throws IOException { try { lease.release(); } finally { channel.close(); } }
    }
}
