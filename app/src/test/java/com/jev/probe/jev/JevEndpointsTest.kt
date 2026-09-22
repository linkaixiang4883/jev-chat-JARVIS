package com.jev.probe.jev

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JevEndpointsTest {
    @Test fun zenRoutesToZenSystemOne() {
        assertEquals("https://opencode.ai/zen/v1/systemone", JevEndpoints.systemOne("zen").url)
    }

    @Test fun typesafeRoutesToOfficial() {
        assertEquals("https://api.typesafe.ai/v1/systemone", JevEndpoints.systemOne("typesafe").url)
    }

    @Test fun replyChatCarriesSessionHeaderAndDefaultModel() {
        val r = JevEndpoints.replyChat("sess-123", "")
        assertEquals("https://opencode.ai/zen/go/v1/chat/completions", r.url)
        assertEquals("sess-123", r.headers["x-opencode-session"])
        assertEquals("glm-5.3-flash", r.defaultModel)
    }

    @Test fun systemOneNeedsNoSessionHeader() {
        assertTrue(JevEndpoints.systemOne("zen").headers.isEmpty())
    }

    @Test fun keysPickPerProvider() {
        assertEquals("oc-key", JevEndpoints.keyFor("zen", "oc-key", "ts-key"))
        assertEquals("ts-key", JevEndpoints.keyFor("typesafe", "oc-key", "ts-key"))
    }

    @Test fun blankModelFallsBackToProviderDefault() {
        assertEquals("jev-1.13-free", JevEndpoints.jevModel("zen", ""))
        assertEquals("jev-latest", JevEndpoints.jevModel("typesafe", ""))
        assertEquals("my-model", JevEndpoints.jevModel("zen", "my-model"))
    }
}
