package com.r1control.app.domain

import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaEntity
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import com.r1control.app.domain.model.ha.TileId
import com.r1control.app.domain.model.ha.isLitBy
import com.r1control.app.domain.model.ha.isPressable
import com.r1control.app.domain.model.ha.isUnavailableWith
import com.r1control.app.domain.model.ha.renamed
import com.r1control.app.domain.model.ha.tapCall
import com.r1control.app.domain.model.ha.templateSummary
import com.r1control.app.domain.model.ha.titleFor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HaTemplateTileTest {
    private val next = HaServiceCall("pyscript", "pytube_next_song", mapOf("entity_id" to HaAttribute.Text("media_player.kitchen_audio")))
    private val button = HaTile.Template(TileId("t0"), "", "", HaEntityId.None, template = "Tiếp", call = next)
    private val text = HaTile.Template(TileId("t1"), "Today", "", HaEntityId("sensor.lunar"), template = "{{ x }}", call = HaServiceCall.None)

    @Test fun aTapRunsTheCardsServiceAndANoOpCallIsNoTap() {
        assertEquals(next, button.tapCall())
        assertTrue(button.isPressable)
        assertNull(text.tapCall())
        assertFalse(text.isPressable)
    }

    @Test fun theNoOpCallIsTheOnlyEmptyOne() {
        assertTrue(HaServiceCall.None.isNone)
        assertFalse(next.isNone)
        assertFalse(HaServiceCall("script", "", emptyMap()).isNone)
    }

    @Test fun aTemplateIsNeverLit() {
        assertFalse(button.isLitBy(HaEntity(HaEntityId("sensor.a"), "on", emptyMap())))
    }

    @Test fun aTemplateNamingNoEntityIsNeverUnavailableButOneNamingAnUnknownEntityIs() {
        assertFalse(button.isUnavailableWith(null))
        assertTrue(text.isUnavailableWith(null))
    }

    @Test fun theTitleIsTheNameElseTheTemplatesOwnFirstLine() {
        assertEquals("Today", text.titleFor(null))
        assertEquals("Tiếp", button.titleFor(null))
    }

    @Test fun renamingKeepsTheTemplateTheTapAndTheCondition() {
        val shown = button.copy(condition = HaCondition.StateIs(HaEntityId("media_player.k"), listOf("playing")))
        val renamed = shown.renamed("Next") as HaTile.Template
        assertEquals("Next", renamed.name)
        assertEquals(shown.copy(name = "Next"), renamed)
    }

    @Test fun theSummaryDropsTagsAndTakesTheFirstLineOfText() {
        assertEquals("Trộn: Bật", templateSummary("{% if state_attr('a', 'shuffle') %}\n  Trộn: Bật\n{% else %}\n  Trộn: Tắt\n{% endif %}\n"))
        assertEquals(
            "Ngày {{ now().day }} tháng {{ now().month }} ─ {{states.sensor.lunar_today.state}}",
            templateSummary("{% set days = ['a', 'b'] %}\n{% set d = days[0] %}\nNgày {{ now().day }} tháng {{ now().month }} ─  {{states.sensor.lunar_today.state}}"),
        )
        assertEquals("a b", templateSummary("{# note #}a {% if x %} b"))
    }

    @Test fun aTemplateOfOnlyTagsIsItsOwnSummary() {
        assertEquals("{% set a = 1 %}", templateSummary("  {% set a = 1 %}\n"))
    }
}
