package com.r1control.app.domain.model.ha

/** A Home Assistant camera a card is drawn for: its entity and the name to show. */
data class HaCamera(val entity: HaEntityId, val name: String)
