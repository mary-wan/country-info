package com.example.countryinformation.soap;

public interface CountryInfoClient {

    String fetchIsoCode(String countryName);

    CountryDetails fetchFullCountryInfo(String isoCode);
}
