package id.panenku.app.data.remote

import com.google.gson.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** DTO envelopes follow backend/common/dto/ApiResponse.kt. No domain demo models. */
data class ApiEnvelope(val success: Boolean = true, val data: JsonElement?, val message: String?)
data class ApiUser(val id: String, val email: String, val role: String, val status: String)
data class ApiSession(val accessToken: String, val refreshToken: String, val user: ApiUser)
class ApiFailure(val status: Int, message: String) : IOException(message)
interface PanenkuHttp {
    @GET suspend fun get(@Url path: String, @Header("Authorization") auth: String?): Response<ApiEnvelope>
    @POST suspend fun post(@Url path: String, @Header("Authorization") auth: String?, @Body body: JsonObject,
        @Header("Idempotency-Key") key: String? = null): Response<ApiEnvelope>
    @PUT suspend fun put(@Url path: String, @Header("Authorization") auth: String?, @Body body: JsonObject): Response<ApiEnvelope>
    @DELETE suspend fun delete(@Url path: String, @Header("Authorization") auth: String?): Response<ApiEnvelope>
    @Multipart @POST suspend fun upload(@Url path: String, @Header("Authorization") auth: String?,
        @Part image: MultipartBody.Part, @PartMap fields: Map<String, @JvmSuppressWildcards RequestBody>,
        @Header("Idempotency-Key") key: String): Response<ApiEnvelope>
    @GET suspend fun image(@Url path: String, @Header("Authorization") auth: String?): Response<ResponseBody>
}

/** Tokens live only in memory: process restart requires login; no plaintext token files or logs. */
class PanenkuApi(baseUrl: String, client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()) {
    private val gson = Gson()
    private val http = Retrofit.Builder().baseUrl(baseUrl).client(client)
        .addConverterFactory(GsonConverterFactory.create(gson)).build().create(PanenkuHttp::class.java)
    @Volatile var session: ApiSession? = null
        private set
    private val refreshMutex = Mutex()
    private fun auth() = session?.let { "Bearer ${it.accessToken}" }
    private fun checked(path: String): String {
        require(!path.contains(":") && !path.startsWith("/") && !path.contains("..")) { "Path API tidak valid" }
        return path
    }
    private fun unwrap(r: Response<ApiEnvelope>): JsonElement {
        if (!r.isSuccessful) {
            val message = runCatching { JsonParser.parseString(r.errorBody()?.string()).asJsonObject.text("message") }.getOrNull()
            throw ApiFailure(r.code(), message?.takeIf { it.isNotBlank() } ?: "Permintaan gagal (${r.code()}).")
        }
        if (r.code() == 204) return JsonNull.INSTANCE
        val envelope = r.body() ?: throw IOException("Respons server kosong.")
        if (!envelope.success) throw ApiFailure(r.code(), envelope.message ?: "Permintaan ditolak.")
        return envelope.data ?: JsonNull.INSTANCE
    }
    private suspend fun authorized(call: suspend () -> Response<ApiEnvelope>): JsonElement {
        val token = session?.accessToken
        val first = call()
        if (first.code() != 401 || token == null) return unwrap(first)
        // Serialize rotating refresh tokens. A successful request is retried at most once.
        refreshMutex.withLock {
            if (session?.accessToken == token) {
                val current = session ?: throw ApiFailure(401, "Silakan masuk kembali.")
                try {
                    session = gson.fromJson(unwrap(http.post("auth/refresh", null,
                        json("refreshToken" to current.refreshToken))), ApiSession::class.java)
                } catch (e: ApiFailure) {
                    if (e.status == 401) session = null
                    throw e
                }
            }
        }
        return unwrap(call())
    }
    suspend fun login(email: String, password: String): ApiSession {
        val result = gson.fromJson(unwrap(http.post("auth/sessions", null,
            json("email" to email.trim(), "password" to password))), ApiSession::class.java)
        require(result.user.role in setOf("pembeli", "petani", "admin")) { "Peran akun tidak dikenal." }
        session = result
        return result
    }
    suspend fun register(email: String, password: String, name: String, role: String) {
        unwrap(http.post("auth/register", null, json("email" to email.trim(), "password" to password,
            "role" to role, "fullName" to name, "storeName" to name.takeIf { role == "petani" })))
    }
    suspend fun logout() {
        val current = session
        try { if (current != null) unwrap(http.post("auth/logout", null, json("refreshToken" to current.refreshToken))) }
        finally { session = null }
    }
    suspend fun get(path: String) = authorized { http.get(checked(path), auth()) }
    suspend fun post(path: String, body: JsonObject = json(), key: String? = null) = authorized { http.post(checked(path), auth(), body, key) }
    suspend fun put(path: String, body: JsonObject) = authorized { http.put(checked(path), auth(), body) }
    suspend fun delete(path: String) = authorized { http.delete(checked(path), auth()) }
    suspend fun upload(path: String, bytes: ByteArray, mime: String, fields: Map<String, String>, key: String, partName: String = "image"): JsonElement {
        require(mime in setOf("image/jpeg", "image/png")) { "Gunakan gambar JPEG atau PNG." }
        require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "Ukuran gambar maksimal 5 MB." }
        val image = MultipartBody.Part.createFormData(partName, if (mime == "image/png") "bukti.png" else "bukti.jpg", bytes.toRequestBody(mime.toMediaType()))
        val parts = fields.mapValues { it.value.toRequestBody("text/plain".toMediaType()) }
        return authorized { http.upload(checked(path), auth(), image, parts, key) }
    }
    suspend fun image(path: String): ByteArray {
        // Refresh uses the same authenticated endpoint before fetching private bytes.
        get("auth/me")
        val r = http.image(checked(path), auth())
        if (!r.isSuccessful) { r.errorBody()?.close(); throw ApiFailure(r.code(), "Gambar belum dapat diambil (${r.code()}).") }
        return r.body()?.use { it.bytes() } ?: throw IOException("Gambar kosong.")
    }
}
fun json(vararg values: Pair<String, Any?>): JsonObject = Gson().toJsonTree(linkedMapOf(*values)).asJsonObject
fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()
fun JsonObject.obj(key: String): JsonObject = get(key)?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject()
fun JsonObject.list(key: String): List<JsonObject> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }.orEmpty()
fun JsonElement.rows(): List<JsonObject> = when {
    isJsonArray -> asJsonArray.map { it.asJsonObject }
    isJsonObject -> asJsonObject.list("items")
    else -> emptyList()
}
fun JsonObject.long(key: String): Long = get(key)?.takeUnless { it.isJsonNull }?.asLong ?: 0L
fun JsonObject.flag(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asBoolean ?: false
fun kg(input: String): BigDecimal {
    val value = input.trim().replace(',', '.').toBigDecimalOrNull() ?: error("Isi jumlah kg yang valid.")
    require(value > BigDecimal.ZERO && value.stripTrailingZeros().scale() <= 2) { "Jumlah minimal 0,01 kg, maksimal dua angka desimal." }
    return value
}
/** Same user, action and payload reuse a key after network failure; stores hashes/keys only. */
class RequestKeys(private val read: (String) -> String?, private val write: (String, String) -> Unit, private val remove: (String) -> Unit = {}) {
    private fun hash(user: String, path: String, payload: String): String = MessageDigest.getInstance("SHA-256").digest("$user|$path|$payload".toByteArray())
            .joinToString("") { "%02x".format(it) }
    fun complete(user: String, path: String, payload: String) = remove(hash(user, path, payload))
    fun key(user: String, path: String, payload: String): String {
        val hash = hash(user, path, payload)
        return read(hash) ?: UUID.randomUUID().toString().also { write(hash, it) }
    }
}
