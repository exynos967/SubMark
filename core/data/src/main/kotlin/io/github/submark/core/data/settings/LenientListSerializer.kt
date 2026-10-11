package io.github.submark.core.data.settings

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonArray

/** List that skips entries which no longer decode (e.g. a removed component) instead of failing the whole file. */
class LenientListSerializer<E>(private val element: KSerializer<E>) : KSerializer<List<E>> {
    private val delegate = ListSerializer(element)
    override val descriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: List<E>) = delegate.serialize(encoder, value)

    override fun deserialize(decoder: Decoder): List<E> {
        val input = decoder as? JsonDecoder ?: return delegate.deserialize(decoder)
        return input.decodeJsonElement().jsonArray.mapNotNull { item ->
            // SerializationException is an IllegalArgumentException.
            try {
                input.json.decodeFromJsonElement(element, item)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
