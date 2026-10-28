package br.com.eduardoamaral.desafiointelbras.domain.model

enum class DeviceField { TYPE, NAME, SERIAL, USERNAME, PASSWORD, MAC_ADDRESS }

enum class ValidationError { REQUIRED, TOO_LONG, INVALID_MAC, INVALID_ALARM_PASSWORD, ALREADY_EXISTS }

/**
 * Regras de negócio dos campos, espelhando o que a API exige (ver /api-json).
 * Validar no app dá feedback imediato e evita uma ida ao servidor que falharia.
 */
object DeviceValidator {
    const val NAME_MAX_LENGTH = 50

    /** Limite do servidor para o número de série (ex.: "ABCDEF1234567"). */
    const val SERIAL_MAX_LENGTH = 13

    private val MAC_REGEX = Regex("^[0-9A-F]{12}$")
    private val ALARM_PASSWORD_REGEX = Regex("^(\\d{4}|\\d{6})$")

    /** Aceita "1a:2b:3c-4d.5e 6f" e devolve "1A2B3C4D5E6F" (a API não aceita separadores). */
    fun normalizeMac(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase()

    fun validate(input: DeviceInput): Map<DeviceField, ValidationError> = buildMap {
        validateName(input.name)?.let { put(DeviceField.NAME, it) }
        when (input) {
            is DeviceInput.Video -> {
                validateSerial(input.serial)?.let { put(DeviceField.SERIAL, it) }
                if (input.username.isBlank()) put(DeviceField.USERNAME, ValidationError.REQUIRED)
                if (input.password.isBlank()) put(DeviceField.PASSWORD, ValidationError.REQUIRED)
            }
            is DeviceInput.Alarm -> {
                validateMac(input.macAddress)?.let { put(DeviceField.MAC_ADDRESS, it) }
                validateAlarmPassword(input.password)?.let { put(DeviceField.PASSWORD, it) }
            }
        }
    }

    fun validateName(name: String): ValidationError? = when {
        name.isBlank() -> ValidationError.REQUIRED
        name.trim().length > NAME_MAX_LENGTH -> ValidationError.TOO_LONG
        else -> null
    }

    fun validateSerial(serial: String): ValidationError? = when {
        serial.isBlank() -> ValidationError.REQUIRED
        serial.trim().length > SERIAL_MAX_LENGTH -> ValidationError.TOO_LONG
        else -> null
    }

    fun validateMac(mac: String): ValidationError? = when {
        mac.isBlank() -> ValidationError.REQUIRED
        !MAC_REGEX.matches(normalizeMac(mac)) -> ValidationError.INVALID_MAC
        else -> null
    }

    fun validateAlarmPassword(password: String): ValidationError? = when {
        password.isEmpty() -> ValidationError.REQUIRED
        !ALARM_PASSWORD_REGEX.matches(password) -> ValidationError.INVALID_ALARM_PASSWORD
        else -> null
    }
}
