package com.lingji.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lingji.app.domain.model.APIProvider
import com.lingji.app.domain.provider.ProviderRegistry

@Composable
fun providerDisplayName(provider: APIProvider): String {
    return stringResource(ProviderRegistry.config(provider).displayNameRes)
}
