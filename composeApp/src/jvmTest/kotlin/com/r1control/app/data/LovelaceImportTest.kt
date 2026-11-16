package com.r1control.app.data

import com.r1control.app.data.remote.importDashboard
import com.r1control.app.data.remote.importLovelace
import com.r1control.app.domain.model.ha.HaAttribute
import com.r1control.app.domain.model.ha.HaCondition
import com.r1control.app.domain.model.ha.HaDashboard
import com.r1control.app.domain.model.ha.HaEntityId
import com.r1control.app.domain.model.ha.HaServiceCall
import com.r1control.app.domain.model.ha.HaTile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The household's real Lovelace configuration (`ha_lovelace_config.json`, read from its Home Assistant),
 * and small hand-written cards for the shapes that file does not hold.
 */
class LovelaceImportTest {
    private val real = importLovelace(fixture("ha_lovelace_config.json"))

    private fun tiles(): List<HaTile> = real.dashboard.sections.flatMap { it.tiles }

    private fun tile(entity: String, name: String? = null): HaTile =
        tiles().first { it.entity == HaEntityId(entity) && (name == null || it.name == name) }

    private fun of(vararg cards: String, view: String = "V"): HaDashboard =
        importDashboard("""{"views":[{"title":"$view","cards":[${cards.joinToString(",")}]}]}""")

    @Test fun theRealConfigurationBecomesThirtySixSectionsAndAHundredEightyFiveTiles() {
        assertEquals(36, real.dashboard.sections.size)
        assertEquals(185, real.dashboard.tileCount)
        assertEquals(listOf("Thiết Bị", "Nhiệt độ", "Quạt của mẹ"), real.dashboard.sections.take(3).map { it.title })
        assertEquals("s0", real.dashboard.sections.first().id.value)
        assertEquals("s35", real.dashboard.sections.last().id.value)
    }

    @Test fun everyTileAndSectionIdIsUnique() {
        assertEquals(tiles().size, tiles().map { it.id }.toSet().size)
        assertEquals(real.dashboard.sections.size, real.dashboard.sections.map { it.id }.toSet().size)
    }

    @Test fun theDaikinOffChipRunsItsScript() {
        val off = assertIs<HaTile.Action>(tile("script.daikin_turn_off", "Tắt"))
        assertEquals(HaServiceCall("script", "daikin_turn_off", emptyMap()), off.call)
        assertEquals("script.daikin_turn_off", off.entity.value)
    }

    @Test fun aSwitchCardIsAToggleWithItsSeededNameAndIcon() {
        val light = assertIs<HaTile.Toggle>(tile("switch.liv_light_1"))
        assertEquals("Đèn trong", light.name)
        assertEquals("mdi:lightbulb", light.icon)
    }

    @Test fun aLightCardIsAToggle() {
        assertIs<HaTile.Toggle>(tile("light.luom"))
    }

    @Test fun aSensorCardIsAReadout() {
        val outside = assertIs<HaTile.Readout>(tile("sensor.bal_temperature"))
        assertEquals("Ngoài", outside.name)
    }

    @Test fun aMediaControlIsAPlayer() {
        assertIs<HaTile.Media>(tile("media_player.kitchen_audio"))
        assertEquals(5, tiles().count { it is HaTile.Media })
    }

    @Test fun anEntitiesRowThatIsABareScriptIdRunsItsScript() {
        val heater = assertIs<HaTile.Action>(tile("script.electric_heater_pwd_on_off"))
        assertEquals("script", heater.call.domain)
        assertEquals("electric_heater_pwd_on_off", heater.call.service)
    }

    @Test fun anEntitiesRowThatIsAButtonPressesIt() {
        val press = assertIs<HaTile.Action>(tile("button.xiaomi_sg_2090490852_p76_toggle_a_2_3"))
        assertEquals(HaServiceCall("button", "press", mapOf("entity_id" to HaAttribute.Text("button.xiaomi_sg_2090490852_p76_toggle_a_2_3"))), press.call)
    }

    @Test fun anEntitiesRowKeepsItsNameWithoutItsStrayBlanks() {
        assertEquals("Alarm Alarm", tile("switch.xiaomi_sg_2090490852_p76_alarm_p_7_1").name)
    }

    @Test fun conditionalAndTemplateCardsAreNoLongerSkipped() {
        assertEquals(null, real.skipped["conditional"])
        assertEquals(null, real.skipped["custom:mushroom-template-card"])
        assertEquals(11, real.skipped["custom:mushroom-cover-card"], "the household's cover cards name no entity")
        assertTrue(tiles().none { it is HaTile.Cover })
    }

    private val playing = HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("playing"))

    private fun conditional(conditions: String, inner: String) = """{"type":"conditional","conditions":[$conditions],"card":$inner}"""

    private val lamp = """{"type":"custom:mushroom-entity-card","entity":"light.a"}"""

    private val kitchenPlaying = """{"condition":"state","entity":"media_player.kitchen","state":"playing"}"""

    @Test fun aConditionalCardGivesItsInnerTileItsCondition() {
        val tile = of(conditional(kitchenPlaying, lamp)).sections.single().tiles.single()
        assertEquals("light.a", tile.entity.value)
        assertEquals(playing, tile.condition)
    }

    @Test fun theShortFormAndAListOfStatesAreStateConditions() {
        val short = of(conditional("""{"entity":"media_player.kitchen","state":"playing"}""", lamp)).sections.single().tiles.single()
        assertEquals(playing, short.condition)
        val list = of(conditional("""{"condition":"state","entity":"media_player.kitchen","state":["playing","paused"]}""", lamp)).sections.single().tiles.single()
        assertEquals(HaCondition.StateIs(HaEntityId("media_player.kitchen"), listOf("playing", "paused")), list.condition)
    }

    @Test fun stateNotIsAStateIsNot() {
        val tile = of(conditional("""{"condition":"state","entity":"media_player.kitchen","state_not":"off"}""", lamp)).sections.single().tiles.single()
        assertEquals(HaCondition.StateIsNot(HaEntityId("media_player.kitchen"), listOf("off")), tile.condition)
    }

    @Test fun aNumericStateIsAboveBelowOrBothTogether() {
        fun numeric(limits: String) = of(conditional("""{"condition":"numeric_state","entity":"sensor.t"$limits}""", lamp)).sections.single().tiles.single().condition
        val sensor = HaEntityId("sensor.t")
        assertEquals(HaCondition.Above(sensor, 20.0), numeric(""","above":20"""))
        assertEquals(HaCondition.Below(sensor, 5.5), numeric(""","below":5.5"""))
        assertEquals(HaCondition.All(listOf(HaCondition.Above(sensor, 10.0), HaCondition.Below(sensor, 20.0))), numeric(""","above":10,"below":20"""))
    }

    @Test fun severalConditionsAreAllRequiredAndNestedConditionalsAddUp() {
        val sensor = HaEntityId("sensor.t")
        val two = of(conditional("""$kitchenPlaying,{"condition":"numeric_state","entity":"sensor.t","above":1}""", lamp)).sections.single().tiles.single()
        assertEquals(HaCondition.All(listOf(playing, HaCondition.Above(sensor, 1.0))), two.condition)
        val nested = of(conditional(kitchenPlaying, conditional("""{"condition":"numeric_state","entity":"sensor.t","above":1}""", lamp))).sections.single().tiles.single()
        assertEquals(two.condition, nested.condition)
    }

    @Test fun anAndConditionIsAllOfItsParts() {
        val tile = of(conditional("""{"condition":"and","conditions":[$kitchenPlaying]}""", lamp)).sections.single().tiles.single()
        assertEquals(playing, tile.condition)
    }

    @Test fun everyTileInAStackUnderAConditionalIsConditionalAndTheNextOneIsNot() {
        val tiles = of(
            conditional(kitchenPlaying, """{"type":"vertical-stack","cards":[$lamp,{"type":"custom:mushroom-entity-card","entity":"light.b"}]}"""),
            """{"type":"custom:mushroom-entity-card","entity":"light.c"}""",
        ).sections.single().tiles
        assertEquals(listOf(playing, playing, HaCondition.Always), tiles.map { it.condition })
    }

    @Test fun aConditionThatCannotBeRepresentedSkipsTheCardAndIsCounted() {
        val unrepresentable = listOf(
            """{"condition":"screen","media_query":"(min-width: 600px)"}""",
            """{"condition":"user","users":["abc"]}""",
            """{"condition":"or","conditions":[$kitchenPlaying]}""",
            """{"condition":"state","entity":"media_player.kitchen","attribute":"shuffle","state":"true"}""",
            """{"condition":"numeric_state","entity":"sensor.t","above":"sensor.limit"}""",
            """{"condition":"numeric_state","entity":"sensor.t"}""",
            """{"condition":"state","state":"on"}""",
        )
        unrepresentable.forEach { condition ->
            val imported = importLovelace("""{"views":[{"title":"V","cards":[${conditional(condition, lamp)}]}]}""")
            assertEquals(0, imported.dashboard.tileCount, condition)
            assertEquals(1, imported.skipped["conditional"], condition)
        }
        val mixed = importLovelace("""{"views":[{"title":"V","cards":[${conditional("""$kitchenPlaying,{"condition":"screen"}""", lamp)}]}]}""")
        assertEquals(0, mixed.dashboard.tileCount, "one part that cannot be represented skips the card")
    }

    @Test fun aConditionalWithNoCardIsSkippedAndOneWithNoConditionsIsDrawn() {
        val none = importLovelace("""{"views":[{"title":"V","cards":[{"type":"conditional","conditions":[$kitchenPlaying]}]}]}""")
        assertEquals(1, none.skipped["conditional"])
        val always = of(conditional("", lamp)).sections.single().tiles.single()
        assertEquals(HaCondition.Always, always.condition)
    }

    private fun templates() = tiles().filterIsInstance<HaTile.Template>()

    @Test fun theRealConfigurationHoldsTwentyOneTemplateTilesTwentyOfThemConditional() {
        assertEquals(21, templates().size)
        assertEquals(20, templates().count { it.condition != HaCondition.Always })
    }

    @Test fun theDatePrimaryIsKeptVerbatimWithItsEntityAndNoTap() {
        val date = templates().single { it.entity.value == "sensor.lunar_today" }
        assertEquals(HaCondition.Always, date.condition)
        assertTrue(date.template.startsWith("{% set days = ['Chủ Nhật'"))
        assertTrue(date.template.endsWith("{{states.sensor.lunar_today.state}}"))
        assertEquals(HaServiceCall.None, date.call)
        assertEquals("", date.icon)
    }

    @Test fun theKitchenNextTileIsConditionalOnThePlayerPlayingAndCallsThePyscriptService() {
        val next = templates().first { it.template == "Tiếp" && it.call.data["entity_id"] == HaAttribute.Text("media_player.kitchen_audio") }
        assertEquals(HaCondition.StateIs(HaEntityId("media_player.kitchen_audio_pytube"), listOf("playing")), next.condition)
        assertEquals(HaServiceCall("pyscript", "pytube_next_song", mapOf("entity_id" to HaAttribute.Text("media_player.kitchen_audio"))), next.call)
        assertEquals(HaEntityId.None, next.entity)
    }

    @Test fun thePausedAndBufferingTilesAreConditionalOnTheirOwnState() {
        val play = templates().first { it.template == "Phát" && it.call.data["entity_id"] == HaAttribute.Text("media_player.r1") }
        assertEquals(HaCondition.StateIs(HaEntityId("media_player.r1_pytube"), listOf("paused")), play.condition)
        assertEquals("pytube_resume", play.call.service)
        val loading = templates().first { it.template.startsWith("Đang tải") && it.condition == HaCondition.StateIs(HaEntityId("media_player.r1_pytube"), listOf("buffering")) }
        assertEquals(HaServiceCall.None, loading.call)
    }

    @Test fun aTemplateCardMapsItsPrimaryEntityIconAndTap() {
        val tile = of(
            """{"type":"custom:mushroom-template-card","primary":"{{ states('sensor.a') }}","entity":"sensor.a","icon":"mdi:thermometer","tap_action":{"action":"toggle"}}""",
        ).sections.single().tiles.single()
        val template = assertIs<HaTile.Template>(tile)
        assertEquals("{{ states('sensor.a') }}", template.template)
        assertEquals("sensor.a", template.entity.value)
        assertEquals("mdi:thermometer", template.icon)
        assertEquals(HaServiceCall("sensor", "toggle", mapOf("entity_id" to HaAttribute.Text("sensor.a"))), template.call)
    }

    @Test fun aTemplateCardWithATemplateIconOrNoTapKeepsNoIconAndDoesNothingOnTap() {
        val tile = assertIs<HaTile.Template>(
            of("""{"type":"custom:mushroom-template-card","primary":"x","icon":"{% if a %}mdi:a{% endif %}"}""").sections.single().tiles.single(),
        )
        assertEquals("", tile.icon)
        assertEquals(HaServiceCall.None, tile.call)
    }

    @Test fun aTemplateCardWithNoTextIsSkippedAndCounted() {
        val imported = importLovelace("""{"views":[{"title":"V","cards":[{"type":"custom:mushroom-template-card","entity":"light.a"}]}]}""")
        assertEquals(0, imported.dashboard.tileCount)
        assertEquals(1, imported.skipped["custom:mushroom-template-card"])
    }

    @Test fun aTemplateChipIsStillSkippedAndCounted() {
        val imported = importLovelace("""{"views":[{"title":"V","cards":[{"type":"custom:mushroom-chips-card","chips":[{"type":"template","content":"x"}]}]}]}""")
        assertEquals(0, imported.dashboard.tileCount)
        assertEquals(1, imported.skipped["chip:template"])
    }

    @Test fun theCameraViewsPictureCardsAreSkippedToo() {
        assertEquals(2, real.skipped["picture-entity"])
    }

    @Test fun aViewOpensASectionOnlyWhenSomethingLandsInIt() {
        val dashboard = importDashboard(
            """{"views":[{"title":"Empty","cards":[]},{"title":"Lamps","cards":[{"type":"custom:mushroom-entity-card","entity":"light.a"}]}]}""",
        )
        assertEquals(listOf("Lamps"), dashboard.sections.map { it.title })
    }

    @Test fun aTitleOrHeadingStartsANewSectionAndABlankOneDoesNot() {
        val dashboard = of(
            """{"type":"custom:mushroom-entity-card","entity":"light.a"}""",
            """{"type":"custom:mushroom-title-card","title":"Second"}""",
            """{"type":"custom:mushroom-entity-card","entity":"light.b"}""",
            """{"type":"heading","heading":"Third"}""",
            """{"type":"custom:mushroom-title-card"}""",
            """{"type":"custom:mushroom-entity-card","entity":"light.c"}""",
        )
        assertEquals(listOf("V", "Second", "Third"), dashboard.sections.map { it.title })
        assertEquals(listOf("light.a"), dashboard.sections[0].tiles.map { it.entity.value })
        assertEquals(listOf("light.c"), dashboard.sections[2].tiles.map { it.entity.value })
    }

    @Test fun stacksAndGridsAreWalkedInto() {
        val dashboard = of(
            """{"type":"vertical-stack","cards":[{"type":"horizontal-stack","cards":[{"type":"grid","cards":[{"type":"custom:mushroom-entity-card","entity":"light.deep"}]}]}]}""",
        )
        assertEquals(listOf("light.deep"), dashboard.sections.single().tiles.map { it.entity.value })
    }

    @Test fun aSectionsViewIsWalkedThroughItsSections() {
        val dashboard = importDashboard(
            """{"views":[{"title":"Cams","type":"sections","sections":[{"type":"grid","cards":[{"type":"custom:mushroom-entity-card","entity":"light.a"}]}]}]}""",
        )
        assertEquals(1, dashboard.tileCount)
    }

    @Test fun anExplicitToggleIsAToggleWhateverTheDomain() {
        val tile = of("""{"type":"custom:mushroom-entity-card","entity":"cover.garage","tap_action":{"action":"toggle"}}""").sections.single().tiles.single()
        assertIs<HaTile.Toggle>(tile)
    }

    @Test fun aNoneTapMakesWhatWouldBePressedAReadoutButKeepsACoverAndAPlayer() {
        val none = ""","tap_action":{"action":"none"}}"""
        val tiles = of(
            """{"type":"custom:mushroom-entity-card","entity":"light.a"$none""",
            """{"type":"custom:mushroom-entity-card","entity":"script.b"$none""",
            """{"type":"custom:mushroom-entity-card","entity":"cover.c"$none""",
            """{"type":"custom:mushroom-entity-card","entity":"media_player.d"$none""",
        ).sections.single().tiles
        assertEquals(listOf("Readout", "Readout", "Cover", "Media"), tiles.map { it::class.simpleName })
    }

    @Test fun aServiceCardMergesItsTargetAndItsData() {
        val tile = of(
            """{"type":"custom:mushroom-chips-card","chips":[{"type":"entity","entity":"script.go","name":"Go","tap_action":{"action":"perform-action","perform_action":"pyscript.pytube_next_song","target":{"entity_id":"media_player.r1"},"data":{"volume":3,"loud":true,"tags":["a"]}}}]}""",
        ).sections.single().tiles.single()
        val call = assertIs<HaTile.Action>(tile).call
        assertEquals("pyscript", call.domain)
        assertEquals("pytube_next_song", call.service)
        assertEquals(
            mapOf(
                "entity_id" to HaAttribute.Text("media_player.r1"),
                "volume" to HaAttribute.Number(3.0),
                "loud" to HaAttribute.Flag(true),
                "tags" to HaAttribute.Structured("""["a"]"""),
            ),
            call.data,
        )
    }

    @Test fun aServiceNamedNothingFallsBackToTheDomainDefault() {
        val tile = of(
            """{"type":"custom:mushroom-entity-card","entity":"script.go","tap_action":{"action":"call-service","service":""}}""",
        ).sections.single().tiles.single()
        assertEquals(HaServiceCall("script", "go", emptyMap()), assertIs<HaTile.Action>(tile).call)
    }

    @Test fun aChipThatIsOnlyAnEntityIsAToggleOrAReadout() {
        val tiles = of(
            """{"type":"custom:mushroom-chips-card","chips":[{"type":"entity","entity":"light.a"},{"type":"entity","entity":"input_text.b"},{"type":"template","content":"x"},{"type":"back"}]}""",
        )
        assertEquals(listOf("Toggle", "Readout"), tiles.sections.single().tiles.map { it::class.simpleName })
    }

    @Test fun aSceneTurnsOn() {
        val tile = of("""{"type":"entities","entities":["scene.movie"]}""").sections.single().tiles.single()
        assertEquals(HaServiceCall("scene", "turn_on", mapOf("entity_id" to HaAttribute.Text("scene.movie"))), assertIs<HaTile.Action>(tile).call)
    }

    @Test fun anEntitiesCardsTitleOpensASection() {
        val dashboard = of("""{"type":"entities","title":"Presence","entities":["sensor.a"]}""")
        assertEquals(listOf("Presence"), dashboard.sections.map { it.title })
    }

    @Test fun somethingThatIsNotAConfigurationIsRefusedWithoutEchoingIt() {
        listOf("", "not json", "[1,2]").forEach { text ->
            val failure = assertFailsWith<IllegalArgumentException> { importDashboard(text) }
            assertTrue(text.isEmpty() || text !in failure.message.orEmpty())
        }
    }

    @Test fun aConfigurationWithNoViewsIsEmpty() {
        assertEquals(HaDashboard.Empty, importDashboard("{}"))
    }
}
