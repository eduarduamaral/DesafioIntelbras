package br.com.eduardoamaral.desafiointelbras.mockapi

// Regras do servidor, escritas à parte de propósito: o servidor falso não usa o DeviceValidator
// do app, senão um erro de regra no app passaria despercebido.

private const val SERIAL_MAX_LENGTH = 13
private val MAC_REGEX = Regex("^[0-9A-Fa-f]{12}$")
private val ALARM_PASSWORD_REGEX = Regex("^(\\d{4}|\\d{6})$")

fun validateVideo(body: VideoDeviceBody): List<String> = buildList {
    requireText("name", body.name)
    requireText("serial", body.serial)
    if (!body.serial.isNullOrBlank() && body.serial.length > SERIAL_MAX_LENGTH) {
        add("serial must be shorter than or equal to $SERIAL_MAX_LENGTH characters")
    }
    requireText("username", body.username)
    requireText("password", body.password)
}

fun validateAlarm(body: AlarmCentralBody): List<String> = buildList {
    requireText("name", body.name)
    requireText("macAddress", body.macAddress)
    if (!body.macAddress.isNullOrBlank() && !MAC_REGEX.matches(body.macAddress)) {
        add("macAddress must be 12 hexadecimal characters without separators")
    }
    requireText("password", body.password)
    if (!body.password.isNullOrBlank() && !ALARM_PASSWORD_REGEX.matches(body.password)) {
        add("password must have 4 or 6 digits")
    }
}

private fun MutableList<String>.requireText(field: String, value: String?) {
    if (value.isNullOrBlank()) add("$field should not be empty")
}
