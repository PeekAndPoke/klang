package io.peekandpoke.cache

class InMemoryUrlCache(
    private val delegate: UrlCache,
) : UrlCache {
    private val cache = mutableMapOf<String, ByteArray?>()

    override suspend fun has(uri: String): Boolean {
        return cache.containsKey(uri) || delegate.has(uri)
    }

    override suspend fun get(uri: String): ByteArray? {
        return cache[uri] ?: delegate.get(uri)
    }

    override suspend fun getOrPut(uri: String, loader: suspend () -> ByteArray?): ByteArray? {
        return cache.getOrPut(uri) {
            delegate.getOrPut(uri, loader)
        }
    }
}
