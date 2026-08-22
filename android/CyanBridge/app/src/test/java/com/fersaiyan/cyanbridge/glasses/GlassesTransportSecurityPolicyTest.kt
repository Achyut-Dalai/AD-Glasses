package com.fersaiyan.cyanbridge.glasses

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassesTransportSecurityPolicyTest {
    @Test
    fun accepts_only_rfc1918_device_addresses() {
        assertEquals("192.168.49.2", GlassesTransportSecurityPolicy.privateIpv4OrNull("192.168.49.2"))
        assertEquals("10.0.0.7", GlassesTransportSecurityPolicy.privateIpv4OrNull("10.0.0.7"))
        assertEquals("172.16.4.8", GlassesTransportSecurityPolicy.privateIpv4OrNull("172.16.4.8"))
        assertNull(GlassesTransportSecurityPolicy.privateIpv4OrNull("8.8.8.8"))
        assertNull(GlassesTransportSecurityPolicy.privateIpv4OrNull("192.168.49.255"))
        assertNull(GlassesTransportSecurityPolicy.privateIpv4OrNull("192.168.49.2.evil"))
    }

    @Test
    fun rejects_manifest_path_traversal_and_url_injection() {
        assertEquals("IMG_1234.jpg", GlassesTransportSecurityPolicy.mediaFileNameOrNull("IMG_1234.jpg"))
        listOf("../secret.jpg", "folder/photo.jpg", "a.jpg?x=1", "a%2fb.jpg", "a.jpg#fragment")
            .forEach { assertNull(GlassesTransportSecurityPolicy.mediaFileNameOrNull(it)) }
    }

    @Test
    fun builds_encoded_local_device_url() {
        val url = GlassesTransportSecurityPolicy.mediaUrl("192.168.49.2", "My photo.jpg")
        assertTrue(url.startsWith("http://192.168.49.2/files/"))
        assertTrue(url.endsWith("My%20photo.jpg"))
    }
}
