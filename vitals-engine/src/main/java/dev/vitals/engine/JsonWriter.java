package dev.vitals.engine;

import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Minimal RFC 8259 JSON string escaping. The document shape lives in {@link JsonReporter}. */
final class JsonWriter {

    private JsonWriter() {}

    /**
     * Returns {@code value} as a JSON string literal including the surrounding
     * double quotes, or the bare literal {@code null} when {@code value} is null.
     */
    static String quote(@Nullable String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
