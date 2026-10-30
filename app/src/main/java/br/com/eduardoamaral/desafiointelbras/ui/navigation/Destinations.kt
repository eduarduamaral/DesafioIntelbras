package br.com.eduardoamaral.desafiointelbras.ui.navigation

import kotlinx.serialization.Serializable

// Rotas type-safe (Navigation 2.8+). O Navigation guarda cada propriedade da rota no
// SavedStateHandle com o nome da propriedade como chave; os ViewModels leem por essa chave.

/** Nome da propriedade deviceId das rotas abaixo. NavArgsTest garante que continuam iguais. */
const val DEVICE_ID_ARG = "deviceId"

@Serializable
data object DashboardDestination

@Serializable
data class DeviceFormDestination(val deviceId: String? = null)

@Serializable
data class DeviceDetailsDestination(val deviceId: String)
