package cc.modules

import kotlinx.serialization.Serializable
import one.wabbit.levenshtein.levenshtein

private fun <V> Collection<Key<V>>.findSimilarNames(name: Key<V>): List<Key<V>> =
    this.mapNotNull {
            val distance = levenshtein(it.value, name.value)
            if (distance >= 10) {
                null
            } else {
                it to distance
            }
        }
        .sortedBy { it.second }
        .take(5)
        .map { it.first }

interface Registry<Value> {
    fun keys(): Collection<Key<Value>>

    operator fun get(key: String): Value? = get(Key(key))

    operator fun get(key: Key<Value>): Value?

    operator fun contains(key: String): Boolean = contains(Key(key))

    operator fun contains(key: Key<Value>): Boolean = get(key) != null

    fun getOrThrow(key: String): Value = getOrThrow(Key(key))

    fun getOrThrow(key: Key<Value>): Value {
        val value = get(key)
        if (value == null) {
            val similarNames = keys().findSimilarNames(key)
            val similarNamesString = similarNames.joinToString(", ")
            throw IllegalArgumentException(
                "No value found for key $key. Did you mean one of these? $similarNamesString"
            )
        }
        return value
    }
}

@Serializable @JvmInline value class Key<Value>(val value: String)
