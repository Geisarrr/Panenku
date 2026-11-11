package com.panenku.backend.modules.admin.repository

import com.panenku.backend.modules.shared.entity.Setting
import org.springframework.data.jpa.repository.JpaRepository

interface SettingsRepository : JpaRepository<Setting, String>
