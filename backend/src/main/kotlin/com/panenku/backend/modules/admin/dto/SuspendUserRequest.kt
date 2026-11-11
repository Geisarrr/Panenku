package com.panenku.backend.modules.admin.dto

data class SuspendUserRequest(
    val suspend: Boolean,    // true = suspend, false = pulihkan
    val reason: String? = null
)