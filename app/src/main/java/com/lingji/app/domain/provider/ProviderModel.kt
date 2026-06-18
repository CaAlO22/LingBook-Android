package com.lingji.app.domain.provider

data class ProviderModel(
    val id: String,
    val name: String,
    val description: String = "",
    val supportsVision: Boolean = false
)
