package com.example.countryinformation.dto;

import java.time.Instant;
import java.util.List;

public record CountryResponse(
        Long id,
        String isoCode,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String currencyIsoCode,
        String flagUrl,
        List<LanguageResponse> languages,
        Instant createdAt,
        Instant updatedAt) {
}
