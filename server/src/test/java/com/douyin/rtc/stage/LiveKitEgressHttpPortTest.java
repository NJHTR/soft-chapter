package com.douyin.rtc.stage;

import com.douyin.rtc.provider.RtcProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * LiveKit Egress 控制面端口(契约 §6: Stage -&gt; SRS 只经 Egress provider 控制命令)。
 * 验证 twirp 路径、Bearer(admin grant)与错误映射;真实 Egress 环境验收未执行。
 */
class LiveKitEgressHttpPortTest {

    private StageProperties stageProps;
    private RtcProperties rtcProps;
    private LiveKitEgressHttpPort port;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        stageProps = new StageProperties();
        stageProps.getEgress().setEnabled(true);
        stageProps.getEgress().setBaseUrl("http://egress:7885");
        rtcProps = new RtcProperties();
        rtcProps.setLivekitApiKey("devkey");
        rtcProps.setLivekitApiSecret("0123456789abcdef0123456789abcdef");
        RestTemplate restTemplate = new RestTemplate();
        port = new LiveKitEgressHttpPort(stageProps, rtcProps, restTemplate);
        server = MockRestServiceServer.createServer(restTemplate);
    }

    @Test
    void successReturnsEgressId() {
        server.expect(requestTo("http://egress:7885/twirp/livekit.Egress/StartRoomCompositeEgress"))
                .andExpect(method(POST))
                .andExpect(header("Authorization", org.hamcrest.Matchers.startsWith("Bearer ")))
                .andRespond(withSuccess(
                        "{\"egressId\":\"EGR-123\",\"trackId\":\"\"}",
                        MediaType.APPLICATION_JSON));

        String ref = port.requestStageEgress("9001", "room-9001");
        assertThat(ref).isEqualTo("EGR-123");
        server.verify();
    }

    @Test
    void serverErrorMapsToProviderException() {
        server.expect(requestTo("http://egress:7885/twirp/livekit.Egress/StartRoomCompositeEgress"))
                .andExpect(method(POST))
                .andRespond(withServerError());
        assertThatThrownBy(() -> port.requestStageEgress("9001", "room-9001"))
                .isInstanceOf(StageProviderException.class)
                .hasMessageContaining("http=500");
    }

    @Test
    void malformedResponseMapsToProviderException() {
        server.expect(requestTo("http://egress:7885/twirp/livekit.Egress/StartRoomCompositeEgress"))
                .andExpect(method(POST))
                .andRespond(withSuccess("{\"error\":\"boom\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> port.requestStageEgress("9001", "room-9001"))
                .isInstanceOf(StageProviderException.class)
                .hasMessageContaining("egressId");
    }

    @Test
    void roomHasAdminBearerGrantClaims() {
        StageMember member = StageMember.audience(1, 2);
        assertThat(port.revokePublishPermission(member)).isFalse();
    }
}