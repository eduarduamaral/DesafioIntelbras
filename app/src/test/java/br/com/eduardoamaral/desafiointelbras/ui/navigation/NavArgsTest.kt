package br.com.eduardoamaral.desafiointelbras.ui.navigation

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Os ViewModels leem o id do SavedStateHandle pela chave DEVICE_ID_ARG, e o Navigation
 * grava com o nome da propriedade da rota. Se alguém renomear a propriedade, este teste
 * falha aqui, e não em tempo de execução com a tela abrindo vazia.
 */
@OptIn(ExperimentalSerializationApi::class)
class NavArgsTest {

    private fun KSerializer<*>.propertyNames() =
        (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }

    @Test
    fun `rota do formulario usa a chave lida pelo ViewModel`() {
        assertEquals(listOf(DEVICE_ID_ARG), DeviceFormDestination.serializer().propertyNames())
    }

    @Test
    fun `rota de detalhes usa a chave lida pelo ViewModel`() {
        assertEquals(listOf(DEVICE_ID_ARG), DeviceDetailsDestination.serializer().propertyNames())
    }
}
