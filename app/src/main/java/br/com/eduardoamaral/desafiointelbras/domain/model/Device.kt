package br.com.eduardoamaral.desafiointelbras.domain.model

/**
 * Modelo de domínio usado pela UI. Não conhece Room nem Retrofit.
 *
 * Na API, vídeo e alarme são recursos diferentes (/video-devices e /alarm-centrals),
 * com campos diferentes. Aqui viram um único Device com [details] específico por tipo,
 * para a lista, a busca e os filtros tratarem tudo de forma uniforme.
 */
data class Device(
    val id: String,
    val name: String,
    val details: DeviceDetails,
    val isFavorite: Boolean = false,
) {
    val type: DeviceType get() = details.type
}

sealed interface DeviceDetails {
    val type: DeviceType
    val password: String

    data class Video(
        val serial: String,
        val username: String,
        override val password: String,
    ) : DeviceDetails {
        override val type: DeviceType get() = DeviceType.VIDEO
    }

    data class Alarm(
        /** 12 dígitos hexadecimais, sem separadores (formato exigido pela API). */
        val macAddress: String,
        /** 4 ou 6 dígitos numéricos. */
        override val password: String,
    ) : DeviceDetails {
        override val type: DeviceType get() = DeviceType.ALARM
    }
}

enum class DeviceType { VIDEO, ALARM }

/** Chips do dashboard. */
enum class DeviceFilter { ALL, FAVORITES, VIDEO, ALARM }

/** Dados que o usuário preenche em "Adicionar / Editar". */
sealed interface DeviceInput {
    val name: String

    data class Video(
        override val name: String,
        val serial: String,
        val username: String,
        val password: String,
    ) : DeviceInput

    data class Alarm(
        override val name: String,
        val macAddress: String,
        val password: String,
    ) : DeviceInput
}
