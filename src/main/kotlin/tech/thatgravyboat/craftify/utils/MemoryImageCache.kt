package tech.thatgravyboat.craftify.utils

import com.google.common.hash.Hashing
import com.mojang.blaze3d.platform.NativeImage
import earth.terrarium.olympus.client.images.ImageProvider
import earth.terrarium.olympus.client.images.ImageProviders
import net.minecraft.resources.Identifier
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import javax.imageio.ImageIO

object ImageCaches {

    private val HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()

    val MISSING_TEXTURE: Identifier = Identifier.withDefaultNamespace("missingno")
    val ALBUM: ImageProvider<URI> = create("craftify_jpeg_url", Duration.ofMinutes(1))
    val ASSET: ImageProvider<URI> = create("craftify_asset_url", Duration.ofHours(1))

    private fun create(id: String, duration: Duration): ImageProvider<URI> {
        return ImageProviders.register(
            id,
            this::fetch,
            { url -> Hashing.sha256().hashUnencodedChars(url.toString()) },
            duration
        )
    }

    private fun fetch(url: URI): CompletableFuture<NativeImage> {
        // Windows SMTC providers such as the SoundCloud desktop app expose
        // album artwork as raw Base64 rather than an HTTP URL.
        if (url.scheme == null) {
            return CompletableFuture.supplyAsync {
                try {
                    val bytes = Base64.getDecoder().decode(url.toString())
                    val image = ImageIO.read(ByteArrayInputStream(bytes))
                        ?: throw IllegalArgumentException("Unsupported Base64 image format")
                    image.toNative()
                } catch (e: Exception) {
                    throw RuntimeException("Failed to decode embedded album artwork", e)
                }
            }
        }

        return HTTP.sendAsync(HttpRequest.newBuilder(url).build(), HttpResponse.BodyHandlers.ofInputStream()).thenApply { response ->
            if (response.statusCode() in 200..299) {
                try {
                    ImageIO.read(response.body()).toNative()
                } catch (e: Exception) {
                    e.printStackTrace()
                    throw RuntimeException("Failed to read image from URL: $url", e)
                }
            } else {
                throw RuntimeException("Failed to fetch image from URL: $url, Status Code: ${response.statusCode()}")
            }
        }
    }
}
