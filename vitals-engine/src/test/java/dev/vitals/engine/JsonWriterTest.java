package dev.vitals.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JsonWriterTest {

    @Test
    void quote_givenNull_returnsBareNullLiteral() {
        assertThat(JsonWriter.quote(null)).isEqualTo("null");
    }

    @Test
    void quote_givenEmptyString_returnsEmptyJsonString() {
        assertThat(JsonWriter.quote("")).isEqualTo("\"\"");
    }

    @Test
    void quote_givenPlainString_wrapsInDoubleQuotes() {
        assertThat(JsonWriter.quote("hello")).isEqualTo("\"hello\"");
    }

    @Test
    void quote_givenQuoteAndBackslash_escapesBoth() {
        assertThat(JsonWriter.quote("a\"b\\c")).isEqualTo("\"a\\\"b\\\\c\"");
    }

    @Test
    void quote_givenWindowsPath_escapesEverySeparator() {
        assertThat(JsonWriter.quote("C:\\Users\\x\\Order.java")).isEqualTo("\"C:\\\\Users\\\\x\\\\Order.java\"");
    }

    @Test
    void quote_givenControlCharacters_usesShortAndUnicodeEscapes() {
        String input = "a\nb\tc\rd\bf\fg\u0001h";
        assertThat(JsonWriter.quote(input)).isEqualTo("\"a\\nb\\tc\\rd\\bf\\fg\\u0001h\"");
    }

    @Test
    void quote_givenNonAsciiUnicode_passesThrough() {
        assertThat(JsonWriter.quote("café ✓")).isEqualTo("\"café ✓\"");
    }
}
