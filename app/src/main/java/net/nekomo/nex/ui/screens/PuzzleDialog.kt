package net.nekomo.nex.ui.screens

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.nekomo.nex.util.Puzzle

/** Multi-step puzzle gate. Tapping the title [Puzzle.BYPASS_TAPS] times quickly is the hidden bypass. */
@Composable
fun PuzzleDialog(kind: Puzzle.Kind, title: String, onSolved: () -> Unit, onDismiss: () -> Unit) {
    var rounds by remember { mutableStateOf(Puzzle.generate(kind)) }
    var index by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }
    var fails by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var taps by remember { mutableStateOf(0) }
    var windowStart by remember { mutableStateOf(0L) }
    fun solved() { Puzzle.markUnlocked(kind); onSolved() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, Modifier.clickable {
                val now = SystemClock.elapsedRealtime()
                if (now - windowStart > Puzzle.BYPASS_WINDOW_MS) { taps = 0; windowStart = now }
                taps++
                if (taps >= Puzzle.BYPASS_TAPS) solved()
            })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Puzzle ${index + 1} of ${rounds.size}. Solve all to continue.")
                Text(rounds[index].prompt)
                OutlinedTextField(input, { input = it; message = null }, label = { Text("Your answer") }, singleLine = true)
                message?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (Puzzle.isCorrect(rounds[index], input)) {
                    input = ""
                    if (index + 1 >= rounds.size) solved() else index++
                } else {
                    fails++
                    input = ""
                    if (fails % 3 == 0) { rounds = Puzzle.generate(kind); index = 0; message = "Wrong again. Here is a new puzzle." } else message = "Not quite. Try again."
                }
            }) { Text("Check") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
