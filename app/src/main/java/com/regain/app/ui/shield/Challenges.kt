package com.regain.app.ui.shield

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.regain.app.core.Haptics
import com.regain.app.ui.components.ProgressBar
import com.regain.app.ui.components.QuietButton
import com.regain.app.ui.components.RIcon
import com.regain.app.ui.components.RIcons
import com.regain.app.ui.components.RText
import com.regain.app.ui.components.pressable
import com.regain.app.ui.theme.RColors
import com.regain.app.ui.theme.RTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

// ---------------------------------------------------------------- questions

data class Question(val text: String, val answer: Int)

/**
 * Mental-math only, no calculator needed. Level 1 = first extension,
 * level 2 = second extension, level 3 = second extension at night.
 */
object QuestionBank {
    fun next(level: Int, avoid: String? = null, rnd: Random = Random.Default): Question {
        var q: Question
        do { q = make(level.coerceIn(1, 3), rnd) } while (q.text == avoid)
        return q
    }

    private fun make(level: Int, r: Random): Question = when (level) {
        1 -> when (r.nextInt(4)) {
            0 -> { val a = r.nextInt(12, 60); val b = r.nextInt(5, 39); Question("$a + $b", a + b) }
            1 -> { val a = r.nextInt(30, 90); val b = r.nextInt(6, a - 5); Question("$a − $b", a - b) }
            2 -> { val a = r.nextInt(3, 10); val b = r.nextInt(3, 10); Question("$a × $b", a * b) }
            else -> { val b = r.nextInt(2, 10); val q = r.nextInt(2, 10); Question("${b * q} ÷ $b", q) }
        }
        2 -> when (r.nextInt(5)) {
            0 -> { val a = r.nextInt(12, 26); val b = r.nextInt(3, 10); Question("$a × $b", a * b) }
            1 -> { val b = r.nextInt(3, 13); val q = r.nextInt(4, 13); Question("${b * q} ÷ $b", q) }
            2 -> { val a = r.nextInt(38, 99); val b = r.nextInt(27, 99); Question("$a + $b", a + b) }
            3 -> { val a = r.nextInt(102, 200); val b = r.nextInt(18, 99); Question("$a − $b", a - b) }
            else -> { val a = r.nextInt(11, 16); Question("$a²", a * a) }
        }
        else -> when (r.nextInt(4)) {
            0 -> { val a = r.nextInt(6, 13); val b = r.nextInt(4, 10); val c = r.nextInt(5, 40); Question("$a × $b + $c", a * b + c) }
            1 -> { val a = r.nextInt(14, 30); val b = r.nextInt(4, 9); Question("$a × $b", a * b) }
            2 -> { val a = r.nextInt(210, 500); val b = r.nextInt(45, 199); Question("$a − $b", a - b) }
            else -> { val b = r.nextInt(6, 13); val q = r.nextInt(6, 16); val c = r.nextInt(3, 20); Question("${b * q} ÷ $b + $c", q + c) }
        }
    }
}

// ---------------------------------------------------------------- math challenge

private const val NEEDED = 5

@Composable
fun MathChallenge(level: Int, onSolved: () -> Unit, onCancel: () -> Unit) {
    val secondsPerQuestion = if (level >= 3) 25 else 20
    var solved by remember { mutableIntStateOf(0) }
    var question by remember { mutableStateOf(QuestionBank.next(level)) }
    var input by remember { mutableStateOf("") }
    var round by remember { mutableIntStateOf(0) } // restarts the timer
    var flash by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val timeLeft = remember { Animatable(1f) }

    fun newQuestion() {
        question = QuestionBank.next(level, avoid = question.text)
        input = ""
        round++
    }

    fun wrong(msg: String) {
        Haptics.play(Haptics.Kind.Error)
        flash = msg
        scope.launch {
            for (x in listOf(18f, -14f, 10f, -6f, 0f)) shake.animateTo(x, tween(55))
        }
        newQuestion()
    }

    fun submit() {
        if (input.isEmpty()) return
        if (input.toIntOrNull() == question.answer) {
            solved++
            flash = null
            if (solved >= NEEDED) {
                Haptics.play(Haptics.Kind.Reward)
                onSolved()
            } else {
                Haptics.play(Haptics.Kind.Success)
                newQuestion()
            }
        } else wrong("Not quite, here's a new one")
    }

    // Debug builds log the answer so the flow can be tested end to end.
    LaunchedEffect(question) {
        if (com.regain.app.BuildConfig.DEBUG) android.util.Log.d("RegainMath", "Q ${question.text} = ${question.answer}")
    }

    // Per-question timer: runs out -> new question.
    LaunchedEffect(round) {
        timeLeft.snapTo(1f)
        timeLeft.animateTo(0f, tween(secondsPerQuestion * 1000, easing = LinearEasing))
        wrong("Too slow, here's a new one")
    }
    LaunchedEffect(flash) { if (flash != null) { delay(1600); flash = null } }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // Progress dots.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(NEEDED) { i ->
                val on = i < solved
                val s by animateFloatAsState(if (on) 1f else 0.7f, spring(dampingRatio = 0.4f), label = "dot")
                Box(
                    Modifier
                        .size(12.dp)
                        .graphicsLayer { scaleX = s; scaleY = s }
                        .background(if (on) RColors.Mint else Color(0x33FFFFFF), CircleShape),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        RText("${NEEDED - solved} to go", style = RTheme.type.footnote, color = RColors.Secondary)
        Spacer(Modifier.height(26.dp))

        AnimatedContent(
            targetState = question,
            transitionSpec = {
                (slideInVertically(spring(dampingRatio = 0.8f)) { it / 2 } + fadeIn()) togetherWith
                    (slideOutVertically { -it / 2 } + fadeOut())
            },
            label = "q",
        ) { q ->
            RText(
                q.text,
                style = RTheme.type.display.copy(fontSize = 56.sp, fontWeight = FontWeight.Light),
                align = TextAlign.Center,
                modifier = Modifier.graphicsLayer { translationX = shake.value * density },
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .width(200.dp)
                .height(58.dp)
                .graphicsLayer { translationX = shake.value * density }
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            RText(if (input.isEmpty()) "?" else input, style = RTheme.type.title1, color = if (input.isEmpty()) RColors.Tertiary else RColors.Label)
        }
        Spacer(Modifier.height(12.dp))
        ProgressBar(timeLeft.value, if (timeLeft.value < 0.25f) RColors.Coral else RColors.Accent, Modifier.width(200.dp), height = 4.dp)
        Box(Modifier.height(30.dp), contentAlignment = Alignment.Center) {
            flash?.let { RText(it, style = RTheme.type.footnote, color = RColors.Coral) }
        }

        Keypad(
            onDigit = { d -> if (input.length < 5) input += d },
            onDelete = { input = input.dropLast(1) },
            onSubmit = ::submit,
        )
        Spacer(Modifier.height(8.dp))
        QuietButton("Never mind", onCancel)
    }
}

@Composable
private fun Keypad(onDigit: (Char) -> Unit, onDelete: () -> Unit, onSubmit: () -> Unit) {
    val rows = listOf("123", "456", "789", "<0>")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { k ->
                    when (k) {
                        '<' -> Key(onClick = onDelete, bg = Color.Transparent) {
                            RIcon(RIcons.Backspace, size = 26.dp)
                        }
                        '>' -> Key(onClick = onSubmit, bg = RColors.Accent, haptic = null) {
                            RIcon(RIcons.Check, size = 28.dp)
                        }
                        else -> Key(onClick = { onDigit(k) }) {
                            RText(k.toString(), style = RTheme.type.title1.copy(fontWeight = FontWeight.Normal))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Key(
    onClick: () -> Unit,
    bg: Color = Color(0x26FFFFFF),
    haptic: Haptics.Kind? = Haptics.Kind.Tick,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(74.dp)
            .pressable(haptic = haptic, pressedScale = 0.88f, onClick = onClick)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------- typing challenge

private val sentences = listOf(
    "I am choosing to spend more of my evening on this app.",
    "This extra time is coming out of something I care about.",
    "I could put the phone down now, but I am choosing not to.",
    "Fifteen more minutes is fine, as long as I really mean it.",
)

@Composable
fun TypingChallenge(level: Int, onSolved: () -> Unit, onCancel: () -> Unit) {
    val target = remember { sentences[Random.nextInt(sentences.size)] + if (level >= 2) " I will stop after this." else "" }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Prefix match drives colouring; one wrong character turns coral.
    val matchLen = text.zip(target).takeWhile { (a, b) -> a == b }.size
    val done = text == target

    LaunchedEffect(done) {
        if (done) {
            Haptics.play(Haptics.Kind.Reward)
            delay(350)
            onSolved()
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RText("Type this exactly", style = RTheme.type.footnote, color = RColors.Secondary)
        Spacer(Modifier.height(14.dp))
        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = RColors.Mint)) { append(target.take(matchLen)) }
            withStyle(SpanStyle(color = if (text.length > matchLen) RColors.Coral else RColors.Label)) {
                append(target.drop(matchLen).take(1))
            }
            withStyle(SpanStyle(color = RColors.Secondary)) { append(target.drop(matchLen + 1)) }
        }
        androidx.compose.foundation.text.BasicText(
            annotated,
            style = RTheme.type.title3.copy(fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, lineHeight = 30.sp),
        )
        Spacer(Modifier.height(22.dp))
        BasicTextField(
            value = text,
            onValueChange = { new ->
                // Typed, not pasted: accept at most a couple of chars at a time.
                if (new.length - text.length <= 2) {
                    if (new.length > text.length) Haptics.play(Haptics.Kind.Tick)
                    text = new
                }
            },
            textStyle = RTheme.type.body.copy(color = RColors.Label),
            cursorBrush = SolidColor(RColors.Accent),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .background(Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
                .padding(16.dp),
        )
        Spacer(Modifier.height(12.dp))
        ProgressBar(matchLen.toFloat() / target.length, RColors.Mint, height = 4.dp)
        Spacer(Modifier.height(10.dp))
        QuietButton("Never mind", onCancel)
    }
}
