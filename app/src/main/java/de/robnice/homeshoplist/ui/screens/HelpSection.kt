package de.robnice.homeshoplist.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/** One block of the help text (simple Markdown subset). */
internal sealed interface HelpBlock {
    data class Title(val text: String) : HelpBlock
    data class Chapter(val text: String) : HelpBlock
    data class Section(val text: String) : HelpBlock
    data class Bullet(val text: String) : HelpBlock
    data class Paragraph(val text: String) : HelpBlock
    /** Screenshot, file name relative to the language folder. */
    data class Picture(val file: String, val description: String) : HelpBlock
    /** Linked table of contents of all chapters and sections, with its (translated) title. */
    data class Contents(val title: String) : HelpBlock
}

internal object HelpContent {
    const val ROOT = "help"
    const val FALLBACK_LANGUAGE = "en"

    private val imagePattern = Regex("""^!\[(.*)]\((.+)\)$""")
    private const val CONTENTS_MARKER = "[toc]"

    /** Language folder for the app language, English if there is no translation. */
    fun languageFolder(context: Context, language: String = Locale.getDefault().language): String {
        val available = context.assets.list(ROOT).orEmpty().toSet()
        return if (language in available) language else FALLBACK_LANGUAGE
    }

    /** Chapters 1., 2., ...; sections 1.1, 1.2, ... within their chapter. Key: index of the block. */
    fun numberHeadings(headings: List<IndexedValue<HelpBlock>>): Map<Int, String> {
        var chapter = 0
        var section = 0
        return headings.associate { (index, block) ->
            index to if (block is HelpBlock.Chapter) {
                chapter++
                section = 0
                "$chapter."
            } else {
                section++
                "$chapter.$section"
            }
        }
    }

    /** Lines starting with "# ", "## ", "### ", "- ", "![text](file)"; consecutive text lines form a paragraph. */
    fun parse(markdown: String): List<HelpBlock> {
        val blocks = mutableListOf<HelpBlock>()
        val paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += HelpBlock.Paragraph(paragraph.toString())
                paragraph.clear()
            }
        }
        markdown.lines().forEach { raw ->
            val line = raw.trim()
            val image = imagePattern.find(line)
            when {
                line.isEmpty() -> flush()
                line.startsWith(CONTENTS_MARKER) -> {
                    flush(); blocks += HelpBlock.Contents(line.removePrefix(CONTENTS_MARKER).trim())
                }
                line.startsWith("### ") -> { flush(); blocks += HelpBlock.Section(line.removePrefix("### ")) }
                line.startsWith("## ") -> { flush(); blocks += HelpBlock.Chapter(line.removePrefix("## ")) }
                line.startsWith("# ") -> { flush(); blocks += HelpBlock.Title(line.removePrefix("# ")) }
                line.startsWith("- ") -> { flush(); blocks += HelpBlock.Bullet(line.removePrefix("- ")) }
                image != null -> {
                    flush()
                    blocks += HelpBlock.Picture(image.groupValues[2].trim(), image.groupValues[1].trim())
                }
                else -> {
                    if (paragraph.isNotEmpty()) paragraph.append(' ')
                    paragraph.append(line)
                }
            }
        }
        flush()
        return blocks
    }
}

/** Settings tab "?": complete help with screenshots, from assets/help/<language>/help.md. */
@Composable
internal fun HelpSection(scrollState: ScrollState) {
    val context = LocalContext.current
    val folder = remember { HelpContent.languageFolder(context) }
    val blocks by produceState(initialValue = emptyList<HelpBlock>(), folder) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("${HelpContent.ROOT}/$folder/help.md")
                    .bufferedReader(Charsets.UTF_8)
                    .use { HelpContent.parse(it.readText()) }
            }.getOrDefault(emptyList())
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    // Positions of the headings within this section and of the section within the scrolling page
    val headingTops = remember(blocks) { mutableStateMapOf<Int, Float>() }
    var sectionTop by remember { mutableFloatStateOf(0f) }
    val headings = remember(blocks) {
        blocks.withIndex().filter { it.value is HelpBlock.Chapter || it.value is HelpBlock.Section }
    }
    // Same numbers in the table of contents and at the headings: chapters 1., 2., sections 1.1, 1.2
    val numbers = remember(headings) { HelpContent.numberHeadings(headings) }

    fun scrollToHeading(index: Int) {
        val top = headingTops[index] ?: return
        val margin = with(density) { 8.dp.toPx() }
        coroutineScope.launch {
            scrollState.animateScrollTo((sectionTop + top - margin).roundToInt().coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier.onGloballyPositioned { sectionTop = it.positionInParent().y },
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEachIndexed { index, block ->
            val positioned = Modifier.onGloballyPositioned { headingTops[index] = it.positionInParent().y }
            when (block) {
                is HelpBlock.Contents -> HelpContents(block.title, headings, numbers, ::scrollToHeading)
                is HelpBlock.Chapter, is HelpBlock.Section -> Box(positioned) {
                    HelpBlockView(block, folder, numbers[index])
                }
                else -> HelpBlockView(block, folder)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Table of contents: chapters and (indented) sections, each a tap target of at least 48 dp. */
@Composable
private fun HelpContents(
    title: String,
    headings: List<IndexedValue<HelpBlock>>,
    numbers: Map<Int, String>,
    onSelect: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            headings.forEach { (index, block) ->
                val isChapter = block is HelpBlock.Chapter
                val number = numbers[index].orEmpty()
                val text = when (block) {
                    is HelpBlock.Chapter -> block.text
                    is HelpBlock.Section -> block.text
                    else -> ""
                }
                val style = if (isChapter) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable { onSelect(index) }
                        .padding(start = if (isChapter) 16.dp else 32.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = number,
                        style = style,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(if (isChapter) 28.dp else 36.dp)
                    )
                    Text(
                        text = text,
                        style = style,
                        fontWeight = if (isChapter) FontWeight.Medium else null,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpBlockView(block: HelpBlock, folder: String, number: String? = null) {
    val prefix = number?.let { "$it " }.orEmpty()
    when (block) {
        is HelpBlock.Title -> Text(
            text = block.text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        is HelpBlock.Chapter -> Text(
            text = prefix + block.text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 16.dp)
        )
        is HelpBlock.Section -> Text(
            text = prefix + block.text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )
        is HelpBlock.Bullet -> Row(verticalAlignment = Alignment.Top) {
            Text("•", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Text(block.text, style = MaterialTheme.typography.bodyMedium)
        }
        is HelpBlock.Paragraph -> Text(block.text, style = MaterialTheme.typography.bodyMedium)
        is HelpBlock.Picture -> HelpPicture("${HelpContent.ROOT}/$folder/${block.file}", block.description)
        is HelpBlock.Contents -> Unit
    }
}

@Composable
private fun HelpPicture(assetPath: String, description: String) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    bitmap?.let {
        // Screenshots are phone-shaped: a bit narrower than the text, centered
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Image(
                bitmap = it,
                contentDescription = description.ifBlank { null },
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .padding(vertical = 4.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            )
        }
    }
}
