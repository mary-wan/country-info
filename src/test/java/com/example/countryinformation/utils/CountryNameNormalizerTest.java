package com.example.countryinformation.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CountryNameNormalizerTest {

    private final CountryNameNormalizer normalizer = new CountryNameNormalizer();

    @ParameterizedTest
    @CsvSource({
            "kenya, Kenya",
            "KENYA, Kenya",
            "Kenya, Kenya",
            "kEnYa, Kenya",
            "south africa, South Africa",
            "SOUTH AFRICA, South Africa",
            "united states, United States",
            "new zealand, New Zealand",
            "guinea-bissau, Guinea-Bissau",
            "GUINEA-BISSAU, Guinea-Bissau",
            "papua new guinea, Papua New Guinea"
    })
    void capitalizesFirstLetterOfEachWord(String input, String expected) {
        assertThat(normalizer.normalize(input)).isEqualTo(expected);
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertThat(normalizer.normalize("  KENYA  ")).isEqualTo("Kenya");
    }

    @Test
    void collapsesRepeatedInternalWhitespace() {
        assertThat(normalizer.normalize("south    africa")).isEqualTo("South Africa");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "\t" })
    void rejectsBlankInput(String input) {
        assertThatThrownBy(() -> normalizer.normalize(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
