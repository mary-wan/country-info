package com.example.countryinformation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.countryinformation.config.CorrelationIdFilter;
import com.example.countryinformation.exception.GlobalExceptionHandler;
import com.example.countryinformation.dto.CountryLookupResult;
import com.example.countryinformation.dto.CountryUpdate;
import com.example.countryinformation.model.CountryInfo;
import com.example.countryinformation.model.Language;
import com.example.countryinformation.service.CountryService;

@WebMvcTest(controllers = CountryController.class)
@Import({ GlobalExceptionHandler.class, CorrelationIdFilter.class })
class CountryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryService countryService;

    private static CountryInfo kenya() {
        CountryInfo country = CountryInfo.builder()
                .id(1L)
                .isoCode("KE")
                .name("Kenya")
                .capitalCity("Nairobi")
                .phoneCode("254")
                .continentCode("AF")
                .currencyIsoCode("KES")
                .flagUrl("http://example.com/kenya.jpg")
                .build();
        country.addLanguage(Language.builder().id(10L).isoCode("swa").name("Swahili").build());
        return country;
    }

    @Test
    void returnsCreatedWithLocationWhenTheCountryIsNew() throws Exception {
        given(countryService.lookup("kenya")).willReturn(new CountryLookupResult(kenya(), true));

        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"kenya\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/countries/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isoCode").value("KE"))
                .andExpect(jsonPath("$.name").value("Kenya"))
                .andExpect(jsonPath("$.capitalCity").value("Nairobi"))
                .andExpect(jsonPath("$.phoneCode").value("254"))
                .andExpect(jsonPath("$.continentCode").value("AF"))
                .andExpect(jsonPath("$.currencyIsoCode").value("KES"))
                .andExpect(jsonPath("$.languages[0].isoCode").value("swa"))
                .andExpect(jsonPath("$.languages[0].name").value("Swahili"));
    }

    @Test
    void returnsOkWithoutLocationWhenTheCountryWasAlreadyStored() throws Exception {
        given(countryService.lookup("kenya")).willReturn(new CountryLookupResult(kenya(), false));

        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"kenya\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void listsStoredCountries() throws Exception {
        given(countryService.findAll()).willReturn(List.of(kenya()));

        mockMvc.perform(get("/api/countries"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].isoCode").value("KE"));
    }

    @Test
    void fetchesASingleCountry() throws Exception {
        given(countryService.findById(1L)).willReturn(kenya());

        mockMvc.perform(get("/api/countries/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kenya"));
    }

    @Test
    void updatesACountryAndForwardsTheMappedCommand() throws Exception {
        given(countryService.update(eq(1L), any(CountryUpdate.class))).willReturn(kenya());

        mockMvc.perform(put("/api/countries/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Kenya",
                          "capitalCity": "Nairobi",
                          "phoneCode": "254",
                          "continentCode": "AF",
                          "currencyIsoCode": "KES",
                          "languages": [ { "isoCode": "swa", "name": "Swahili" } ]
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        verify(countryService).update(eq(1L), any(CountryUpdate.class));
    }

    @Test
    void rejectsAnUpdateWithABlankLanguageName() throws Exception {
        mockMvc.perform(put("/api/countries/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kenya\",\"languages\":[{\"isoCode\":\"swa\",\"name\":\"  \"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deletesACountry() throws Exception {
        mockMvc.perform(delete("/api/countries/1"))
                .andExpect(status().isNoContent());

        verify(countryService).delete(1L);
    }
}
