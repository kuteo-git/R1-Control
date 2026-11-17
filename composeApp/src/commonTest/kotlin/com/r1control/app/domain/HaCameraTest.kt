package com.r1control.app.domain

import com.r1control.app.data.remote.cameraStreamUrlFrom
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.camerasFrom
import kotlin.test.Test
import kotlin.test.assertEquals

class HaCameraTest {
    @Test fun theStreamUrlIsTheUrlFieldOfTheResult() {
        assertEquals("/api/hls/abc/master.m3u8", cameraStreamUrlFrom("""{"url":"/api/hls/abc/master.m3u8"}"""))
    }

    @Test fun aResultWithoutAUrlOrNotJsonIsEmpty() {
        assertEquals("", cameraStreamUrlFrom("""{"other":1}"""))
        assertEquals("", cameraStreamUrlFrom("not json"))
        assertEquals("", cameraStreamUrlFrom(""))
    }

    @Test fun onlyStreamingCamerasAreOffered_sortedByName() {
        fun cam(id: String, name: String, features: Int) = HaEntity(
            HaEntityId(id), "idle",
            mapOf("friendly_name" to HaAttribute.Text(name), "supported_features" to HaAttribute.Number(features.toDouble())),
        )
        val entities = listOf(
            cam("camera.gate", "Gate", 2),
            cam("camera.kitchen", "Bếp", 2),
            cam("camera.old", "No stream", 0), // cannot stream: left out
            HaEntity(HaEntityId("light.hall"), "on", emptyMap()), // not a camera
        ).associateBy { it.id }
        val cameras = camerasFrom(entities)
        assertEquals(listOf("Bếp", "Gate"), cameras.map { it.name })
        assertEquals(listOf("camera.kitchen", "camera.gate"), cameras.map { it.entity.value })
    }
}
