package org.ruby_lang.prism;

public abstract class Parser {

    @SuppressWarnings("restricted")
    public static void loadLibrary(String path) {
        System.load(path);
    }

    public static native byte[] parseAndSerialize(byte[] source, byte[] options);

    public static byte[] parseAndSerialize(byte[] source) {
        return parseAndSerialize(source, null);
    }

}
