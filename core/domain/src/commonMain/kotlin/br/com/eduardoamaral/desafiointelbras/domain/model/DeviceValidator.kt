package br.com.eduardoamaral.desafiointelbras.domain.model

enum class DeviceField { TYPE, NAME, SERIAL, USERNAME, PASSWORD, MAC_ADDRESS }

enum class ValidationError { REQUIRED, TOO_LONG, INVALID_MAC, INVALID_ALARM_PASSWORD, ALREADY_EXISTS }

/**
 * Regras de negócio dos campos, espelhando o que a API exige (ver /api-json).
 *
 * Duas famílias de funções, todas puras e testadas em DeviceValidatorTest:
 * - sanitize*: aplicadas enquanto o usuário digita; descartam o que nunca seria válido
 *   (letra na senha do alarme, 14º caractere do serial...).
 * - validate*: aplicadas ao salvar; apontam o que ainda falta (campo vazio, tamanho errado...).
 *
 * Validar no app dá feedback imediato e evita uma ida ao servidor que falharia.
 */
object DeviceValidator {
    const val NAME_MAX_LENGTH = 50

    /** Limite do servidor para o número de série (ex.: "ABCDEF1234567"). */
    const val SERIAL_MAX_LENGTH = 13

    /** MAC sem separadores: 12 dígitos hexadecimais. */
    const val MAC_LENGTH = 12

    /** Senha da central de alarme: 4 ou 6 dígitos. */
    const val ALARM_PASSWORD_MAX_LENGTH = 6

    private const val HEX_DIGITS = "0123456789ABCDEF"
    private val MAC_REGEX = Regex("^[0-9A-F]{$MAC_LENGTH}$")
    private val ALARM_PASSWORD_REGEX = Regex("^(\\d{4}|\\d{6})$")

    /** Aceita "1a:2b:3c-4d.5e 6f" e devolve "1A2B3C4D5E6F" (a API não aceita separadores). */
    fun normalizeMac(raw: String): String = raw.filter { it.isLetterOrDigit() }.uppercase()

    // ---- Entrada (enquanto digita) ----

    /** Corta no limite do servidor: o contador do campo para em 13. */
    fun sanitizeSerialInput(raw: String): String = raw.take(SERIAL_MAX_LENGTH)

    /** Só hexadecimal, maiúsculo, sem separadores, no máximo 12 caracteres. */
    fun sanitizeMacInput(raw: String): String =
        normalizeMac(raw).filter { it in HEX_DIGITS }.take(MAC_LENGTH)

    /** Só dígitos, no máximo 6. */
    fun sanitizeAlarmPasswordInput(raw: String): String =
        raw.filter(Char::isDigit).take(ALARM_PASSWORD_MAX_LENGTH)

    // ---- Validação (ao salvar) ----

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
