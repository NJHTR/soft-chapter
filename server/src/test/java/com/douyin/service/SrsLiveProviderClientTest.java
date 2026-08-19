package com.douyin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                .andRespond(withSuccess("{\"code\":0,\"server\":\"srs-x\",\"streams\":["
                        + "{\"name\":\"active\",\"publish\":{\"active\":true,\"cid\":\"client-1\"}},"
                        + "{\"name\":\"inactive\",\"publish\":{\"active\":false}}]}",
                        MediaType.APPLICATION_JSON));

        SrsLiveProviderClient client = new SrsLiveProviderClient(builder, new ObjectMapper(), "http://srs.test");
        LiveProviderClient.LiveProviderSnapshot snapshot = client.snapshot();

        assertTrue(snapshot.reachable());
        assertTrue(snapshot.activePublishStreams().contains("active"));
        assertFalse(snapshot.activePublishStreams().contains("inactive"));
        assertEquals("srs-x:client-1", snapshot.activePublishSessions().get("active"));
        assertEquals("srs-x", snapshot.providerServerId());
        server.verify();
    }

    @Test
    void rejectsAnActivePublishWithoutClientGeneration() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://srs.test/api/v1/streams?start=0&count=100"))
                .andRespond(withSuccess("{\"code\":0,\"server\":\"srs-x\",\"streams\":["
                        + "{\"name\":\"active\",\"publish\":{\"active\":true}}]}",
                        MediaType.APPLICATION_JSON));

        SrsLiveProviderClient client = new SrsLiveProviderClient(builder, new ObjectMapper(), "http://srs.test");

        assertFalse(client.snapshot().reachable());
        server.verify();
    }

    @Test
    void rejectsAResponseWithoutProviderServerGeneration() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://srs.test/api/v1/streams?start=0&count=100"))
                .andRespond(withSuccess("{\"code\":0,\"streams\":[]}", MediaType.APPLICATION_JSON));

        SrsLiveProviderClient client = new SrsLiveProviderClient(builder, new ObjectMapper(), "http://srs.test");

        assertFalse(client.snapshot().reachable());
        server.verify();
    }

    @Test
    void rejectsSnapshotWhenProviderServerChangesBetweenPages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String firstPageStreams = IntStream.range(0, 100)
                .mapToObj(index -> "{\"name\":\"stream-" + index
                        + "\",\"publish\":{\"active\":false}}")
                .reduce((left, right) -> left + "," + right)
                .orElseThrow();
        server.expect(requestTo("http://srs.test/api/v1/streams?start=0&count=100"))
                .andRespond(withSuccess("{\"code\":0,\"server\":\"srs-x\",\"streams\":["
                        + firstPageStreams + "]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://srs.test/api/v1/streams?start=100&count=100"))
                .andRespond(withSuccess("{\"code\":0,\"server\":\"srs-y\",\"streams\":[]}",
                        MediaType.APPLICATION_JSON));

        SrsLiveProviderClient client = new SrsLiveProviderClient(builder, new ObjectMapper(), "http://srs.test");

        assertFalse(client.snapshot().reachable());
        server.verify();
    }
}
