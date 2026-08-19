package com.douyin.service;

import java.util.Map;
import java.util.Set;

/** Read-only provider health surface used by reconciliation. */
public interface LiveProviderClient {
    LiveProviderSnapshot snapshot();

    record LiveProviderSnapshot(boolean reachable,
                                Set<String> activePublishStreams,
                                Map<String, String> activePublishSessions,
                                String providerServerId) {
        public LiveProviderSnapshot(boolean reachable, Set<String> activePublishStreams) {
            this(reachable, activePublishStreams, Map.of(), null);
        }

        public static LiveProviderSnapshot unavailable() {
            return new LiveProviderSnapshot(false, Set.of(), Map.of(), null);
        }
    }
}
