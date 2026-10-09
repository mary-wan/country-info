package com.example.countryinformation.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.countryinformation.exception.CountryNotFoundException;
import com.example.countryinformation.exception.UnknownCountryException;
import com.example.countryinformation.model.CountryInfo;
import com.example.countryinformation.model.Language;
import com.example.countryinformation.repository.CountryInfoRepository;
import com.example.countryinformation.dto.CountryLookupResult;
import com.example.countryinformation.dto.CountryUpdate;
import com.example.countryinformation.dto.LanguageUpdate;
import com.example.countryinformation.soap.CountryDetails;
import com.example.countryinformation.soap.CountryInfoClient;
import com.example.countryinformation.soap.LanguageDetails;
import com.example.countryinformation.utils.CountryNameNormalizer;

@ExtendWith(MockitoExtension.class)
class CountryServiceImplTest {

    @Mock
    private CountryInfoRepository repository;

    @Mock
    private CountryInfoClient countryInfoClient;

    @Spy
    private CountryNameNormalizer normalizer = new CountryNameNormalizer();

    @InjectMocks
    private CountryServiceImpl service;

    private static CountryDetails kenyaDetails() {
        return new CountryDetails("KE", "Kenya", "Nairobi", "254", "AF", "KES",
                "http://example.com/kenya.jpg", List.of(new LanguageDetails("swa", "Swahili")));
    }

    private static CountryInfo storedKenya() {
        return CountryInfo.builder().id(1L).isoCode("KE").name("Kenya").capitalCity("Nairobi").build();
    }

    @Test
    void resolvesThroughSoapAndPersistsWhenCountryIsNew() {
        given(repository.findByName("Kenya")).willReturn(Optional.empty());
        given(countryInfoClient.fetchIsoCode("Kenya")).willReturn("KE");
        given(repository.findByIsoCode("KE")).willReturn(Optional.empty());
        given(countryInfoClient.fetchFullCountryInfo("KE")).willReturn(kenyaDetails());
        given(repository.save(any(CountryInfo.class))).willAnswer(invocation -> invocation.getArgument(0));

        CountryLookupResult result = service.lookup("kenya");

        assertThat(result.created()).isTrue();

        ArgumentCaptor<CountryInfo> captor = ArgumentCaptor.forClass(CountryInfo.class);
        verify(repository).save(captor.capture());
        CountryInfo saved = captor.getValue();

        assertThat(saved.getIsoCode()).isEqualTo("KE");
        assertThat(saved.getName()).isEqualTo("Kenya");
        assertThat(saved.getCapitalCity()).isEqualTo("Nairobi");
        assertThat(saved.getCurrencyIsoCode()).isEqualTo("KES");
        assertThat(saved.getLanguages()).singleElement()
                .satisfies(language -> {
                    assertThat(language.getName()).isEqualTo("Swahili");
                    assertThat(language.getCountry()).isSameAs(saved);
                });
    }

    @Test
    void normalizesTheNameBeforeLookingItUp() {
        given(repository.findByName("South Africa")).willReturn(Optional.of(storedKenya()));

        service.lookup("  south   AFRICA ");

        verify(repository).findByName("South Africa");
    }

    @Test
    void returnsStoredCountryWithoutCallingSoapWhenNameAlreadyExists() {
        given(repository.findByName("Kenya")).willReturn(Optional.of(storedKenya()));

        CountryLookupResult result = service.lookup("kenya");

        assertThat(result.created()).isFalse();
        assertThat(result.country().getIsoCode()).isEqualTo("KE");
        verifyNoInteractions(countryInfoClient);
        verify(repository, never()).save(any());
    }

    @Test
    void returnsStoredCountryWhenAnAliasResolvesToAnAlreadyStoredIsoCode() {
        given(repository.findByName("Holland")).willReturn(Optional.empty());
        given(countryInfoClient.fetchIsoCode("Holland")).willReturn("NL");
        given(repository.findByIsoCode("NL")).willReturn(Optional.of(storedKenya()));

        CountryLookupResult result = service.lookup("holland");

        assertThat(result.created()).isFalse();
        verify(countryInfoClient, never()).fetchFullCountryInfo(any());
        verify(repository, never()).save(any());
    }

    @Test
    void fallsBackToTheNormalizedNameWhenUpstreamOmitsIt() {
        given(repository.findByName("Kenya")).willReturn(Optional.empty());
        given(countryInfoClient.fetchIsoCode("Kenya")).willReturn("KE");
        given(repository.findByIsoCode("KE")).willReturn(Optional.empty());
        given(countryInfoClient.fetchFullCountryInfo("KE")).willReturn(
                new CountryDetails("KE", null, null, null, null, null, null, List.of()));
        given(repository.save(any(CountryInfo.class))).willAnswer(invocation -> invocation.getArgument(0));

        CountryLookupResult result = service.lookup("kenya");

        assertThat(result.country().getName()).isEqualTo("Kenya");
    }

    @Test
    void propagatesUnknownCountryAndStoresNothing() {
        given(repository.findByName("Narnia")).willReturn(Optional.empty());
        given(countryInfoClient.fetchIsoCode("Narnia"))
                .willThrow(new UnknownCountryException("No country found by the name 'Narnia'"));

        assertThatThrownBy(() -> service.lookup("narnia"))
                .isInstanceOf(UnknownCountryException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsBlankLookupNameBeforeTouchingTheRepository() {
        assertThatThrownBy(() -> service.lookup("   "))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(repository, countryInfoClient);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        given(repository.findById(300L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(300L))
                .isInstanceOf(CountryNotFoundException.class)
                .hasMessageContaining("300");
    }

    @Test
    void updateNormalizesTheNameAndReplacesLanguages() {
        CountryInfo existing = storedKenya();
        existing.addLanguage(Language.builder().isoCode("eng").name("English").build());

        given(repository.findById(1L)).willReturn(Optional.of(existing));
        given(repository.save(any(CountryInfo.class))).willAnswer(invocation -> invocation.getArgument(0));

        CountryInfo updated = service.update(1L, new CountryUpdate("kenya", "Mombasa", "254", "AF", "KES",
                null, List.of(new LanguageUpdate("swa", "Swahili"))));

        assertThat(updated.getName()).isEqualTo("Kenya");
        assertThat(updated.getCapitalCity()).isEqualTo("Mombasa");
        assertThat(updated.getLanguages()).singleElement()
                .satisfies(language -> assertThat(language.getName()).isEqualTo("Swahili"));
    }

    @Test
    void updateLeavesLanguagesUntouchedWhenOmitted() {
        CountryInfo existing = storedKenya();
        existing.addLanguage(Language.builder().isoCode("swa").name("Swahili").build());

        given(repository.findById(1L)).willReturn(Optional.of(existing));
        given(repository.save(any(CountryInfo.class))).willAnswer(invocation -> invocation.getArgument(0));

        CountryInfo updated = service.update(1L,
                new CountryUpdate("Kenya", "Nairobi", "254", "AF", "KES", null, null));

        assertThat(updated.getLanguages()).singleElement()
                .satisfies(language -> assertThat(language.getName()).isEqualTo("Swahili"));
    }

    @Test
    void updateThrowsWhenMissing() {
        given(repository.findById(300L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(300L,
                new CountryUpdate("Kenya", null, null, null, null, null, null)))
                .isInstanceOf(CountryNotFoundException.class);
    }

    @Test
    void deleteRemovesTheStoredCountry() {
        CountryInfo existing = storedKenya();
        given(repository.findById(1L)).willReturn(Optional.of(existing));

        service.delete(1L);

        verify(repository).delete(existing);
    }

    @Test
    void deleteThrowsWhenMissing() {
        given(repository.findById(300L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(300L))
                .isInstanceOf(CountryNotFoundException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void findAllDelegatesToTheRepository() {
        given(repository.findAll()).willReturn(List.of(storedKenya()));

        assertThat(service.findAll()).hasSize(1);
    }
}
