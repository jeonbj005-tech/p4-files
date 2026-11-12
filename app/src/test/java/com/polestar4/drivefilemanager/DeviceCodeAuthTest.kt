package com.polestar4.drivefilemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DeviceCodeAuthTest {
    @Test fun revokedRefreshTokenRequestsNewLoginInsteadOfShowingGenericBadRequest() {
        val failure = DeviceCodeAuth.tokenFailure("invalid_grant", "Bad Request") as AuthFailure

        assertEquals("TOKEN_REAUTH_REQUIRED", failure.code)
        assertEquals("invalid_grant", failure.reason)
        assertFalse(failure.message.orEmpty().contains("Bad Request"))
    }

    @Test fun temporaryOAuthErrorsRemainRetryable() {
        assertTrue(DeviceCodeAuth.tokenFailure("temporarily_unavailable") is IOException)
        assertTrue(DeviceCodeAuth.tokenFailure("server_error") is IOException)
    }

    @Test fun invalidClientIsReportedSeparatelyFromExpiredUserConsent() {
        val failure = DeviceCodeAuth.tokenFailure("invalid_client", "Bad Request") as AuthFailure

        assertEquals("OAUTH_CLIENT_NOT_FOUND", failure.code)
        assertEquals("invalid_client", failure.reason)
    }
}
