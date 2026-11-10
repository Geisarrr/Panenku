package com.panenku.backend.modules.shared.repository

import com.panenku.backend.modules.shared.entity.User
import com.panenku.backend.modules.shared.entity.BuyerProfile
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(email: String): User?
    fun existsByEmail(email: String): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    fun lockById(@Param("id") id: UUID): User?
}
interface BuyerProfileRepository : JpaRepository<BuyerProfile, UUID>
