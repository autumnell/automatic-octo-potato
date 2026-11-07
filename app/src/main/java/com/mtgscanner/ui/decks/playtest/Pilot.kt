package com.mtgscanner.ui.decks.playtest

/**
 * Who plays the opponent's side during its turn. [takeTurn] gets the table after the opponent has untapped and drawn,
 * and returns it at the end of their turn (before play passes back). A smarter opponent is another [Pilot].
 */
interface Pilot {
    val label: String
    fun takeTurn(t: Table, side: Int): Table
}

/** You play the opponent's cards yourself: its turn does nothing on its own. */
object HandPilot : Pilot {
    override val label = "You play it"
    override fun takeTurn(t: Table, side: Int): Table = t
}

/**
 * A plain opponent: plays a land, casts the most expensive spells its lands can pay for (colors aren't checked),
 * then attacks with every creature that can, straight at you. Blocks are yours to make with Undo and life.
 */
object SimplePilot : Pilot {
    override val label = "Plays itself"

    /** Mana for a commander: its mana value plus 2 for each earlier cast. */
    private fun cost(p: Piece, s: Side, fromCommand: Boolean): Int =
        p.card.cmc.toInt() + if (fromCommand) 2 * s.commanderCasts else 0

    override fun takeTurn(t: Table, side: Int): Table {
        var table = t
        val said = mutableListOf<String>()
        fun me() = table.sides[side]

        // 1. A land, if there's one in hand.
        me()[Zone.HAND].firstOrNull { it.card.isLand }?.let { land ->
            table = Playtest.move(table, side, land.id, Zone.BATTLEFIELD)
            table = table.copy(sides = table.sides.mapIndexed { i, s -> if (i == side) s.copy(landPlayed = true) else s })
            said += "played ${land.card.name}"
        }

        // 2. Spells, most expensive first, while untapped lands can pay. The commander counts as in hand.
        while (true) {
            val s = me()
            val untapped = s[Zone.BATTLEFIELD].filter { it.card.isLand && !it.tapped }
            // Cards whose details couldn't be looked up (no type line) are held, not cast for free.
            val options = s[Zone.HAND].filter { !it.card.isLand && it.card.typeLine.isNotBlank() }.map { it to false } +
                s[Zone.COMMAND].map { it to true }
            val pick = options
                .filter { (p, cmd) -> cost(p, s, cmd) <= untapped.size }
                .maxByOrNull { (p, cmd) -> cost(p, s, cmd) } ?: break
            val (p, fromCommand) = pick
            val pay = cost(p, s, fromCommand)
            untapped.take(pay).forEach { table = Playtest.toggleTap(table, side, it.id) }
            table = Playtest.move(table, side, p.id, if (p.card.isPermanent) Zone.BATTLEFIELD else Zone.GRAVEYARD)
            said += "cast ${p.card.name}"
        }

        // 3. Attack with every untapped creature that's been here since the turn began.
        val target = (side + 1) % table.sides.size
        if (target != side) {
            val attackers = me()[Zone.BATTLEFIELD].filter { it.card.isCreature && !it.tapped && !it.sick && (it.power ?: 0) > 0 }
            val damage = attackers.sumOf { it.power ?: 0 }
            if (damage > 0) {
                attackers.forEach { table = Playtest.toggleTap(table, side, it.id) }
                table = Playtest.life(table, target, -damage)
                said += "attacked with ${attackers.size} for $damage"
            }
        }

        val who = me().name
        return Playtest.logged(table, if (said.isEmpty()) "$who passed" else "$who " + said.joinToString(", "))
    }
}
