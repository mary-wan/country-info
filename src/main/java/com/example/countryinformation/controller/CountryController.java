package com.example.countryinformation.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.countryinformation.dto.CountryLookupRequest;
import com.example.countryinformation.dto.CountryMapper;
import com.example.countryinformation.dto.CountryResponse;
import com.example.countryinformation.dto.CountryUpdateRequest;
import com.example.countryinformation.dto.CountryLookupResult;
import com.example.countryinformation.service.CountryService;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/countries")
@Tag(name = "Countries", description = "Country lookup and CRUD operations")
@Slf4j
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @PostMapping
    @Operation(summary = "Look up a country by name",
            description = "Normalizes the name, returns the stored country if it already exists, "
                    + "otherwise resolves it through the SOAP service and stores it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Country resolved and stored"),
            @ApiResponse(responseCode = "200", description = "Country already existed"),
            @ApiResponse(responseCode = "400", description = "Invalid country name"),
            @ApiResponse(responseCode = "404", description = "No country found by that name")
    })
    public ResponseEntity<CountryResponse> lookup(@Valid @RequestBody CountryLookupRequest request,
            UriComponentsBuilder uriBuilder) {
        log.info("Lookup requested for country name '{}'", request.name());

        CountryLookupResult result = countryService.lookup(request.name());
        CountryResponse body = CountryMapper.toResponse(result.country());

        if (!result.created()) {
            return ResponseEntity.ok(body);
        }

        URI location = uriBuilder.path("/api/countries/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @GetMapping
    @Operation(summary = "List all stored countries")
    public List<CountryResponse> findAll() {
        return countryService.findAll().stream().map(CountryMapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fetch a stored country by its identifier")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Country found"),
            @ApiResponse(responseCode = "404", description = "No country with that identifier")
    })
    public CountryResponse findById(@PathVariable Long id) {
        return CountryMapper.toResponse(countryService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a stored country")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Country updated"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "No country with that identifier")
    })
    public CountryResponse update(@PathVariable Long id, @Valid @RequestBody CountryUpdateRequest request) {
        log.info("Update requested for country id {}", id);
        return CountryMapper.toResponse(countryService.update(id, CountryMapper.toCountryUpdate(request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a stored country")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Country deleted"),
            @ApiResponse(responseCode = "404", description = "No country with that identifier")
    })
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Delete requested for country id {}", id);
        countryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
