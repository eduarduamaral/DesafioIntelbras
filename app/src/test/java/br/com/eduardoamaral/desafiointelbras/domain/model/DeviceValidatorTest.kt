package br.com.eduardoamaral.desafiointelbras.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceValidatorTest {

    @Test
    fun `video valido nao tem erros`() {
        val input = DeviceInput.Video("Câmera", "ABC123", "admin", "senha")
        assertTrue(DeviceValidator.validate(input).isEmpty())
    }

    @Test
    fun `video vazio aponta todos os campos obrigatorios`() {
        val errors = DeviceValidator.validate(DeviceInput.Video("", " ", "", ""))
        assertEquals(
            mapOf(
                DeviceField.NAME to ValidationError.REQUIRED,
                DeviceField.SERIAL to ValidationError.REQUIRED,
                DeviceField.USERNAME to ValidationError.REQUIRED,
                DeviceField.PASSWORD to ValidationError.REQUIRED,
            ),
            errors,
        )
    }

    @Test
    fun `alarme valido com senha de 4 e de 6 digitos`() {
        assertTrue(DeviceValidator.validate(DeviceInput.Alarm("Casa", "1A2B3C4D5E6F", "1234")).isEmpty())
        assertTrue(DeviceValidator.validate(DeviceInput.Alarm("Casa", "1A2B3C4D5E6F", "123456")).isEmpty())
    }

    @Test
    fun `senha de alarme precisa ter 4 ou 6 digitos numericos`() {
        listOf("123", "12345", "1234567", "12a4").forEach { password ->
            assertEquals(
                password,
                ValidationError.INVALID_ALARM_PASSWORD,
                DeviceValidator.validateAlarmPassword(password),
            )
        }
    }

    @Test
    fun `MAC aceita separadores e minusculas`() {
        assertNull(DeviceValidator.validateMac("1a:2b:3c:4d:5e:6f"))
        assertNull(DeviceValidator.validateMac("1A-2B-3C-4D-5E-6F"))
        assertEquals("1A2B3C4D5E6F", DeviceValidator.normalizeMac("1a:2b:3c-4d.5e 6f"))
    }

    @Test
    fun `MAC invalido`() {
        assertEquals(ValidationError.INVALID_MAC, DeviceValidator.validateMac("1A2B3C"))
        assertEquals(ValidationError.INVALID_MAC, DeviceValidator.validateMac("1A2B3C4D5E6G"))
        assertEquals(ValidationError.REQUIRED, DeviceValidator.validateMac(""))
    }

    @Test
    fun `serial aceita ate 13 caracteres`() {
        assertNull(DeviceValidator.validateSerial("ABCDEF1234567"))
        assertEquals(ValidationError.TOO_LONG, DeviceValidator.validateSerial("ABCDEF12345678"))
        assertEquals(ValidationError.REQUIRED, DeviceValidator.validateSerial("  "))
    }

    @Test
    fun `entrada do serial e cortada em 13`() {
        assertEquals("ABCDEF1234567", DeviceValidator.sanitizeSerialInput("ABCDEF1234567890"))
        assertEquals("ABC", DeviceValidator.sanitizeSerialInput("ABC"))
    }

    @Test
    fun `entrada do MAC aceita so hexadecimal maiusculo e no maximo 12`() {
        assertEquals("1A2B3C4D5E6F", DeviceValidator.sanitizeMacInput("1a:2b:3c:4d:5e:6f:99"))
        assertEquals("ABCDEF", DeviceValidator.sanitizeMacInput("ab-cd-ef-gh"))
        assertEquals("", DeviceValidator.sanitizeMacInput("xyz"))
    }

    @Test
    fun `entrada da senha do alarme aceita so digitos e no maximo 6`() {
        assertEquals("123456", DeviceValidator.sanitizeAlarmPasswordInput("12a34b5678"))
        assertEquals("", DeviceValidator.sanitizeAlarmPasswordInput("abc"))
    }

    @Test
    fun `entrada saneada sempre passa na validacao de formato quando completa`() {
        val mac = DeviceValidator.sanitizeMacInput("aa:bb:cc:dd:ee:ff")
        assertNull(DeviceValidator.validateMac(mac))
        val password = DeviceValidator.sanitizeAlarmPasswordInput("1-2-3-4")
        assertNull(DeviceValidator.validateAlarmPassword(password))
    }

    @Test
    fun `nome acima do limite`() {
        val name = "a".repeat(DeviceValidator.NAME_MAX_LENGTH + 1)
        assertEquals(ValidationError.TOO_LONG, DeviceValidator.validateName(name))
    }
}
