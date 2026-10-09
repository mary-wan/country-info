package com.example.countryinformation.soap;

import java.util.List;

public record CountryDetails(
        String isoCode,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String currencyIsoCode,
        String flagUrl,
        List<LanguageDetails> languages) {
}
