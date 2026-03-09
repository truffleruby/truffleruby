package org.ruby_lang.prism;

// @formatter:off
public final class ParseResult {

    public static final class MagicComment {
        public final Nodes.Location keyLocation;
        public final Nodes.Location valueLocation;

        public MagicComment(Nodes.Location keyLocation, Nodes.Location valueLocation) {
            this.keyLocation = keyLocation;
            this.valueLocation = valueLocation;
        }
    }

    public enum ErrorLevel {
        /** For errors that should raise SyntaxError. */
        ERROR_SYNTAX,

        /** For errors that should raise ArgumentError. */
        ERROR_ARGUMENT,

        /** For errors that should raise LoadError. */
        ERROR_LOAD,
    }

    public static ErrorLevel[] ERROR_LEVELS = ErrorLevel.values();

    public static final class Error {
        public final Nodes.ErrorType type;
        public final String message;
        public final Nodes.Location location;
        public final ErrorLevel level;

        public Error(Nodes.ErrorType type, String message, Nodes.Location location, ErrorLevel level) {
            this.type = type;
            this.message = message;
            this.location = location;
            this.level = level;
        }
    }

    public enum WarningLevel {
        /** For warnings which should be emitted if $VERBOSE != nil. */
        WARNING_DEFAULT,

        /** For warnings which should be emitted if $VERBOSE == true. */
        WARNING_VERBOSE
    }

    public static WarningLevel[] WARNING_LEVELS = WarningLevel.values();

    public static final class Warning {
        public final Nodes.WarningType type;
        public final String message;
        public final Nodes.Location location;
        public final WarningLevel level;

        public Warning(Nodes.WarningType type, String message, Nodes.Location location, WarningLevel level) {
            this.type = type;
            this.message = message;
            this.location = location;
            this.level = level;
        }
    }

    public final Nodes.Node value;
    public final MagicComment[] magicComments;
    public final Nodes.Location dataLocation;
    public final Error[] errors;
    public final Warning[] warnings;
    public final Nodes.Source source;

    public ParseResult(Nodes.Node value, MagicComment[] magicComments, Nodes.Location dataLocation, Error[] errors, Warning[] warnings, Nodes.Source source) {
        this.value = value;
        this.magicComments = magicComments;
        this.dataLocation = dataLocation;
        this.errors = errors;
        this.warnings = warnings;
        this.source = source;
    }
}
// @formatter:on
