package com.gpstl.joyn.common.geocoding;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.MediaType.APPLICATION_JSON;

class AddressGeocodingServiceTest {

    @Test
    void geocodesUsingFirstMostRelevantFeature() {
        RestClient.Builder builder = RestClient.builder();
        builder.baseUrl("http://geocoder.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://geocoder.test/search/?q=Paris%209e"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {"features":[
                          {"geometry":{"coordinates":[2.347,48.859]}},
                          {"geometry":{"coordinates":[-0.4,44.7]}}
                        ]}
                        """, APPLICATION_JSON));
        AddressGeocodingService service = new AddressGeocodingService(builder.build());

        assertThat(service.geocode("Paris 9e"))
                .contains(new Coordinates(2.347, 48.859));
        server.verify();
    }

    @Test
    void noMatchingAddressHasNoCoordinates() {
        RestClient.Builder builder = RestClient.builder();
        builder.baseUrl("http://geocoder.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://geocoder.test/search/?q=unknown"))
                .andRespond(withSuccess("{\"features\":[]}", APPLICATION_JSON));
        AddressGeocodingService service = new AddressGeocodingService(builder.build());

        assertThat(service.geocode("unknown")).isEmpty();
        server.verify();
    }

    @Test
    void rejectsMalformedCoordinates() {
        RestClient.Builder builder = RestClient.builder();
        builder.baseUrl("http://geocoder.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://geocoder.test/search/?q=invalid"))
                .andRespond(withSuccess("""
                        {"features":[{"geometry":{"coordinates":[250,48.859]}}]}
                        """, APPLICATION_JSON));
        AddressGeocodingService service = new AddressGeocodingService(builder.build());

        assertThatThrownBy(() -> service.geocode("invalid"))
                .isInstanceOf(GeocodingException.class)
                .hasMessage("Address geocoding returned invalid coordinates");
        server.verify();
    }
}
