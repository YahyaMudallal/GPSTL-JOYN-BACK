package com.gpstl.joyn.common.geocoding;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

@Service
public class AddressGeocodingService {

    private final RestClient restClient;

    @Autowired
    public AddressGeocodingService(
            @Value("${joyn.geocoding.base-url:https://api-adresse.data.gouv.fr}") String baseUrl) {
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(3))
                        .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    AddressGeocodingService(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<Coordinates> geocode(String address) {
        if (address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            var response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/search/")
                            .queryParam("q", "{address}")
                            .build(address.trim()))
                    .retrieve()
                    .body(GeocodingResponse.class);
            if (response == null || response.features() == null) {
                throw new GeocodingException("Address geocoding returned an invalid response");
            }
            if (response.features().isEmpty()) {
                return Optional.empty();
            }
            Feature feature = response.features().getFirst();
            double[] coordinates = feature == null || feature.geometry() == null
                    ? null
                    : feature.geometry().coordinates();
            if (coordinates == null || coordinates.length < 2) {
                throw new GeocodingException("Address geocoding returned invalid coordinates");
            }
            try {
                return Optional.of(new Coordinates(coordinates[0], coordinates[1]));
            } catch (IllegalArgumentException exception) {
                throw new GeocodingException("Address geocoding returned invalid coordinates", exception);
            }
        } catch (RestClientException exception) {
            throw new GeocodingException("Address geocoding service is unavailable", exception);
        }
    }

    private record GeocodingResponse(java.util.List<Feature> features) {
    }

    private record Feature(Geometry geometry) {
    }

    private record Geometry(double[] coordinates) {
    }
}
