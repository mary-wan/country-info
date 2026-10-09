package com.example.countryinformation.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.countryinformation.model.CountryInfo;

@Repository
public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {

    @EntityGraph("CountryInfo.withLanguages")
    Optional<CountryInfo> findByIsoCode(String isoCode);

    @EntityGraph("CountryInfo.withLanguages")
    Optional<CountryInfo> findByName(String name);

    @EntityGraph("CountryInfo.withLanguages")
    @Override
    Optional<CountryInfo> findById(Long id);

    @EntityGraph("CountryInfo.withLanguages")
    @Override
    List<CountryInfo> findAll();
}
