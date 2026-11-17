package com.r1control.app.domain.model.ha

/** A Home Assistant camera that can stream: its entity and the name to show. */
data class HaCamera(val entity: HaEntityId, val name: String)

/** CameraEntityFeature.STREAM, the bit a camera sets in `supported_features` when it can give a live stream. */
private const val CAMERA_STREAM = 2

private val HaEntity.canStream: Boolean
    get() = (((attributes["supported_features"] as? HaAttribute.Number)?.value?.toInt() ?: 0) and CAMERA_STREAM) != 0

/** Every camera that can stream, by name. A camera that cannot stream is left out: the app only ever plays a stream. */
fun camerasFrom(entities: Map<HaEntityId, HaEntity>): List<HaCamera> =
    entities.values
        .filter { it.id.domainName == "camera" && it.canStream }
        .sortedBy { it.friendlyName.lowercase() }
        .map { HaCamera(it.id, it.friendlyName) }
