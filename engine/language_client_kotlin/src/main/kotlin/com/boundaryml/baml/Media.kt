package com.boundaryml.baml

import com.boundaryml.baml.cffi.HostValue
import com.boundaryml.baml.cffi.hostValue
import com.boundaryml.baml.cffi.hostClassValue
import com.boundaryml.baml.cffi.hostMapValue

/**
 * Media type enum matching proto MediaTypeEnum.
 */
enum class MediaType {
    IMAGE, AUDIO, PDF, VIDEO
}

/**
 * Base class for all BAML media types.
 * Represents media content that can be passed as input to BAML functions
 * (e.g., images for multi-modal LLM calls).
 *
 * Media objects are created via factory methods on the concrete subclasses
 * (BamlImage, BamlAudio, BamlPdf, BamlVideo) and encoded as handle references
 * when sent to the Rust engine.
 */
sealed class BamlMedia : BamlSerializable {
    abstract val mediaType: MediaType
    abstract val mimeType: String?
    abstract val url: String?
    abstract val base64Data: String?

    val isUrl: Boolean get() = url != null
    val isBase64: Boolean get() = base64Data != null

    override fun encode(): HostValue {
        // Encode media as a class value with the media properties.
        // The Rust engine recognizes these field names and constructs the media object.
        val fields = mutableMapOf<String, Any?>()
        fields["media_type"] = mediaType.name.lowercase()
        if (mimeType != null) fields["mime_type"] = mimeType
        if (url != null) fields["url"] = url
        if (base64Data != null) fields["base64"] = base64Data
        return Serde.encodeClass(bamlTypeName(), fields)
    }

    override fun toString(): String {
        val source = when {
            url != null -> "url=${url!!.take(50)}${if (url!!.length > 50) "..." else ""}"
            base64Data != null -> "base64=[${base64Data!!.length} chars]"
            else -> "empty"
        }
        return "${bamlTypeName()}($source${mimeType?.let { ", mimeType=$it" } ?: ""})"
    }
}

/**
 * BAML Image type for multi-modal function inputs.
 */
class BamlImage private constructor(
    override val mimeType: String?,
    override val url: String?,
    override val base64Data: String?
) : BamlMedia() {
    override val mediaType: MediaType = MediaType.IMAGE
    override fun bamlTypeName(): String = "Image"

    companion object {
        fun fromUrl(url: String, mimeType: String? = null): BamlImage =
            BamlImage(mimeType = mimeType, url = url, base64Data = null)

        fun fromBase64(base64: String, mimeType: String? = null): BamlImage =
            BamlImage(mimeType = mimeType, url = null, base64Data = base64)
    }
}

/**
 * BAML Audio type for multi-modal function inputs.
 */
class BamlAudio private constructor(
    override val mimeType: String?,
    override val url: String?,
    override val base64Data: String?
) : BamlMedia() {
    override val mediaType: MediaType = MediaType.AUDIO
    override fun bamlTypeName(): String = "Audio"

    companion object {
        fun fromUrl(url: String, mimeType: String? = null): BamlAudio =
            BamlAudio(mimeType = mimeType, url = url, base64Data = null)

        fun fromBase64(base64: String, mimeType: String? = null): BamlAudio =
            BamlAudio(mimeType = mimeType, url = null, base64Data = base64)
    }
}

/**
 * BAML PDF type for multi-modal function inputs.
 */
class BamlPdf private constructor(
    override val mimeType: String?,
    override val url: String?,
    override val base64Data: String?
) : BamlMedia() {
    override val mediaType: MediaType = MediaType.PDF
    override fun bamlTypeName(): String = "Pdf"

    companion object {
        fun fromUrl(url: String, mimeType: String? = null): BamlPdf =
            BamlPdf(mimeType = mimeType, url = url, base64Data = null)

        fun fromBase64(base64: String, mimeType: String? = null): BamlPdf =
            BamlPdf(mimeType = mimeType, url = null, base64Data = base64)
    }
}

/**
 * BAML Video type for multi-modal function inputs.
 */
class BamlVideo private constructor(
    override val mimeType: String?,
    override val url: String?,
    override val base64Data: String?
) : BamlMedia() {
    override val mediaType: MediaType = MediaType.VIDEO
    override fun bamlTypeName(): String = "Video"

    companion object {
        fun fromUrl(url: String, mimeType: String? = null): BamlVideo =
            BamlVideo(mimeType = mimeType, url = url, base64Data = null)

        fun fromBase64(base64: String, mimeType: String? = null): BamlVideo =
            BamlVideo(mimeType = mimeType, url = null, base64Data = base64)
    }
}
