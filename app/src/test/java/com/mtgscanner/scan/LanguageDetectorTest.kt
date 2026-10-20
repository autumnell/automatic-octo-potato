package com.mtgscanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageDetectorTest {
    @Test fun footerWins() {
        assertEquals("de", LanguageDetector.detect("Lightning Bolt deals 3 damage to any target.", "DE"))
        assertNull(LanguageDetector.detect("Blitzschlag verursacht 3 Schadenspunkte", "EN"))
        assertEquals("ja", LanguageDetector.detect("", "JP"))
        assertEquals("zhs", LanguageDetector.detect("", "CS"))
    }

    @Test fun englishIsNull() {
        assertNull(LanguageDetector.detect("Flying\nWhenever this creature deals damage to a player, draw a card. Until end of turn, target creature gets +1/+1."))
        assertNull(LanguageDetector.detect("Lightning Bolt"))
        assertNull(LanguageDetector.detect(""))
    }

    @Test fun scripts() {
        assertEquals("ja", LanguageDetector.detect("稲妻 インスタント カードを1枚引く。"))
        assertEquals("ko", LanguageDetector.detect("번개 충격 순간마법"))
        assertEquals("zhs", LanguageDetector.detect("闪电击 瞬间 造成三点伤害"))
        assertEquals("ru", LanguageDetector.detect("Молния Мгновенное заклинание"))
    }

    @Test fun latinLanguages() {
        assertEquals("de", LanguageDetector.detect("Fliegend\nWenn die Kreatur ins Spiel kommt, ziehe eine Karte. Bis zum Ende des Zuges erhält jede Kreatur +1/+1."))
        assertEquals("fr", LanguageDetector.detect("Vol\nQuand cette créature arrive, piochez une carte. Jusqu'à la fin du tour, chaque créature cible gagne +1/+1."))
        assertEquals("it", LanguageDetector.detect("Volare\nQuando questa creatura entra, pesca una carta. Fino alla fine del turno, ogni creatura bersaglio che controlli prende +1/+1."))
        assertEquals("es", LanguageDetector.detect("Vuela\nCuando la criatura entre, roba una carta. Hasta el final del turno, las criaturas objetivo que controlas obtienen +1/+1."))
        assertEquals("pt", LanguageDetector.detect("Voar\nQuando esta criatura entrar, compre um card. Até o final do turno, a criatura alvo que você controla recebe +1/+1."))
    }
}
