package com.panenku.backend.modules.petani.service

import com.panenku.backend.common.exception.ApiException
import com.panenku.backend.modules.petani.dto.ProductPhotoAccess
import com.panenku.backend.modules.shared.entity.Product
import io.minio.*
import io.minio.errors.ErrorResponseException
import io.minio.http.Method
import okhttp3.OkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Dedicated product bucket: never signs KTP/payment/refund object paths. */
@Component
class ProductPhotoStorage(
    @Value("\${panenku.product-photos.enabled:false}") private val enabled: Boolean,
    @Value("\${panenku.product-photos.endpoint}") private val endpoint: String,
    @Value("\${panenku.product-photos.public-endpoint}") private val publicEndpoint: String,
    @Value("\${panenku.product-photos.access-key}") private val accessKey: String,
    @Value("\${panenku.product-photos.secret-key}") private val secretKey: String,
    @Value("\${panenku.product-photos.bucket:panenku-products}") private val bucket: String,
    private val local: LocalProductPhotoStore
) {
    private val client by lazy { buildClient(endpoint) }
    private val signingClient by lazy { buildClient(publicEndpoint) }
    private fun configured() = enabled && (local.selected || (accessKey.isNotBlank() && secretKey.isNotBlank()))
    private fun buildClient(address: String): MinioClient {
        if (!configured()) unavailable()
        val url = if (address.contains("://")) address else "http://$address"
        return MinioClient.builder().endpoint(url).credentials(accessKey, secretKey).region("us-east-1")
            .httpClient(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()).build()
    }
    private fun privateBucket() {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            try { client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()) }
            catch (ex: ErrorResponseException) { if (ex.errorResponse().code() !in setOf("BucketAlreadyOwnedByYou", "BucketAlreadyExists")) throw ex }
        }
        val policy = try { client.getBucketPolicy(GetBucketPolicyArgs.builder().bucket(bucket).build()) }
            catch (ex: ErrorResponseException) { if (ex.errorResponse().code() == "NoSuchBucketPolicy") "" else throw ex }
        if (!policy.isNullOrBlank()) throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PHOTO_BUCKET_NOT_PRIVATE", "Bucket foto produk harus privat tanpa bucket policy publik.")
    }
    internal fun put(product: Product, image: ValidatedProductImage): String = storageCall {
        if (local.selected) return@storageCall local.put(product, image)
        privateBucket()
        val key = "products/${product.farmerId}/${product.id}/${UUID.randomUUID()}.${image.extension}"
        client.putObject(PutObjectArgs.builder().bucket(bucket).`object`(key)
            .stream(ByteArrayInputStream(image.bytes), image.bytes.size.toLong(), -1).contentType(image.contentType).build())
        key
    }
    fun managed(product: Product, key: String?): Boolean = if (local.selected) local.managed(product, key)
        else key != null && Regex("products/${product.farmerId}/${product.id}/[0-9a-f-]{36}\\.(jpg|png)").matches(key)

    fun access(product: Product): ProductPhotoAccess? {
        if (!configured() || !managed(product, product.photoUrl)) return null
        return runCatching { signed(product.photoUrl!!) }.getOrNull()
    }
    internal fun signed(key: String): ProductPhotoAccess = storageCall {
        if (local.selected) return@storageCall local.signed(key)
        val url = signingClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
            .bucket(bucket).`object`(key).expiry(900, TimeUnit.SECONDS).build())
        val query = URI(url).rawQuery.split('&').associate {
            val pair = it.split('=', limit = 2); pair[0] to URLDecoder.decode(pair[1], Charsets.UTF_8)
        }
        val at = LocalDateTime.parse(query.getValue("X-Amz-Date"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
            .toInstant(ZoneOffset.UTC).plusSeconds(query.getValue("X-Amz-Expires").toLong())
        ProductPhotoAccess(url, at)
    }
    internal fun removeUncommitted(key: String) = storageCall {
        if (local.selected) return@storageCall local.delete(key)
        client.removeObject(RemoveObjectArgs.builder().bucket(bucket).`object`(key).build())
    }
    private fun unavailable(): Nothing = throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PRODUCT_PHOTOS_UNAVAILABLE", "Penyimpanan foto produk belum tersedia. Periksa konfigurasi storage lokal atau MinIO foto produk.")
    private fun <T> storageCall(block: () -> T): T {
        if (!configured()) unavailable()
        return try { block() } catch (ex: ApiException) { throw ex } catch (_: Exception) { unavailable() }
    }
}
