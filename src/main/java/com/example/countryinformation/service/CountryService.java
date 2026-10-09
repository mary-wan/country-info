package com.example.countryinformation.service;

import java.util.List;

import com.example.countryinformation.model.CountryInfo;

public interface CountryService {

    CountryLookupResult lookup(String rawCountryName);

    List<CountryInfo> findAll();

    CountryInfo findById(Long id);

    CountryInfo update(Long id, CountryUpdate update);

    void delete(Long id);
}
