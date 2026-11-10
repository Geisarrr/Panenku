package com.panenku.backend.modules.shared.repository

import com.panenku.backend.modules.shared.entity.*
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.*
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FarmerProfileRepository : JpaRepository<FarmerProfile, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from FarmerProfile f where f.id = :id")
    fun lockById(@Param("id") id: UUID): FarmerProfile?
}
interface PaymentMethodRepository : JpaRepository<FarmerPaymentMethod, UUID> {
    @Query("select p.farmerId as farmerId, p.commodityId as commodityId from Product p where p.id = :id")
    fun reference(@Param("id") id: UUID): ProductReference?
    fun findAllByFarmerId(farmerId: UUID): List<FarmerPaymentMethod>
}
interface SettingRepository : JpaRepository<Setting, String> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select s from Setting s where s.key = :key")
    fun lockByKey(@Param("key") key: String): Setting?
}
interface CommodityRepository : JpaRepository<Commodity, UUID> {
    fun findAllByIsActiveTrueOrderByName(): List<Commodity>
    fun existsByNameIgnoreCase(name: String): Boolean
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Commodity c where c.id = :id")
    fun lockById(@Param("id") id: UUID): Commodity?
}
interface ProductReference {
    val farmerId: UUID
    val commodityId: UUID
}

interface ProductRepository : JpaRepository<Product, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    fun lockById(@Param("id") id: UUID): Product?
    @Query("select p.farmerId as farmerId, p.commodityId as commodityId from Product p where p.id = :id")
    fun reference(@Param("id") id: UUID): ProductReference?
    fun findAllByFarmerId(farmerId: UUID, pageable: Pageable): Page<Product>

    @Query("""select p from Product p, User a, FarmerProfile f, Commodity c
        where p.farmerId = a.id and p.farmerId = f.id and p.commodityId = c.id
        and a.status = 'active' and f.ktpStatus = 'disetujui' and c.isActive = true
        and p.publicationStatus = 'aktif'
        and (:commodityId is null or p.commodityId = :commodityId)
        and (lower(p.name) like :search escape '!' or lower(c.name) like :search escape '!' or lower(f.storeName) like :search escape '!')""")
    fun catalog(@Param("commodityId") commodityId: UUID?, @Param("search") search: String, pageable: Pageable): Page<Product>
}
