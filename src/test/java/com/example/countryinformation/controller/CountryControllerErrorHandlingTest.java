package com.example.countryinformation.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.countryinformation.config.CorrelationIdFilter;
import com.example.countryinformation.exception.CountryNotFoundException;
import com.example.countryinformation.exception.GlobalExceptionHandler;
import com.example.countryinformation.exception.SoapIntegrationException;
import com.example.countryinformation.exception.UnknownCountryException;
import com.example.countryinformation.service.CountryService;

@WebMvcTest(controllers = CountryController.class)
@Import({ GlobalExceptionHandler.class, CorrelationIdFilter.class })
class CountryControllerErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CountryService countryService;

    @Test
    void returnsNotFoundWhenIdDoesNotExist() throws Exception {
        given(countryService.findById(anyLong())).willThrow(new CountryNotFoundException(300L));

        mockMvc.perform(get("/api/countries/300"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("No country found with id 300"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void returnsNotFoundWhenUpstreamDoesNotKnowTheCountry() throws Exception {
        given(countryService.lookup("Narnia"))
                .willThrow(new UnknownCountryException("No country found by the name 'Narnia'"));

        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Narnia\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No country found by the name 'Narnia'"));
    }

    @Test
    void returnsBadRequestWithFieldErrorsWhenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").isNotEmpty());
    }

    @Test
    void returnsBadRequestWhenIdIsNotNumeric() throws Exception {
        mockMvc.perform(get("/api/countries/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void returnsBadGatewayWhenUpstreamFails() throws Exception {
        willThrow(new SoapIntegrationException("connection refused"))
                .given(countryService).lookup("Kenya");

        mockMvc.perform(post("/api/countries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kenya\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void echoesSuppliedCorrelationId() throws Exception {
        given(countryService.findById(anyLong())).willThrow(new CountryNotFoundException(300L));

        mockMvc.perform(get("/api/countries/300").header(CorrelationIdFilter.HEADER, "test-correlation-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.correlationId").value("test-correlation-id"));
    }
}
