package com.douyin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
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

    @Test
    void boundsConfiguredConnectAndReadTimeouts() {
        SrsLiveProviderClient.TimeoutConfiguration defaults =
                SrsLiveProviderClient.timeoutConfiguration(0, 0);
        assertEquals(Duration.ofSeconds(2), defaults.connectTimeout());
        assertEquals(Duration.ofSeconds(5), defaults.readTimeout());

        SrsLiveProviderClient.TimeoutConfiguration bounded =
                SrsLiveProviderClient.timeoutConfiguration(1, 60_000);
        assertEquals(Duration.ofMillis(250), bounded.connectTimeout());
        assertEquals(Duration.ofSeconds(30), bounded.readTimeout());
    }

    @Test
    void treatsAnSrsApiThatDoesNotReturnAsUnavailableWithinReadTimeout() throws IOException {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        httpServer.setExecutor(executor);
        httpServer.createContext("/api/v1/streams", exchange -> {
            try {
                Thread.sleep(1_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "{\"code\":0,\"server\":\"srs-x\",\"streams\":[]}".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        httpServer.start();
        try {
            String apiBase = "http://127.0.0.1:" + httpServer.getAddress().getPort();
            SrsLiveProviderClient client = new SrsLiveProviderClient(
                    RestClient.builder(), new ObjectMapper(), apiBase,
                    SrsLiveProviderClient.timeoutConfiguration(250, 250));

            assertTimeoutPreemptively(Duration.ofSeconds(3),
                    () -> assertFalse(client.snapshot().reachable()));
        } finally {
            httpServer.stop(0);
            executor.shutdownNow();
        }
    }
}
