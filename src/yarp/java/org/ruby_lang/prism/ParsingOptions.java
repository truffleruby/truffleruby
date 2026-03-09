package org.ruby_lang.prism;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumSet;

// @formatter:off
public abstract class ParsingOptions {
    /**
     * The version of Ruby syntax that we should be parsing with.
     * See pm_options_version_t in include/prism/options.h.
     */
    public enum SyntaxVersion {
        LATEST(0), // Handled in pm_parser_init
        V3_3(1),
        V3_4(2),
        V3_5(3),
        V4_0(3),
        V4_1(4);

        private final int value;

        SyntaxVersion(int value) {
            this.value = value;
        }

        public byte getValue() {
            return (byte) value;
        }
    }

    /**
     * The command line options that can be passed to the parser.
     * See PM_OPTIONS_COMMAND_LINE_* in include/prism/options.h.
     *
     * NOTE: positions should match PM_OPTIONS_COMMAND_LINE_* constants values
     */
    public enum CommandLine { A, E, L, N, P, X };

    /**
     * The forwarding options for a given scope in the parser.
     */
    public enum Forwarding {
        NONE(0),
        POSITIONAL(1),
        KEYWORD(2),
        BLOCK(4),
        ALL(8);

        private final int value;

        Forwarding(int value) {
            this.value = value;
        }

        public byte getValue() {
            return (byte) value;
        }
    };

    /**
     * Represents a scope in the parser.
     */
    public static class Scope {
        private byte[][] locals;
        private Forwarding[] forwarding;

        public Scope(byte[][] locals) {
            this(locals, new Forwarding[0]);
        }

        public Scope(Forwarding[] forwarding) {
            this(new byte[0][], forwarding);
        }

        public Scope(byte[][] locals, Forwarding[] forwarding) {
            this.locals = locals;
            this.forwarding = forwarding;
        }

        public byte[][] getLocals() {
            return locals;
        }

        public int getForwarding() {
            int value = 0;
            for (Forwarding f : forwarding) {
                value |= f.getValue();
            }
            return value;
        }
    }

    public static byte[] serialize(byte[] filepath, int line, byte[] encoding, boolean frozenStringLiteral, EnumSet<CommandLine> commandLine, SyntaxVersion version, boolean encodingLocked, boolean mainScript, boolean partialScript, byte[][][] scopes) {
        Scope[] normalizedScopes = new Scope[scopes.length];
        for (int i = 0; i < scopes.length; i++) {
            normalizedScopes[i] = new Scope(scopes[i]);
        }

        return serialize(filepath, line, encoding, frozenStringLiteral, commandLine, version, encodingLocked, mainScript, partialScript, normalizedScopes);
    }

    /**
     * Serialize parsing options into byte array.
     *
     * @param filepath the name of the file that is currently being parsed
     * @param line the line within the file that the parser starts on. This value is 1-indexed
     * @param encoding the name of the encoding that the source file is in
     * @param frozenStringLiteral whether the frozen string literal option has been set
     * @param commandLine the set of flags that were set on the command line
     * @param version code of Ruby version which syntax will be used to parse
     * @param encodingLocked whether the encoding is locked (should almost always be false)
     * @param mainScript whether the file is the main script
     * @param partialScript whether the file is a partial script
     * @param scopes scopes surrounding the code that is being parsed with local variable names defined in every scope
     *            ordered from the outermost scope to the innermost one
     */
    public static byte[] serialize(byte[] filepath, int line, byte[] encoding, boolean frozenStringLiteral, EnumSet<CommandLine> commandLine, SyntaxVersion version, boolean encodingLocked, boolean mainScript, boolean partialScript, Scope[] scopes) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();

        // filepath
        write(output, serializeInt(filepath.length));
        write(output, filepath);

        // line
        write(output, serializeInt(line));

        // encoding
        write(output, serializeInt(encoding.length));
        write(output, encoding);

        // frozenStringLiteral
        output.write(frozenStringLiteral ? 1 : 0);

        // command line
        output.write(serializeEnumSet(commandLine));

        // version
        output.write(version.getValue());

        // encodingLocked
        output.write(encodingLocked ? 1 : 0);

        // mainScript
        output.write(mainScript ? 1 : 0);

        // partialScript
        output.write(partialScript ? 1 : 0);

        // freeze
        output.write(0);

        // scopes

        // number of scopes
        write(output, serializeInt(scopes.length));

        // local variables in each scope
        for (Scope scope : scopes) {
            byte[][] locals = scope.getLocals();

            // number of locals
            write(output, serializeInt(locals.length));

            // forwarding flags
            output.write(scope.getForwarding());

            // locals
            for (byte[] local : locals) {
                write(output, serializeInt(local.length));
                write(output, local);
            }
        }

        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, byte[] bytes) {
        // Note: we cannot use output.writeBytes(local) because that's Java 11
        output.write(bytes, 0, bytes.length);
    }

    private static <T extends Enum<T>> byte serializeEnumSet(EnumSet<T> set) {
        byte result = 0;
        for (T value : set) {
            assert (1 << value.ordinal()) <= Byte.MAX_VALUE;
            result |= (byte) (1 << value.ordinal());
        }
        return result;
    }

    private static byte[] serializeInt(int n) {
        ByteBuffer buffer = ByteBuffer.allocate(4).order(ByteOrder.nativeOrder());
        buffer.putInt(n);
        return buffer.array();
    }
}
// @formatter:on
