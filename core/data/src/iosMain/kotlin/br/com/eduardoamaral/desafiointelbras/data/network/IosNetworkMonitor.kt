@file:OptIn(ExperimentalForeignApi::class)

package br.com.eduardoamaral.desafiointelbras.data.network

import br.com.eduardoamaral.desafiointelbras.domain.network.NetworkMonitor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * Observa a rede com o NWPathMonitor (framework Network), o equivalente iOS do
 * ConnectivityManager. "satisfied" é o caminho com rota para a internet; o iOS não expõe
 * o "VALIDATED" do Android, então um Wi-Fi sem internet só aparece como falha de rede na
 * hora da requisição, que a fila já trata como temporária.
 */
class IosNetworkMonitor : NetworkMonitor {

    override val isOnline: Flow<Boolean> = callbackFlow {
        val monitor = nw_path_monitor_create()
        nw_path_monitor_set_update_handler(monitor) { path ->
            trySend(nw_path_get_status(path) == nw_path_status_satisfied)
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("br.com.eduardoamaral.desafiointelbras.network", null))
        // O handler é chamado logo ao iniciar, com o estado atual.
        nw_path_monitor_start(monitor)
        awaitClose { nw_path_monitor_cancel(monitor) }
    }
        .distinctUntilChanged()
        .conflate()
}
