package com.example.countryinformation.dto;

import com.example.countryinformation.model.CountryInfo;
import com.example.countryinformation.service.CountryUpdate;
import com.example.countryinformation.service.LanguageUpdate;

public final class CountryMapper {

    private CountryMapper() {
    }

    public static CountryResponse toResponse(CountryInfo country) {
        return new CountryResponse(
                country.getId(),
                country.getIsoCode(),
                country.getName(),
                country.getCapitalCity(),
                country.getPhoneCode(),
                country.getContinentCode(),
                country.getCurrencyIsoCode(),
                country.getFlagUrl(),
                country.getLanguages().stream()
                        .map(language -> new LanguageResponse(language.getIsoCode(), language.getName()))
                        .toList(),
                country.getCreatedAt(),
                country.getUpdatedAt());
    }

    public static CountryUpdate toCountryUpdate(CountryUpdateRequest request) {
        return new CountryUpdate(
                request.name(),
                request.capitalCity(),
                request.phoneCode(),
                request.continentCode(),
                request.currencyIsoCode(),
                request.flagUrl(),
                request.languages() == null ? null : request.languages().stream()
                        .map(language -> new LanguageUpdate(language.isoCode(), language.name()))
                        .toList());
    }
}
