package com.example.countryinformation.service;

import com.example.countryinformation.model.CountryInfo;

public record CountryLookupResult(CountryInfo country, boolean created) {
}
