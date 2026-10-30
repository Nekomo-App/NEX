package net.nekomo.nex.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Bump when the notice text changes materially; users are asked to acknowledge again. */
const val LEGAL_VERSION = 1

val LEGAL_SECTIONS = listOf(
    "What NEX is" to "NEX (Nekomo Extension Exchange) is a free, open-source tool that manages repositories, extensions, plugins and API settings for other apps. It does not host, stream, index, or distribute any media, and it ships with no third-party repositories enabled.",
    "Third-party content" to "Repositories and extensions are created and hosted by independent third parties. The Nekomo community does not own, control, review, or endorse them, and is not responsible for their content, availability, legality, or security. You add every repository yourself.",
    "Your responsibility" to "You are responsible for making sure that the repositories, extensions, and content you use are legal where you live, and that you comply with the terms of the services they access. Install only what you trust; NEX shows checksum, signature and package checks but cannot guarantee that third-party code is safe.",
    "Copyright & DMCA" to "NEX respects intellectual-property rights. NEX itself contains no copyrighted media. If you believe a repository or extension infringes your rights, contact the party that hosts it. Notices about the NEX source code itself can be filed as an issue at github.com/Nekomo-App/NEX.",
    "Trademarks" to "Mihon, Aniyomi, CloudStream and Zangetsu are separate projects. NEX is not affiliated with or endorsed by them; their names are used only to describe compatibility.",
    "No warranty" to "NEX is provided \"as is\" under the Apache License 2.0, without warranties of any kind.",
)

@Composable
fun LegalText(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LEGAL_SECTIONS.forEach { (h, b) ->
            Column {
                Text(h, fontWeight = FontWeight.SemiBold)
                Text(b, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
