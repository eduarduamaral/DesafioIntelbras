package br.com.eduardoamaral.desafiointelbras.domain.network

import kotlinx.coroutines.flow.Flow

/**
 * Se o aparelho tem internet agora. Fica no domínio porque quem depende dela é a UI
 * (o aviso de "sem conexão"); a implementação, que usa o Android, fica na camada de dados.
 */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
