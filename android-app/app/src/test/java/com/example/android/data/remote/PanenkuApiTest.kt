package id.panenku.app.data.remote

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

class PanenkuApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PanenkuApi
    @Before fun setup() { server = MockWebServer(); server.start(); api = PanenkuApi(server.url("/api/v1/").toString()) }
    @After fun close() { server.shutdown() }
    private fun response(data: String) = MockResponse().setHeader("Content-Type", "application/json").setBody("""{"success":true,"data":$data,"message":"Berhasil"}""")
    private fun session(access: String = "access", refresh: String = "refresh") = """{"accessToken":"$access","refreshToken":"$refresh","user":{"id":"user-id","email":"buyer@example.test","role":"pembeli","status":"active"}}"""
    private suspend fun login() { server.enqueue(response(session())); api.login("buyer@example.test", "password"); server.takeRequest() }
    @Test fun loginUsesSessionEndpointAndRoleFromServer() = runBlocking {
        server.enqueue(response(session()))
        assertEquals("pembeli", api.login("buyer@example.test", "password").user.role)
        val request = server.takeRequest()
        assertEquals("/api/v1/auth/sessions", request.path)
        assertNull(request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("password"))
    }
    @Test fun tokenIsSentAndDecimalIsPreserved() = runBlocking {
        login(); server.enqueue(response("{}"))
        api.post("buyer/cart/items", json("productId" to "id", "quantityKg" to kg("0,25")))
        val request = server.takeRequest()
        assertEquals("Bearer access", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"quantityKg\":0.25"))
    }
    @Test fun expiredAccessRefreshesAndRetriesWithRotatedTokens() = runBlocking {
        login()
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(response(session("new-access", "new-refresh")))
        server.enqueue(response("{\"items\":[]}"))
        api.get("buyer/orders")
        assertEquals("/api/v1/buyer/orders", server.takeRequest().path)
        val refresh = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", refresh.path)
        assertTrue(refresh.body.readUtf8().contains("refresh"))
        assertEquals("Bearer new-access", server.takeRequest().getHeader("Authorization"))
        assertEquals("new-refresh", api.session!!.refreshToken)
    }
    @Test fun invalidRefreshClearsSession() = runBlocking {
        login(); server.enqueue(MockResponse().setResponseCode(401)); server.enqueue(MockResponse().setResponseCode(401))
        try { api.get("buyer/orders"); fail("Expected unauthorized") } catch (e: ApiFailure) { assertEquals(401, e.status) }
        assertNull(api.session)
    }
    @Test fun businessConflictPreservesBackendMessageAndDoesNotRefresh() = runBlocking {
        login(); server.enqueue(MockResponse().setResponseCode(409).setBody("""{"message":"Stok tidak cukup."}"""))
        try { api.post("buyer/checkout", json(), "stable-key"); fail("Expected conflict") }
        catch (e: ApiFailure) { assertEquals("Stok tidak cukup.", e.message) }
        assertEquals("stable-key", server.takeRequest().getHeader("Idempotency-Key"))
        assertNotNull(api.session)
    }
    @Test fun multipartSendsActualBytesAndFields() = runBlocking {
        login(); server.enqueue(response("{}"))
        val image = byteArrayOf(0x50, 0x41, 0x4e, 0x45, 0x4e)
        api.upload("buyer/refunds", image, "image/png", mapOf("orderId" to "order-id"), "upload-key")
        val request = server.takeRequest(); val body = request.body.readUtf8()
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertEquals("upload-key", request.getHeader("Idempotency-Key"))
        assertTrue(body.contains("name=\"image\"")); assertTrue(body.contains("PANEN")); assertTrue(body.contains("order-id"))
    }
    @Test fun logoutHandlesNoContentAndDropsLocalSession() = runBlocking {
        login(); server.enqueue(MockResponse().setResponseCode(204)); api.logout(); assertNull(api.session)
        assertEquals("/api/v1/auth/logout", server.takeRequest().path)
    }
    @Test fun failedLoginDoesNotCreateSession() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"message":"Login gagal"}"""))
        try { api.login("bad", "bad"); fail("Expected failure") } catch (_: ApiFailure) { }
        assertNull(api.session)
    }
    @Test fun retryKeySurvivesRecreationAndIsScopedToUserAndPayload() {
        val storage = mutableMapOf<String, String>()
        fun keys() = RequestKeys({ storage[it] }, { k, v -> storage[k] = v })
        val first = keys().key("a", "checkout", "payload")
        assertEquals(first, keys().key("a", "checkout", "payload"))
        assertNotEquals(first, keys().key("b", "checkout", "payload"))
        assertNotEquals(first, keys().key("a", "checkout", "changed"))
    }
    @Test fun invalidOrOverpreciseWeightsAreRejected() {
        listOf("0", "-1", "0.001", "abc").forEach {
            try { kg(it); fail("Accepted $it") } catch (_: IllegalArgumentException) { } catch (_: IllegalStateException) { }
        }
        assertEquals("0.25", kg("0,25").toPlainString())
    }
    @Test fun successfulActionCanUseNewKeyForAnotherIntentionalSubmission() {
        val storage = mutableMapOf<String, String>()
        val keys = RequestKeys({ storage[it] }, { k, v -> storage[k] = v }, { storage.remove(it); Unit })
        val first = keys.key("a", "refund", "payload")
        keys.complete("a", "refund", "payload")
        assertNotEquals(first, keys.key("a", "refund", "payload"))
    }

}
