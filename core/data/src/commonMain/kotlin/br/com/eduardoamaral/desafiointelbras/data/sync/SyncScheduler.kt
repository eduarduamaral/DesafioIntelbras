package br.com.eduardoamaral.desafiointelbras.data.sync

/**
 * Pede o envio da fila de pendências. Cada plataforma decide como: no Android o :app agenda
 * um worker do WorkManager; nos testes é um fake. Interface para o repositório não conhecer nenhum.
 */
interface SyncScheduler {
    fun schedule()
}
