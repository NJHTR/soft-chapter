package com.douyin.rtc;

import com.douyin.rtc.domain.CallDomainException;
import com.douyin.rtc.domain.CallErrorCode;
import com.douyin.rtc.repository.RtcAclMapper;
import com.douyin.rtc.service.AclService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AclUserExistenceTest {

    @Test
    void missingTargetIsNotReportedAsOfflineOrUnauthorized() {
        RtcAclMapper mapper = mock(RtcAclMapper.class);
        when(mapper.userExists(2002L)).thenReturn(false);
        AclService acl = new AclService(mapper);

        assertThatThrownBy(() -> acl.assertDirectCallAllowed(1001L, 2002L))
                .isInstanceOf(CallDomainException.class)
                .satisfies(error -> assertThat(((CallDomainException) error).getCode())
                        .isEqualTo(CallErrorCode.USER_NOT_FOUND));
    }
}
