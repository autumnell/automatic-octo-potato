package com.mtgscanner.ui.collection

import android.content.Context
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtgscanner.MtgApp
import com.mtgscanner.data.db.unitPrice
import com.mtgscanner.ui.components.CardImage
import com.mtgscanner.ui.components.EmptyState
import com.mtgscanner.ui.components.GradientButton
import com.mtgscanner.ui.components.SelectPill
import com.mtgscanner.ui.components.formatUsd
import com.mtgscanner.ui.theme.AppSettings
import com.mtgscanner.ui.theme.Palette
import kotlin.random.Random
import com.mtgscanner.ui.theme.Corners

/** Best quiz streak, saved on the phone. */
private object QuizPrefs {
    fun best(context: Context): Int = context.getSharedPreferences("app", Context.MODE_PRIVATE).getInt("quizBest", 0)
    fun saveBest(context: Context, n: Int) {
        context.getSharedPreferences("app", Context.MODE_PRIVATE).edit().putInt("quizBest", n).apply()
    }
}

/** A small game on your own cards: which box is it in, or is it worth more or less than another. Works offline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSheet(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val repo = (ctx.applicationContext as MtgApp).repository
    val cards by remember { repo.observeAll() }.collectAsState(initial = null)
    val boxes by remember { repo.observeBoxes() }.collectAsState(initial = emptyList())
    val prices = AppSettings.showPrices
    var mode by remember { mutableStateOf(if (prices) QuizMode.MIX else QuizMode.BOX) }
    var score by remember { mutableStateOf(QuizScore(best = QuizPrefs.best(ctx))) }
    var question by remember { mutableStateOf<QuizQuestion?>(null) }
    // The answer given: a box id, or 1 for higher and 0 for lower. Null until answered.
    var answer by remember { mutableStateOf<Long?>(null) }

    val pool = remember(cards) {
        cards.orEmpty().map { QuizCard(it.id, it.name, it.boxId, if (it.proxy) null else it.unitPrice(), it.imageUrl) }
    }
    val boxIds = remember(boxes) { boxes.map { it.id } }
    val boxNames = remember(boxes) { boxes.associate { it.id to it.name } }
    fun next() {
        answer = null
        question = QuizMath.next(if (prices) mode else QuizMode.BOX, pool, boxIds, Random.Default)
    }
    LaunchedEffect(cards != null, mode) { if (cards != null) next() }

    fun answerWith(value: Long, correct: Boolean) {
        if (answer != null) return
        answer = value
        val s = score.answer(correct)
        if (s.best > score.best) QuizPrefs.saveBest(ctx, s.best)
        score = s
        if (correct) com.mtgscanner.util.Haptics.tick()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Palette.Surface,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Quiz", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "Streak ${score.streak} · Best ${score.best}",
                    style = MaterialTheme.typography.labelLarge, color = Palette.Muted,
                )
            }
            if (prices) {
                Spacer(Modifier.height(10.dp))
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuizMode.entries.forEach { m -> SelectPill(m.label, m == mode, { mode = m }) }
                }
            }
            val q = question
            when {
                cards == null -> Spacer(Modifier.height(200.dp))
                pool.isEmpty() -> EmptyState("No cards yet")
                q == null -> EmptyState("Not enough to ask", body = if (mode == QuizMode.BOX || !prices) "Needs 2 boxes" else "Needs priced cards")
                q is QuizQuestion.WhichBox -> WhichBoxView(q, boxNames, answer) { id -> answerWith(id, id == q.card.boxId) }
                q is QuizQuestion.HigherLower -> HigherLowerView(q, answer) { higher -> answerWith(if (higher) 1L else 0L, higher == q.higher) }
            }
            if (q != null) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${score.right} / ${score.asked}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    GradientButton(if (answer == null) "Skip" else "Next", onClick = { next() })
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** One answer button: plain until answered, then green for right and red for a wrong pick. */
@Composable
private fun Choice(label: String, state: Boolean?, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val edge: Color = when (state) {
        true -> Palette.Success
        false -> Palette.Danger
        null -> Palette.Outline
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(Corners.Medium),
        color = if (state == null) Palette.SurfaceHigh else edge.copy(alpha = 0.16f),
        modifier = modifier.border(1.dp, edge, RoundedCornerShape(Corners.Medium)),
    ) {
        Text(
            label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun WhichBoxView(q: QuizQuestion.WhichBox, names: Map<Long, String>, answer: Long?, onPick: (Long) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        CardImage(q.card.image, Modifier.width(170.dp), zoom = true)
        Spacer(Modifier.height(10.dp))
        Text(q.card.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("Which box?", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Spacer(Modifier.height(12.dp))
        q.options.forEach { id ->
            val state = when {
                answer == null -> null
                id == q.card.boxId -> true
                id == answer -> false
                else -> null
            }
            Choice(names[id] ?: "Box", state, enabled = answer == null, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) { onPick(id) }
        }
    }
}

@Composable
private fun HigherLowerView(q: QuizQuestion.HigherLower, answer: Long?, onPick: (Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            PriceCard(q.shown, formatUsd(q.shown.price), Modifier.weight(1f))
            PriceCard(q.card, if (answer == null) "?" else formatUsd(q.card.price), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text("Higher or lower?", style = MaterialTheme.typography.bodyMedium, color = Palette.Muted)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(true to "Higher", false to "Lower").forEach { (higher, label) ->
                val picked = answer != null && (answer == 1L) == higher
                val state = when {
                    answer == null -> null
                    higher == q.higher -> true
                    picked -> false
                    else -> null
                }
                Choice(label, state, enabled = answer == null, modifier = Modifier.weight(1f)) { onPick(higher) }
            }
        }
    }
}

@Composable
private fun PriceCard(c: QuizCard, price: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        CardImage(c.image, Modifier.fillMaxWidth(), zoom = true)
        Spacer(Modifier.height(6.dp))
        Text(c.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(price, style = MaterialTheme.typography.titleMedium, color = Palette.AccentText)
    }
}
