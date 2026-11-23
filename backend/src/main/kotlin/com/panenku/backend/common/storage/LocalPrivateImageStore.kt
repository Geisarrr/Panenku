package com.panenku.backend.common.storage

import com.panenku.backend.common.exception.invalid
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

/** Penyimpanan lokal untuk pengembangan; tidak menyediakan URL atau otorisasi HTTP. */
class LocalPrivateImageStore(directory: Path, private val maxPixels: Long = 12_000_000L) {
    private val root = directory.toAbsolutePath().normalize()

    init {
        Files.createDirectories(root, PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY))
        require(!Files.isSymbolicLink(root) && Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            "Direktori penyimpanan tidak valid"
        }
        val permissions = Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS)
        require(permissions.all { it in OWNER_DIRECTORY }) { "Direktori penyimpanan harus privat" }
    }

    /** Mengembalikan kunci acak, bukan nama file yang diberikan klien. */
    fun upload(file: MultipartFile): String {
        if (file.isEmpty || file.size > MAX_BYTES) invalid("Ukuran gambar harus antara 1 byte dan 5 MB.")
        val bytes = file.inputStream.use { it.readNBytes(MAX_BYTES + 1) }
        return upload(bytes)
    }

    fun upload(bytes: ByteArray): String {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) invalid("Ukuran gambar harus antara 1 byte dan 5 MB.")
        val extension = validateImage(bytes)
        val key = "${UUID.randomUUID()}.$extension"
        val path = root.resolve(key)
        val options = setOf<OpenOption>(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
        var created = false
        try {
            Files.newByteChannel(path, options, PosixFilePermissions.asFileAttribute(OWNER_FILE)).use { channel ->
                created = true
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
            }
        } catch (ex: Exception) {
            if (created) Files.deleteIfExists(path)
            throw ex
        }
        return key
    }

    /** Pemanggil wajib memeriksa kepemilikan dan hak akses sebelum membaca. */
    fun read(key: String): ByteArray {
        val path = pathFor(key)
        return Files.newByteChannel(path, setOf(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)).use { channel ->
            if (channel.size() > MAX_BYTES) invalid("Ukuran gambar terlalu besar.")
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteBuffer.allocate(8192)
            while (channel.read(buffer) != -1) {
                buffer.flip()
                output.write(buffer.array(), 0, buffer.remaining())
                if (output.size() > MAX_BYTES) invalid("Ukuran gambar terlalu besar.")
                buffer.clear()
            }
            output.toByteArray().also { bytes ->
                if (bytes.isEmpty() || !key.endsWith("." + validateImage(bytes))) invalid("Isi gambar tidak sesuai dengan kunci file.")
            }
        }
    }

    fun delete(key: String) {
        Files.deleteIfExists(pathFor(key))
    }

    private fun pathFor(key: String): Path {
        if (!KEY_PATTERN.matches(key)) invalid("Kunci file tidak valid.")
        return root.resolve(key)
    }

    private fun validateImage(bytes: ByteArray): String {
        MemoryCacheImageInputStream(ByteArrayInputStream(bytes)).use { input ->
            val readers = ImageIO.getImageReaders(input)
            if (!readers.hasNext()) invalid("File harus berupa gambar JPEG atau PNG yang valid.")
            val reader = readers.next()
            try {
                reader.input = input
                val extension = when (reader.formatName.lowercase(java.util.Locale.ROOT)) {
                    "jpeg", "jpg" -> "jpg"
                    "png" -> "png"
                    else -> invalid("File harus berupa gambar JPEG atau PNG yang valid.")
                }
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0 || width.toLong() * height > maxPixels) {
                    invalid("Dimensi gambar tidak valid atau terlalu besar.")
                }
                reader.read(0) ?: invalid("Gambar tidak dapat dibaca.")
                return extension
            } catch (ex: com.panenku.backend.common.exception.ApiException) {
                throw ex
            } catch (ex: Exception) {
                invalid("File harus berupa gambar JPEG atau PNG yang valid.")
            } finally {
                reader.dispose()
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 5 * 1024 * 1024
        val KEY_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)")
        val OWNER_DIRECTORY = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)
        val OWNER_FILE = setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    }
}
