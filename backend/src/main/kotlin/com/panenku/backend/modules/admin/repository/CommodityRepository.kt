package com.panenku.backend.modules.admin.repository

import com.panenku.backend.modules.shared.entity.Commodity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CommodityRepository : JpaRepository<Commodity, UUID>