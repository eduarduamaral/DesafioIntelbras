package br.com.eduardoamaral.desafiointelbras.mockapi

import java.util.UUID

sealed interface StoreResult<out T> {
    data class Ok<T>(val value: T) : StoreResult<T>
    data object NotFound : StoreResult<Nothing>
    data object Conflict : StoreResult<Nothing>
}

/**
 * Um recurso da API em memória, na ordem de criação. Os métodos são sincronizados porque o
 * servidor atende várias requisições ao mesmo tempo (o app busca câmeras e alarmes em paralelo).
 */
class ResourceStore<T : Stored>(private val seed: () -> List<T>) {
    private val items = LinkedHashMap<String, T>()

    init {
        reset()
    }

    @Synchronized
    fun all(): List<T> = items.values.toList()

    @Synchronized
    fun get(id: String): T? = items[id]

    @Synchronized
    fun create(build: (id: String) -> T): StoreResult<T> {
        val item = build(UUID.randomUUID().toString())
        if (isTaken(item.uniqueKey, exceptId = null)) return StoreResult.Conflict
        items[item.id] = item
        return StoreResult.Ok(item)
    }

    @Synchronized
    fun update(id: String, build: (id: String) -> T): StoreResult<T> {
        if (id !in items) return StoreResult.NotFound
        val item = build(id)
        if (isTaken(item.uniqueKey, exceptId = id)) return StoreResult.Conflict
        items[id] = item
        return StoreResult.Ok(item)
    }

    @Synchronized
    fun delete(id: String): Boolean = items.remove(id) != null

    @Synchronized
    fun reset() {
        items.clear()
        seed().forEach { items[it.id] = it }
    }

    private fun isTaken(key: String, exceptId: String?) =
        items.values.any { it.id != exceptId && it.uniqueKey.equals(key, ignoreCase = true) }
}
