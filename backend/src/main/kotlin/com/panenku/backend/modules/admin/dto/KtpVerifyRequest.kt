package com.panenku.backend.modules.admin.dto

data class KtpVerifyRequest(
    val approved: Boolean,       // true = disetujui, false = ditolak
    val rejectionReason: String? = null  // wajib jika ditolak
)