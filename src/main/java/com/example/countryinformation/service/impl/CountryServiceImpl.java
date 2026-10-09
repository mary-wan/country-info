package com.example.countryinformation.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.countryinformation.dto.CountryLookupResult;
import com.example.countryinformation.dto.CountryUpdate;
import com.example.countryinformation.exception.CountryNotFoundException;
import com.example.countryinformation.model.CountryInfo;
import com.example.countryinformation.model.Language;
import com.example.countryinformation.repository.CountryInfoRepository;
import com.example.countryinformation.service.CountryService;
import com.example.countryinformation.soap.CountryDetails;
import com.example.countryinformation.soap.CountryInfoClient;
import com.example.countryinformation.utils.CountryNameNormalizer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CountryServiceImpl implements CountryService {

    private final CountryInfoRepository repository;
    private final CountryInfoClient countryInfoClient;
    private final CountryNameNormalizer normalizer;

    @Override
    public CountryLookupResult lookup(String rawCountryName) {
        String normalizedName = normalizer.normalize(rawCountryName);

        Optional<CountryInfo> byName = repository.findByName(normalizedName);
        if (byName.isPresent()) {
            log.info("Country '{}' already stored; returning existing record", normalizedName);
            return new CountryLookupResult(byName.get(), false);
        }

        String isoCode = countryInfoClient.fetchIsoCode(normalizedName);

        Optional<CountryInfo> byIsoCode = repository.findByIsoCode(isoCode);
        if (byIsoCode.isPresent()) {
            log.info("Country '{}' resolved to already stored ISO code {}", normalizedName, isoCode);
            return new CountryLookupResult(byIsoCode.get(), false);
        }

        CountryDetails details = countryInfoClient.fetchFullCountryInfo(isoCode);
        CountryInfo saved = persist(details, normalizedName);
        log.info("Stored new country {} ({})", saved.getName(), saved.getIsoCode());
        return new CountryLookupResult(saved, true);
    }

    private CountryInfo persist(CountryDetails details, String fallbackName) {
        CountryInfo country = CountryInfo.builder()
                .isoCode(details.isoCode())
                .name(details.name() == null ? fallbackName : details.name())
                .capitalCity(details.capitalCity())
                .phoneCode(details.phoneCode())
                .continentCode(details.continentCode())
                .currencyIsoCode(details.currencyIsoCode())
                .flagUrl(details.flagUrl())
                .build();

        details.languages().forEach(language -> country.addLanguage(
                Language.builder()
                        .isoCode(language.isoCode())
                        .name(language.name())
                        .build()));

        return repository.save(country);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CountryInfo> findAll() {
        return repository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public CountryInfo findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new CountryNotFoundException(id));
    }

    @Override
    @Transactional
    public CountryInfo update(Long id, CountryUpdate update) {
        CountryInfo country = repository.findById(id).orElseThrow(() -> new CountryNotFoundException(id));

        country.setName(normalizer.normalize(update.name()));
        country.setCapitalCity(update.capitalCity());
        country.setPhoneCode(update.phoneCode());
        country.setContinentCode(update.continentCode());
        country.setCurrencyIsoCode(update.currencyIsoCode());
        country.setFlagUrl(update.flagUrl());

        if (update.languages() != null) {
            country.replaceLanguages(update.languages().stream()
                    .map(language -> Language.builder()
                            .isoCode(language.isoCode())
                            .name(language.name())
                            .build())
                    .toList());
        }

        log.info("Updated country {} ({})", country.getName(), country.getIsoCode());
        return repository.save(country);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        CountryInfo country = repository.findById(id).orElseThrow(() -> new CountryNotFoundException(id));
        repository.delete(country);
        log.info("Deleted country {} ({})", country.getName(), country.getIsoCode());
    }
}
