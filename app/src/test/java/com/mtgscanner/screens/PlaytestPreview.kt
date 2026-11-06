package com.mtgscanner.screens

import com.mtgscanner.ui.decks.playtest.PlayCard
import com.mtgscanner.ui.decks.playtest.PlayDeck
import com.mtgscanner.ui.decks.playtest.Piece
import com.mtgscanner.ui.decks.playtest.Playtest
import com.mtgscanner.ui.decks.playtest.SimplePilot
import com.mtgscanner.ui.decks.playtest.Table
import com.mtgscanner.ui.decks.playtest.Zone
import kotlin.random.Random

/** A game a few turns in, against a precon that plays itself, for the playtest screen picture. */
object PlaytestPreview {
    private val forest = PlayCard("Forest", typeLine = "Basic Land — Forest")
    private val swamp = PlayCard("Swamp", typeLine = "Basic Land — Swamp")
    private val elf = PlayCard("Llanowar Elves", typeLine = "Creature — Elf Druid", cmc = 1.0, power = "1", toughness = "1")
    private val beast = PlayCard("Ravenous Baloth", typeLine = "Creature — Beast", cmc = 4.0, power = "4", toughness = "4")
    private val ring = PlayCard("Sol Ring", typeLine = "Artifact", cmc = 1.0)
    private val gitrog = PlayCard("The Gitrog, Ravenous Ride", typeLine = "Legendary Creature — Frog Horror Mount", cmc = 5.0, power = "6", toughness = "5")
    private val zombie = PlayCard("Gravecrawler", typeLine = "Creature — Zombie", cmc = 1.0, power = "2", toughness = "1")

    private val you = PlayDeck("The Gitrog", List(40) { forest } + List(30) { swamp } + List(15) { elf } + List(13) { beast } + ring, listOf(gitrog))
    private val them = PlayDeck("Undead Unleashed", List(36) { swamp } + List(63) { zombie })

    /** The game just dealt, for the opening hand picture. */
    val opening: Table by lazy { Playtest.start(you, them, Random(4)) }

    val table: Table by lazy {
        var t = Playtest.start(you, them, Random(4))
        fun play(side: Int, pick: (Piece) -> Boolean) {
            val s = t.sides[side]
            val p = (s[Zone.HAND] + s[Zone.LIBRARY]).first(pick)
            t = Playtest.move(t, side, p.id, Zone.BATTLEFIELD)
        }
        repeat(3) { play(0) { it.card == forest } }
        play(0) { it.card == swamp }
        play(0) { it.card == elf }
        play(0) { it.card == beast }
        play(0) { it.card == ring }
        t = Playtest.untap(t, 0)
        val beastId = t.sides[0][Zone.BATTLEFIELD].first { it.card == beast }.id
        t = Playtest.counter(t, 0, beastId, Piece.PLUS, 2)
        t = Playtest.toggleTap(t, 0, t.sides[0][Zone.BATTLEFIELD].first { it.card == forest }.id)
        t = Playtest.token(t, 0, PlayCard("Treasure", typeLine = "Token Artifact — Treasure"), 2)
        repeat(3) { play(1) { it.card == swamp } }
        play(1) { it.card == zombie }
        t = Playtest.untap(t, 1)
        t = Playtest.nextTurn(t)
        t = Playtest.nextTurn(SimplePilot.takeTurn(t, 1))
        t
    }
}
