package br.com.eduardoamaral.desafiointelbras.domain.model

/**
 * Resultado de criar ou editar. Os dois casos são sucesso para quem usa: o item já está no
 * banco e a lista já mostra a mudança. A diferença é só se o servidor já sabe dela.
 */
sealed interface WriteResult {
    val device: Device

    /** O servidor confirmou; [device] é a versão dele. */
    data class Synced(override val device: Device) : WriteResult

    /** Sem rede: ficou na fila e vai ao servidor quando a conexão voltar. */
    data class Queued(override val device: Device) : WriteResult
}

/** Monta o dispositivo como ficará gravado, com o MAC já no formato da API. */
fun DeviceInput.toDevice(id: String): Device = Device(
    id = id,
    name = name.trim(),
    details = when (this) {
        is DeviceInput.Video -> DeviceDetails.Video(serial = serial, username = username, password = password)
        is DeviceInput.Alarm -> DeviceDetails.Alarm(
            macAddress = DeviceValidator.normalizeMac(macAddress),
            password = password,
        )
    },
)

/** O caminho inverso: o que a fila envia para a API a partir do item gravado. */
fun Device.toInput(): DeviceInput = when (val d = details) {
    is DeviceDetails.Video ->
        DeviceInput.Video(name = name, serial = d.serial, username = d.username, password = d.password)
    is DeviceDetails.Alarm -> DeviceInput.Alarm(name = name, macAddress = d.macAddress, password = d.password)
}
