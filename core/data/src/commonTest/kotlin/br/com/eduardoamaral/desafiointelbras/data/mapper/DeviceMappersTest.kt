package br.com.eduardoamaral.desafiointelbras.data.mapper

import br.com.eduardoamaral.desafiointelbras.data.local.DeviceEntity
import br.com.eduardoamaral.desafiointelbras.data.local.DeviceWithFavorite
import br.com.eduardoamaral.desafiointelbras.data.local.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceMappersTest {

    private fun row(syncState: SyncState, serial: String? = "ABC123") = DeviceWithFavorite(
        device = DeviceEntity(
            id = "v1",
            type = "VIDEO",
            name = "Câmera",
            password = "1234",
            serial = serial,
            username = "admin",
            syncState = syncState.name,
        ),
        isFavorite = true,
    )

    @Test
    fun `item sincronizado nao aparece como aguardando envio`() {
        val device = row(SyncState.SYNCED).toDomain()!!

        assertFalse(device.isPendingSync)
        assertTrue(device.isFavorite)
    }

    @Test
    fun `criacao e edicao pendentes aparecem como aguardando envio`() {
        assertTrue(row(SyncState.PENDING_CREATE).toDomain()!!.isPendingSync)
        assertTrue(row(SyncState.PENDING_UPDATE).toDomain()!!.isPendingSync)
    }

    @Test
    fun `linha inconsistente e descartada`() {
        assertNull(row(SyncState.SYNCED, serial = null).toDomain())
        assertEquals("Câmera", row(SyncState.SYNCED).toDomain()!!.name)
    }
}
