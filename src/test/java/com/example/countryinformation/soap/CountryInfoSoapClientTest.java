package com.example.countryinformation.soap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;

import com.example.countryinformation.exception.SoapIntegrationException;
import com.example.countryinformation.exception.UnknownCountryException;
import com.example.countryinformation.generated.ArrayOftLanguage;
import com.example.countryinformation.generated.CountryISOCode;
import com.example.countryinformation.generated.CountryISOCodeResponse;
import com.example.countryinformation.generated.FullCountryInfo;
import com.example.countryinformation.generated.FullCountryInfoResponse;
import com.example.countryinformation.generated.TCountryInfo;
import com.example.countryinformation.generated.TLanguage;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

@ExtendWith(MockitoExtension.class)
class CountryInfoSoapClientTest {

    private static final int MAX_ATTEMPTS = 3;

    @Mock
    private WebServiceTemplate webServiceTemplate;

    private CountryInfoSoapClient client;

    @BeforeEach
    void setUp() {
        Retry retry = Retry.of("test", RetryConfig.custom()
                .maxAttempts(MAX_ATTEMPTS)
                .waitDuration(Duration.ofMillis(1))
                .retryExceptions(SoapIntegrationException.class)
                .ignoreExceptions(UnknownCountryException.class)
                .build());

        CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
                .slidingWindowSize(100)
                .minimumNumberOfCalls(100)
                .ignoreExceptions(UnknownCountryException.class)
                .build());

        client = new CountryInfoSoapClient(webServiceTemplate, retry, circuitBreaker);
    }

    private static CountryISOCodeResponse isoResponse(String value) {
        CountryISOCodeResponse response = new CountryISOCodeResponse();
        response.setCountryISOCodeResult(value);
        return response;
    }

    private static FullCountryInfoResponse fullResponse(TCountryInfo result) {
        FullCountryInfoResponse response = new FullCountryInfoResponse();
        response.setFullCountryInfoResult(result);
        return response;
    }

    private static TCountryInfo kenya() {
        TCountryInfo info = new TCountryInfo();
        info.setSISOCode("ke");
        info.setSName("Kenya");
        info.setSCapitalCity("Nairobi");
        info.setSPhoneCode("254");
        info.setSContinentCode("AF");
        info.setSCurrencyISOCode("KES");
        info.setSCountryFlag("http://example.com/kenya.jpg");
        return info;
    }

    private static ArrayOftLanguage languages(TLanguage... entries) {
        ArrayOftLanguage array = new ArrayOftLanguage();
        for (TLanguage entry : entries) {
            array.getTLanguage().add(entry);
        }
        return array;
    }

    private static TLanguage language(String isoCode, String name) {
        TLanguage language = new TLanguage();
        language.setSISOCode(isoCode);
        language.setSName(name);
        return language;
    }

    @Test
    void fetchIsoCodeSendsTheCountryNameAndUppercasesTheResult() {
        given(webServiceTemplate.marshalSendAndReceive(any(CountryISOCode.class)))
                .willReturn(isoResponse(" ke "));

        assertThat(client.fetchIsoCode("Kenya")).isEqualTo("KE");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "   ",
            "Country not found in the database",
            "1",
            "KENYA"
    })
    void fetchIsoCodeTreatsAnyNonIsoPayloadAsAnUnknownCountry(String sentinel) {
        given(webServiceTemplate.marshalSendAndReceive(any(CountryISOCode.class)))
                .willReturn(isoResponse(sentinel));

        assertThatThrownBy(() -> client.fetchIsoCode("Narnia"))
                .isInstanceOf(UnknownCountryException.class)
                .hasMessageContaining("Narnia");
    }

    @Test
    void anUnknownCountryIsNotRetried() {
        given(webServiceTemplate.marshalSendAndReceive(any(CountryISOCode.class)))
                .willReturn(isoResponse("Country not found in the database"));

        assertThatThrownBy(() -> client.fetchIsoCode("Narnia"))
                .isInstanceOf(UnknownCountryException.class);

        verify(webServiceTemplate, times(1)).marshalSendAndReceive(any());
    }

    @Test
    void fetchFullCountryInfoMapsEveryField() {
        TCountryInfo info = kenya();
        info.setLanguages(languages(language("SWA", "Swahili")));
        given(webServiceTemplate.marshalSendAndReceive(any(FullCountryInfo.class)))
                .willReturn(fullResponse(info));

        CountryDetails details = client.fetchFullCountryInfo("KE");

        assertThat(details.isoCode()).isEqualTo("KE");
        assertThat(details.name()).isEqualTo("Kenya");
        assertThat(details.capitalCity()).isEqualTo("Nairobi");
        assertThat(details.phoneCode()).isEqualTo("254");
        assertThat(details.continentCode()).isEqualTo("AF");
        assertThat(details.currencyIsoCode()).isEqualTo("KES");
        assertThat(details.flagUrl()).isEqualTo("http://example.com/kenya.jpg");
        assertThat(details.languages()).containsExactly(new LanguageDetails("swa", "Swahili"));
    }

    @Test
    void fetchFullCountryInfoToleratesMissingLanguages() {
        given(webServiceTemplate.marshalSendAndReceive(any(FullCountryInfo.class)))
                .willReturn(fullResponse(kenya()));

        assertThat(client.fetchFullCountryInfo("KE").languages()).isEmpty();
    }

    @Test
    void fetchFullCountryInfoDropsLanguagesWithoutAName() {
        TCountryInfo info = kenya();
        info.setLanguages(languages(language("swa", "Swahili"), language("xxx", "  "), language(null, null)));
        given(webServiceTemplate.marshalSendAndReceive(any(FullCountryInfo.class)))
                .willReturn(fullResponse(info));

        assertThat(client.fetchFullCountryInfo("KE").languages())
                .containsExactly(new LanguageDetails("swa", "Swahili"));
    }

    @Test
    void fetchFullCountryInfoThrowsWhenUpstreamReturnsNoResult() {
        given(webServiceTemplate.marshalSendAndReceive(any(FullCountryInfo.class)))
                .willReturn(fullResponse(null));

        assertThatThrownBy(() -> client.fetchFullCountryInfo("ZZ"))
                .isInstanceOf(UnknownCountryException.class)
                .hasMessageContaining("ZZ");
    }

    @Test
    void fetchFullCountryInfoThrowsWhenTheIsoCodeComesBackBlank() {
        TCountryInfo info = new TCountryInfo();
        info.setSISOCode("   ");
        given(webServiceTemplate.marshalSendAndReceive(any(FullCountryInfo.class)))
                .willReturn(fullResponse(info));

        assertThatThrownBy(() -> client.fetchFullCountryInfo("ZZ"))
                .isInstanceOf(UnknownCountryException.class);
    }

    @Test
    void translatesTransportFailuresIntoSoapIntegrationException() {
        given(webServiceTemplate.marshalSendAndReceive(any()))
                .willThrow(new WebServiceIOException("connection refused"));

        assertThatThrownBy(() -> client.fetchIsoCode("Kenya"))
                .isInstanceOf(SoapIntegrationException.class)
                .hasMessageContaining("CountryISOCode");
    }

    @Test
    void retriesTransportFailuresUpToTheConfiguredLimit() {
        given(webServiceTemplate.marshalSendAndReceive(any()))
                .willThrow(new WebServiceIOException("connection refused"));

        assertThatThrownBy(() -> client.fetchIsoCode("Kenya"))
                .isInstanceOf(SoapIntegrationException.class);

        verify(webServiceTemplate, times(MAX_ATTEMPTS)).marshalSendAndReceive(any());
    }

    @Test
    void recoversWhenATransientFailureIsFollowedBySuccess() {
        given(webServiceTemplate.marshalSendAndReceive(any()))
                .willThrow(new WebServiceIOException("connection reset"))
                .willReturn(isoResponse("KE"));

        assertThat(client.fetchIsoCode("Kenya")).isEqualTo("KE");

        verify(webServiceTemplate, times(2)).marshalSendAndReceive(any());
    }

    @Test
    void rejectsAnUnexpectedResponsePayload() {
        given(webServiceTemplate.marshalSendAndReceive(any(CountryISOCode.class)))
                .willReturn("not a response object");

        assertThatThrownBy(() -> client.fetchIsoCode("Kenya"))
                .isInstanceOf(SoapIntegrationException.class)
                .hasMessageContaining("unexpected payload");
    }
}
