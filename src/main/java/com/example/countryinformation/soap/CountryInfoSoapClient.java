package com.example.countryinformation.soap;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;
import org.springframework.ws.client.WebServiceClientException;
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
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class CountryInfoSoapClient implements CountryInfoClient {

    private static final Pattern ISO_CODE = Pattern.compile("^[A-Za-z]{2,3}$");

    private final WebServiceTemplate webServiceTemplate;
    private final Retry countryInfoRetry;
    private final CircuitBreaker countryInfoCircuitBreaker;

    @Override
    public String fetchIsoCode(String countryName) {
        CountryISOCode request = new CountryISOCode();
        request.setSCountryName(countryName);

        CountryISOCodeResponse response = call(request, CountryISOCodeResponse.class, "CountryISOCode");
        String isoCode = trimToNull(response.getCountryISOCodeResult());

        if (isoCode == null || !ISO_CODE.matcher(isoCode).matches()) {
            log.info("Upstream did not resolve country name '{}' (response: '{}')", countryName, isoCode);
            throw new UnknownCountryException("No country found by the name '" + countryName + "'");
        }
        return isoCode.toUpperCase();
    }

    @Override
    public CountryDetails fetchFullCountryInfo(String isoCode) {
        FullCountryInfo request = new FullCountryInfo();
        request.setSCountryISOCode(isoCode);

        FullCountryInfoResponse response = call(request, FullCountryInfoResponse.class, "FullCountryInfo");
        TCountryInfo result = response.getFullCountryInfoResult();

        if (result == null || trimToNull(result.getSISOCode()) == null) {
            log.info("Upstream returned no country detail for ISO code '{}'", isoCode);
            throw new UnknownCountryException("No country details found for ISO code '" + isoCode + "'");
        }

        return new CountryDetails(
                result.getSISOCode().trim().toUpperCase(),
                trimToNull(result.getSName()),
                trimToNull(result.getSCapitalCity()),
                trimToNull(result.getSPhoneCode()),
                trimToNull(result.getSContinentCode()),
                trimToNull(result.getSCurrencyISOCode()),
                trimToNull(result.getSCountryFlag()),
                toLanguages(result.getLanguages()));
    }

    private <T> T call(Object request, Class<T> responseType, String operation) {
        Supplier<Object> guarded = Retry.decorateSupplier(countryInfoRetry,
                CircuitBreaker.decorateSupplier(countryInfoCircuitBreaker, () -> send(request, operation)));

        Object response = guarded.get();

        if (!responseType.isInstance(response)) {
            throw new SoapIntegrationException(
                    "SOAP call '" + operation + "' returned an unexpected payload: "
                            + (response == null ? "null" : response.getClass().getName()));
        }
        return responseType.cast(response);
    }

    private Object send(Object request, String operation) {
        try {
            return webServiceTemplate.marshalSendAndReceive(request);
        } catch (WebServiceClientException e) {
            throw new SoapIntegrationException("SOAP call '" + operation + "' failed: " + e.getMessage(), e);
        }
    }

    private List<LanguageDetails> toLanguages(ArrayOftLanguage languages) {
        return Optional.ofNullable(languages)
                .map(ArrayOftLanguage::getTLanguage)
                .orElseGet(List::of)
                .stream()
                .filter(language -> trimToNull(language.getSName()) != null)
                .map(this::toLanguage)
                .toList();
    }

    private LanguageDetails toLanguage(TLanguage language) {
        String code = trimToNull(language.getSISOCode());
        return new LanguageDetails(code == null ? null : code.toLowerCase(), trimToNull(language.getSName()));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
