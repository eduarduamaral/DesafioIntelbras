package br.com.eduardoamaral.desafiointelbras.mockapi

import java.util.UUID

sealed interface StoreResult<out T> {
    data class Ok<T>(val value: T) : StoreResult<T>
    data object NotFound : StoreResult<Nothing>
    data object Conflict : StoreResult<Nothing>
}

/**
 * Um recurso da API em memória, na ordem de criação. Sincronizado porque o servidor atende
 * várias requisições ao mesmo tempo (o app busca câmeras e alarmes em paralelo).
 *
 * Começa com [initial] (o que estava salvo) ou, sem nada salvo, com [seed]. [onChange] é
 * chamado depois de cada mudança, fora da trava, para quem guarda os dados em arquivo.
 */
class ResourceStore<T : Stored>(
    private val seed: () -> List<T>,
    initial: List<T>? = null,
    private val onChange: () -> Unit = {},
) {
    private val lock = Any()
    private val items = LinkedHashMap<String, T>()

    init {
        (initial ?: seed()).forEach { items[it.id] = it }
    }

    fun all(): List<T> = synchronized(lock) { items.values.toList() }

    fun get(id: String): T? = synchronized(lock) { items[id] }

    fun create(build: (id: String) -> T): StoreResult<T> = changing {
        val item = build(UUID.randomUUID().toString())
        if (isTaken(item.uniqueKey, exceptId = null)) return@changing StoreResult.Conflict
        items[item.id] = item
        StoreResult.Ok(item)
    }

    fun update(id: String, build: (id: String) -> T): StoreResult<T> = changing {
        if (id !in items) return@changing StoreResult.NotFound
        val item = build(id)
        if (isTaken(item.uniqueKey, exceptId = id)) return@changing StoreResult.Conflict
        items[id] = item
        StoreResult.Ok(item)
    }

    fun delete(id: String): Boolean = changing { items.remove(id) != null }

    fun reset() = changing {
        items.clear()
        seed().forEach { items[it.id] = it }
    }

    private fun <R> changing(block: () -> R): R {
        val result = synchronized(lock, block)
        onChange()
        return result
    }

    private fun isTaken(key: String, exceptId: String?) =
        items.values.any { it.id != exceptId && it.uniqueKey.equals(key, ignoreCase = true) }
}
