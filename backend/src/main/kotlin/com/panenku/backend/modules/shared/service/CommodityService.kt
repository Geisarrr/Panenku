package com.panenku.backend.modules.shared.service

import com.panenku.backend.common.exception.*
import com.panenku.backend.common.security.ActorService
import com.panenku.backend.modules.shared.dto.*
import com.panenku.backend.modules.shared.entity.Commodity
import com.panenku.backend.modules.shared.repository.CommodityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
@Transactional
class CommodityService(private val repository: CommodityRepository, private val actors: ActorService) {
    fun list(actor: UUID): List<CommodityDTO> {
        actors.require(actor, "admin")
        return repository.findAll().sortedBy { it.name }.map { CommodityDTO(it.id, it.name, it.isActive) }
    }
    fun save(actor: UUID, id: UUID?, request: CommodityRequest): CommodityDTO {
        actors.require(actor, "admin")
        val name = request.name.trim()
        if (name.isBlank() || name.length > 100) invalid("Nama komoditas wajib, maksimal 100 karakter.")
        val entity = id?.let { repository.lockById(it) ?: missing("Komoditas tidak ditemukan.") } ?: Commodity()
        if (!entity.name.equals(name, ignoreCase = true) && repository.existsByNameIgnoreCase(name)) conflict("Nama komoditas sudah dipakai.")
        entity.name = name; entity.isActive = request.isActive
        repository.saveAndFlush(entity)
        return CommodityDTO(entity.id, entity.name, entity.isActive)
    }
}
