package com.douyin.service;

import java.util.Set;

/** Read-only provider health surface used by reconciliation. */
public interface LiveProviderClient {
    LiveProviderSnapshot snapshot();

    record LiveProviderSnapshot(boolean reachable, Set<String> activePublishStreams) {
        public static LiveProviderSnapshot unavailable() {
            return new LiveProviderSnapshot(false, Set.of());
        }
    }
}
