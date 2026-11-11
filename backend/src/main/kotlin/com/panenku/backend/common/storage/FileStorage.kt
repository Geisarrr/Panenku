package com.panenku.backend.common.storage

import com.panenku.backend.common.exception.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

/** Storage is not implemented yet. Never return an object path before persistence succeeds. */
@Component
@Suppress("UNUSED_PARAMETER")
class FileStorage {
    fun upload(folder: String, file: MultipartFile): String = unavailable()
    fun delete(path: String): Unit = unavailable()

    private fun unavailable(): Nothing = throw ApiException(
        HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_NOT_CONFIGURED",
        "Penyimpanan berkas belum dikonfigurasi. Tidak ada berkas yang diunggah atau dihapus."
    )
}
