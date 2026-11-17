package com.r1control.app.data.remote

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Home Assistant's WebSocket protocol, read at the boundary: a text frame becomes an
// [HaMessage] or [HaMessage.Ignored], and nothing past this file sees JSON. A frame that
// does not parse is ignored rather than thrown, and is never printed: it can hold the
// household's entity names.

internal sealed interface HaMessage {
    data object AuthRequired : HaMessage
    data object AuthOk : HaMessage
    data object AuthInvalid : HaMessage
    data object Ignored : HaMessage

    /**
     * The reply to a command; [errorCode] is Home Assistant's short code, empty on success or when it gave none.
     * [payload] is the `result` object as JSON text, empty when the reply carried none. It can hold the
     * household's names: read it, never print it.
     */
    data class Result(val id: Int, val success: Boolean, val errorCode: String, val payload: String = "") : HaMessage

    /** An event on a `subscribe_entities` subscription. */
    data class Entities(val id: Int, val delta: EntityDelta) : HaMessage

    /**
     * An event on a `render_template` subscription: the template as it now renders. [rendered] can hold
     * household text: read it, never print it.
     */
    data class TemplateEvent(val id: Int, val rendered: String) : HaMessage
}

/**
 * What one `subscribe_entities` event says. The first event of a subscription is all [added]
 * (compressed state: `s` state, `a` attributes); later ones also carry [changed] (`c`, with
 * `+` for what is new and `-` for what is gone) and [removed] (`r`).
 */
internal data class EntityDelta(
    val added: Map<HaEntityId, HaEntity>,
    val changed: Map<HaEntityId, EntityChange>,
    val removed: Set<HaEntityId>,
)

internal data class EntityChange(
    val state: String?,
    val attributesSet: Map<String, HaAttribute>,
    val attributesRemoved: List<String>,
)

private val wireJson = Json { ignoreUnknownKeys = true }

internal fun parseHaMessage(text: String): HaMessage {
    val root = try {
        wireJson.parseToJsonElement(text) as? JsonObject
    } catch (_: Exception) {
        null
    } ?: return HaMessage.Ignored
    return when (root.text("type")) {
        "auth_required" -> HaMessage.AuthRequired
        "auth_ok" -> HaMessage.AuthOk
        "auth_invalid" -> HaMessage.AuthInvalid
        "result" -> {
            val id = root.int("id") ?: return HaMessage.Ignored
            val success = (root["success"] as? JsonPrimitive)?.booleanOrNull ?: false
            val code = (root["error"] as? JsonObject)?.text("code").orEmpty()
            HaMessage.Result(id, success, code, (root["result"] as? JsonObject)?.toString().orEmpty())
        }
        "event" -> {
            val id = root.int("id") ?: return HaMessage.Ignored
            val event = root["event"] as? JsonObject ?: return HaMessage.Ignored
            // A template's event carries `result`; an entities event carries `a`, `c` or `r` and never `result`.
            val rendered = event["result"]
            if (rendered != null) HaMessage.TemplateEvent(id, renderedText(rendered)) else HaMessage.Entities(id, deltaOf(event))
        }
        else -> HaMessage.Ignored
    }
}

/** Home Assistant parses a rendered number or list into JSON; the screen wants the text either way. */
private fun renderedText(element: JsonElement): String = when (element) {
    JsonNull -> ""
    is JsonPrimitive -> element.content
    else -> element.toString()
}

private fun deltaOf(event: JsonObject): EntityDelta {
    val added = (event["a"] as? JsonObject).orEmpty().mapNotNull { (id, body) ->
        val entity = body as? JsonObject ?: return@mapNotNull null
        val key = HaEntityId(id)
        key to HaEntity(key, entity.text("s").orEmpty(), attributesOf(entity["a"]))
    }.toMap()
    val changed = (event["c"] as? JsonObject).orEmpty().mapNotNull { (id, body) ->
        val parts = body as? JsonObject ?: return@mapNotNull null
        val set = parts["+"] as? JsonObject
        val gone = parts["-"] as? JsonObject
        val removedKeys = (gone?.get("a") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull } +
            (set?.get("a") as? JsonObject).orEmpty().filterValues { it === JsonNull }.keys
        HaEntityId(id) to EntityChange(set?.text("s"), attributesOf(set?.get("a")), removedKeys)
    }.toMap()
    val removed = (event["r"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(::HaEntityId) }.toSet()
    return EntityDelta(added, changed, removed)
}

/**
 * The entities after [delta], in Home Assistant's own order of application: additions, removals,
 * then changes. A change to an entity not known is dropped (Home Assistant warns of the same).
 * A null attribute is no attribute.
 */
internal fun Map<HaEntityId, HaEntity>.applying(delta: EntityDelta): Map<HaEntityId, HaEntity> {
    val next = toMutableMap()
    next.putAll(delta.added)
    delta.removed.forEach(next::remove)
    delta.changed.forEach { (id, change) ->
        val known = next[id] ?: return@forEach
        next[id] = known.copy(
            state = change.state ?: known.state,
            attributes = (known.attributes + change.attributesSet) - change.attributesRemoved.toSet(),
        )
    }
    return next
}

private fun attributesOf(element: JsonElement?): Map<String, HaAttribute> {
    val obj = element as? JsonObject ?: return emptyMap()
    return obj.mapNotNull { (key, value) -> attributeOf(value)?.let { key to it } }.toMap()
}

internal fun attributeOf(value: JsonElement): HaAttribute? = when (value) {
    JsonNull -> null
    is JsonPrimitive -> when {
        value.isString -> HaAttribute.Text(value.content)
        value.booleanOrNull != null -> HaAttribute.Flag(value.booleanOrNull == true)
        value.doubleOrNull != null -> HaAttribute.Number(value.doubleOrNull ?: 0.0)
        else -> null
    }
    else -> HaAttribute.Structured(value.toString())
}

internal fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.doubleOrNull?.toInt()

// Outgoing frames. The token appears only in [authFrame], which is only ever sent.

internal fun authFrame(token: String): String = buildJsonObject {
    put("type", "auth")
    put("access_token", token)
}.toString()

internal fun subscribeEntitiesFrame(id: Int): String = buildJsonObject {
    put("id", id)
    put("type", "subscribe_entities")
}.toString()

internal fun pingFrame(id: Int): String = buildJsonObject {
    put("id", id)
    put("type", "ping")
}.toString()

internal fun lovelaceConfigFrame(id: Int): String = buildJsonObject {
    put("id", id)
    put("type", "lovelace/config")
}.toString()

internal fun renderTemplateFrame(id: Int, template: String): String = buildJsonObject {
    put("id", id)
    put("type", "render_template")
    put("template", template)
}.toString()

/** Asks Home Assistant to start an HLS stream of [entityId]; the result carries the playlist path under `url`. */
internal fun cameraStreamFrame(id: Int, entityId: String): String = buildJsonObject {
    put("id", id)
    put("type", "camera/stream")
    put("entity_id", entityId)
    put("format", "hls")
}.toString()

/** The playlist path from a `camera/stream` result ([payload] is its `result` object), or empty when it carries none. */
internal fun cameraStreamUrlFrom(payload: String): String = try {
    Json.parseToJsonElement(payload).jsonObject["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
} catch (_: Exception) {
    ""
}

/** Ends the subscription [subscription], which was made by the command with that id. */
internal fun unsubscribeEventsFrame(id: Int, subscription: Int): String = buildJsonObject {
    put("id", id)
    put("type", "unsubscribe_events")
    put("subscription", subscription)
}.toString()

internal fun callServiceFrame(id: Int, domain: String, service: String, data: Map<String, HaAttribute>): String = buildJsonObject {
    put("id", id)
    put("type", "call_service")
    put("domain", domain)
    put("service", service)
    put("service_data", JsonObject(data.mapValues { (_, value) -> jsonOf(value) }))
}.toString()

private fun jsonOf(value: HaAttribute): JsonElement = when (value) {
    is HaAttribute.Text -> JsonPrimitive(value.value)
    // A whole number goes out as one: some services validate an integer strictly.
    is HaAttribute.Number -> if (value.value % 1.0 == 0.0 && kotlin.math.abs(value.value) < 1e15) JsonPrimitive(value.value.toLong()) else JsonPrimitive(value.value)
    is HaAttribute.Flag -> JsonPrimitive(value.value)
    is HaAttribute.Structured -> wireJson.parseToJsonElement(value.json)
}
