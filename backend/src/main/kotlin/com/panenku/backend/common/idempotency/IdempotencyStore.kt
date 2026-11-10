package com.panenku.backend.common.idempotency

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Penyimpanan idempotensi sederhana (in-memory).
 * Untuk produksi, ganti dengan Redis.
 */
@Component
class IdempotencyStore {

    private val processedKeys = ConcurrentHashMap<String, String>()

    /**
     * Cek apakah request dengan key ini sudah diproses.
     * Jika belum, simpan dan kembalikan true. Jika sudah, kembalikan false.
     */
    fun tryProcess(key: String, response: String): Boolean {
        return processedKeys.putIfAbsent(key, response) == null
    }

    fun getResponse(key: String): String? {
        return processedKeys[key]
    }

    fun remove(key: String) {
        processedKeys.remove(key)
    }
}