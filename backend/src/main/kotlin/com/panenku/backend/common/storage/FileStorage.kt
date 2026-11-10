package com.panenku.backend.common.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

/**
 * Penyimpanan file privat (MinIO / AWS S3).
 * File KTP, bukti pembayaran, dan bukti refund disini — tidak ada link publik permanen.
 */
@Component
class FileStorage {

    @Value("\${minio.bucket}")
    private lateinit var bucket: String

    /**
     * Upload file dan kembalikan path yang disimpan di bucket.
     * Path contoh: ktp/uuid-filename.jpg
     */
    fun upload(folder: String, file: MultipartFile): String {
        val fileName = "${UUID.randomUUID()}_${file.originalFilename}"
        val path = "$folder/$fileName"

        // TODO: Implementasi upload ke MinIO/S3 setelah Gradle sync selesai
        // Misal: minioClient.putObject(bucket, path, file.inputStream, file.contentType)

        return path
    }

    /**
     * Hapus file dari storage.
     */
    fun delete(path: String) {
        // TODO: Implementasi hapus file dari MinIO/S3
    }
}