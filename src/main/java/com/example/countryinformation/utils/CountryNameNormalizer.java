package com.example.countryinformation.utils;

import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class CountryNameNormalizer {

    public String normalize(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("Country name must not be blank");
        }

        StringBuilder builder = new StringBuilder(
                rawName.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT));

        boolean capitalizeNext = true;
        for (int i = 0; i < builder.length(); i++) {
            char current = builder.charAt(i);
            if (current == ' ' || current == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext && Character.isLetter(current)) {
                builder.setCharAt(i, Character.toUpperCase(current));
                capitalizeNext = false;
            }
        }
        return builder.toString();
    }
}
