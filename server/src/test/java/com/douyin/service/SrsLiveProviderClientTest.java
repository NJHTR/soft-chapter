package com.douyin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SrsLiveProviderClientTest {

    @Test
    void treatsAnSrsErrorPayloadAsUnavailableInsteadOfAnAuthoritativeEmptyStreamList() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://srs.test/api/v1/streams?start=0&count=100"))
                .andRespond(withSuccess("{\"code\":401}", MediaType.APPLICATION_JSON));

        SrsLiveProviderClient client = new SrsLiveProviderClient(builder, new ObjectMapper(), "http://srs.test");

        assertFalse(client.snapshot().reachable());
        server.verify();
    }

    @Test
    void acceptsOnlyExplicitlyActivePublishStreams() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://srs.test/api/v1/streams?start=0&count=100"))
                .andRespond(withSuccess("{\"code\":0,\"streams\":["
                        + "{\"name\":\"active\",\"publish\":{\"active\":true}},"
                        + "{\"name\":\"inactive\",\"publish\":{\"active\":false}}]}",
                        MediaType.APPLICATION_JSON));

        SrsLiveProviderClient client = new SrsLiveProviderClient(builder, new ObjectMapper(), "http://srs.test");
        LiveProviderClient.LiveProviderSnapshot snapshot = client.snapshot();

        assertTrue(snapshot.reachable());
        assertTrue(snapshot.activePublishStreams().contains("active"));
        assertFalse(snapshot.activePublishStreams().contains("inactive"));
        server.verify();
    }
}
