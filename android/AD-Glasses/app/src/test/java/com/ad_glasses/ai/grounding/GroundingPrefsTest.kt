package com.ad_glasses.ai.grounding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GroundingPrefsTest {
    @Test
    fun validHttpsEndpointsAreNormalized() {
        assertEquals(
            "https://nominatim.example.com",
            GroundingPrefs.validatedEndpoint(
                " https://nominatim.example.com/ ",
                GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL,
                allowPath = false,
            ),
        )
        assertEquals(
            "https://overpass.example.com/api/interpreter",
            GroundingPrefs.validatedEndpoint(
                "https://overpass.example.com/api/interpreter/",
                GroundingPrefs.DEFAULT_OVERPASS_ENDPOINT,
                allowPath = true,
            ),
        )
        assertEquals(
            "https://router.example.com:8443",
            GroundingPrefs.validatedEndpoint(
                "https://router.example.com:8443",
                GroundingPrefs.DEFAULT_OSRM_BASE_URL,
                allowPath = false,
            ),
        )
    }

    @Test
    fun blankValueFallsBackToKnownGoodDefault() {
        assertEquals(
            GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL,
            GroundingPrefs.validatedEndpoint("  ", GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL, allowPath = false),
        )
    }

    @Test
    fun unsafeOrMalformedEndpointsAreRejected() {
        listOf(
            "http://example.com",
            "https://user:pass@example.com",
            "https://example.com/path?token=secret",
            "https://example.com/path#fragment",
            "https://example.com/../other",
            "https:///missing-host",
        ).forEach { endpoint ->
            assertThrows(endpoint, IllegalArgumentException::class.java) {
                GroundingPrefs.validatedEndpoint(endpoint, GroundingPrefs.DEFAULT_OVERPASS_ENDPOINT, allowPath = true)
            }
        }
    }

    @Test
    fun baseServicesRejectUnexpectedPaths() {
        assertThrows(IllegalArgumentException::class.java) {
            GroundingPrefs.validatedEndpoint(
                "https://nominatim.example.com/search",
                GroundingPrefs.DEFAULT_NOMINATIM_BASE_URL,
                allowPath = false,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            GroundingPrefs.validatedEndpoint(
                "https://router.example.com/osrm",
                GroundingPrefs.DEFAULT_OSRM_BASE_URL,
                allowPath = false,
            )
        }
    }
}
