package com.example.countryinformation.dto;

import com.example.countryinformation.model.CountryInfo;

public record CountryLookupResult(CountryInfo country, boolean created) {
}
