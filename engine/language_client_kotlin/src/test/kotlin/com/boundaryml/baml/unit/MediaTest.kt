package com.boundaryml.baml.unit

import com.boundaryml.baml.*
import com.boundaryml.baml.cffi.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaTest {

    @Test
    fun `BamlImage fromUrl creates url-based image`() {
        val img = BamlImage.fromUrl("https://example.com/cat.jpg", "image/jpeg")
        assertEquals("https://example.com/cat.jpg", img.url)
        assertEquals("image/jpeg", img.mimeType)
        assertNull(img.base64Data)
        assertTrue(img.isUrl)
    }

    @Test
    fun `BamlImage fromBase64 creates base64-based image`() {
        val img = BamlImage.fromBase64("iVBORw0KGgo=", "image/png")
        assertEquals("iVBORw0KGgo=", img.base64Data)
        assertEquals("image/png", img.mimeType)
        assertNull(img.url)
        assertTrue(img.isBase64)
    }

    @Test
    fun `BamlImage encodes as class value`() {
        val img = BamlImage.fromUrl("https://example.com/cat.jpg")
        val encoded = img.encode()
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Image", encoded.classValue.name)
    }

    @Test
    fun `BamlAudio encodes as class value`() {
        val audio = BamlAudio.fromUrl("https://example.com/clip.mp3", "audio/mpeg")
        val encoded = audio.encode()
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Audio", encoded.classValue.name)
    }

    @Test
    fun `BamlPdf encodes as class value`() {
        val pdf = BamlPdf.fromBase64("JVBERi0xLjQ=", "application/pdf")
        val encoded = pdf.encode()
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Pdf", encoded.classValue.name)
    }

    @Test
    fun `BamlVideo encodes as class value`() {
        val video = BamlVideo.fromUrl("https://example.com/clip.mp4")
        val encoded = video.encode()
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Video", encoded.classValue.name)
    }

    @Test
    fun `media encodes through Serde encodeValue`() {
        val img = BamlImage.fromUrl("https://example.com/img.png")
        val encoded = Serde.encodeValue(img)
        assertEquals(HostValue.ValueCase.CLASS_VALUE, encoded.valueCase)
        assertEquals("Image", encoded.classValue.name)
    }

    @Test
    fun `media in function args encodes correctly`() {
        val img = BamlImage.fromUrl("https://example.com/img.png", "image/png")
        val bytes = Serde.encodeArgs(mapOf("image" to img))
        val parsed = HostFunctionArguments.parseFrom(bytes)
        assertEquals(1, parsed.kwargsCount)
        assertEquals("image", parsed.getKwargs(0).stringKey)
        val value = parsed.getKwargs(0).value
        assertEquals(HostValue.ValueCase.CLASS_VALUE, value.valueCase)
        assertEquals("Image", value.classValue.name)
    }

    @Test
    fun `media encode includes correct fields`() {
        val img = BamlImage.fromUrl("https://example.com/cat.jpg", "image/jpeg")
        val encoded = img.encode()
        val fields = encoded.classValue.fieldsList.associate { it.stringKey to it.value }
        assertEquals("image", fields["media_type"]?.stringValue)
        assertEquals("image/jpeg", fields["mime_type"]?.stringValue)
        assertEquals("https://example.com/cat.jpg", fields["url"]?.stringValue)
    }

    @Test
    fun `base64 media encode includes base64 field`() {
        val img = BamlImage.fromBase64("abc123", "image/png")
        val encoded = img.encode()
        val fields = encoded.classValue.fieldsList.associate { it.stringKey to it.value }
        assertEquals("image", fields["media_type"]?.stringValue)
        assertEquals("image/png", fields["mime_type"]?.stringValue)
        assertEquals("abc123", fields["base64"]?.stringValue)
    }

    @Test
    fun `media without mimeType omits it`() {
        val img = BamlImage.fromUrl("https://example.com/cat.jpg")
        val encoded = img.encode()
        val fields = encoded.classValue.fieldsList.associate { it.stringKey to it.value }
        assertTrue("mime_type" !in fields)
    }

    @Test
    fun `all media types have correct mediaType`() {
        assertEquals(MediaType.IMAGE, BamlImage.fromUrl("x").mediaType)
        assertEquals(MediaType.AUDIO, BamlAudio.fromUrl("x").mediaType)
        assertEquals(MediaType.PDF, BamlPdf.fromUrl("x").mediaType)
        assertEquals(MediaType.VIDEO, BamlVideo.fromUrl("x").mediaType)
    }

    @Test
    fun `media toString is readable`() {
        val img = BamlImage.fromUrl("https://example.com/cat.jpg")
        assertTrue(img.toString().contains("Image"))
        assertTrue(img.toString().contains("url="))

        val b64 = BamlImage.fromBase64("abc")
        assertTrue(b64.toString().contains("base64="))
    }
}
