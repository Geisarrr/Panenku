package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.exception.invalid
import com.panenku.backend.common.exception.missing
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.shared.dto.NotificationDTO
import com.panenku.backend.modules.shared.dto.NotificationPageDTO
import com.panenku.backend.modules.shared.dto.NotificationsMarkedReadDTO
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.util.UUID

/** In-app feed only. It intentionally does not send push, email, or WhatsApp messages. */
@Service
class NotificationService(
    private val actors: ActorService,
    private val db: JdbcTemplate,
    private val clock: Clock
) {
    private val row = RowMapper { rs, _ ->
        NotificationDTO(
            rs.getObject("id", UUID::class.java), rs.getString("title"), rs.getString("body"),
            rs.getString("type"), rs.getBoolean("is_read"), rs.getString("deep_link"),
            rs.getTimestamp("created_at").toInstant()
        )
    }

    @Transactional
    fun create(userId: UUID, title: String, body: String?, type: String, deepLink: String? = null, now: Instant = Instant.now(clock)): UUID {
        val cleanTitle = text(title, 255, "Judul notifikasi")
        val cleanBody = optional(body, 2000, "Isi notifikasi")
        val cleanType = text(type, 50, "Jenis notifikasi")
        val cleanLink = optional(deepLink, 512, "Deep link")
        val id = UUID.randomUUID()
        db.update("""INSERT INTO notifications(id,user_id,title,body,type,is_read,deep_link,created_at)
            VALUES (?,?,?,?,?,false,?,?)""", id, userId, cleanTitle, cleanBody, cleanType, cleanLink, Timestamp.from(now))
        return id
    }

    /** Active admins receive operational notifications, for example refund escalations. */
    @Transactional
    fun createForActiveAdmins(title: String, body: String?, type: String, deepLink: String? = null, now: Instant = Instant.now(clock)) {
        db.queryForList("SELECT id FROM users WHERE role='admin' AND status='active'", UUID::class.java)
            .forEach { create(it, title, body, type, deepLink, now) }
    }

    @Transactional(readOnly = true)
    fun list(actor: UUID, page: Int, size: Int, unreadOnly: Boolean): NotificationPageDTO {
        actors.requireAccountForSupport(actor)
        if (page < 0 || size !in 1..100) invalid("Paginasi tidak valid.")
        val clause = if (unreadOnly) " AND is_read=false" else ""
        val total = db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=?$clause", Long::class.java, actor) ?: 0L
        val unread = db.queryForObject("SELECT count(*) FROM notifications WHERE user_id=? AND is_read=false", Long::class.java, actor) ?: 0L
        val items = db.query("""SELECT id,title,body,type,is_read,deep_link,created_at FROM notifications
            WHERE user_id=?$clause ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?""", row, actor, size, page * size)
        return NotificationPageDTO(items, page, size, total, unread)
    }

    @Transactional
    fun markRead(actor: UUID, id: UUID): NotificationDTO {
        actors.requireAccountForSupport(actor)
        val updated = db.update("UPDATE notifications SET is_read=true WHERE id=? AND user_id=? AND is_read=false", id, actor)
        if (updated == 0) {
            val exists = db.queryForObject("SELECT count(*) FROM notifications WHERE id=? AND user_id=?", Long::class.java, id, actor) ?: 0L
            if (exists == 0L) missing("Notifikasi tidak ditemukan.")
        }
        return db.query("SELECT id,title,body,type,is_read,deep_link,created_at FROM notifications WHERE id=? AND user_id=?", row, id, actor)
            .singleOrNull() ?: missing("Notifikasi tidak ditemukan.")
    }

    @Transactional
    fun markAllRead(actor: UUID): NotificationsMarkedReadDTO {
        actors.requireAccountForSupport(actor)
        return NotificationsMarkedReadDTO(db.update("UPDATE notifications SET is_read=true WHERE user_id=? AND is_read=false", actor))
    }

    private fun text(value: String, max: Int, label: String): String = value.trim().also {
        if (it.isBlank() || it.length > max) invalid("$label wajib diisi dan maksimal $max karakter.")
    }
    private fun optional(value: String?, max: Int, label: String): String? = value?.trim()?.takeIf { it.isNotEmpty() }?.also {
        if (it.length > max) invalid("$label maksimal $max karakter.")
    }
}
