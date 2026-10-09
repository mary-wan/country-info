package com.example.countryinformation.dto;

import java.util.List;

public record CountryUpdate(
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String currencyIsoCode,
        String flagUrl,
        List<LanguageUpdate> languages) {
}
