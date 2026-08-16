package com.fersaiyan.cyanbridge.ui.adglasses

import android.content.Intent
import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Small presentation model for rich conversation output.
 *
 * AD Glasses deliberately does not auto-download arbitrary remote media embedded in an
 * AI response. A result may present a photo/video/audio/link card, and the user chooses
 * whether to open it. Media already on this phone may be previewed inline.
 */
internal sealed interface ADConversationBlock {
    data class TextBlock(val text: String) : ADConversationBlock
    data class CodeBlock(val language: String?, val code: String) : ADConversationBlock
    data class LinkBlock(
        val label: String,
        val target: String,
        val kind: ADConversationLinkKind,
    ) : ADConversationBlock
}

internal enum class ADConversationLinkKind {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    LINK,
}

@Composable
internal fun ADConversationMessageBody(
    content: String,
    userMessage: Boolean,
) {
    val blocks = parseADConversationBlocks(content)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ADConversationBlock.TextBlock -> Text(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ADColors.Ink,
                )
                is ADConversationBlock.CodeBlock -> ADConversationCodeBlock(block)
                is ADConversationBlock.LinkBlock -> ADConversationLinkCard(block, userMessage)
            }
        }
    }
}

@Composable
private fun ADConversationCodeBlock(block: ADConversationBlock.CodeBlock) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ADColors.SurfaceSubtle, RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Code,
                contentDescription = null,
                tint = ADColors.Muted,
                modifier = Modifier.size(17.dp),
            )
            Text(
                block.language?.takeIf { it.isNotBlank() } ?: "Code",
                modifier = Modifier.padding(start = 7.dp),
                style = MaterialTheme.typography.labelMedium,
                color = ADColors.Muted,
            )
        }
        Text(
            block.code,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = ADColors.Ink,
        )
    }
}

@Composable
private fun ADConversationLinkCard(
    block: ADConversationBlock.LinkBlock,
    userMessage: Boolean,
) {
    val context = LocalContext.current
    val uri = remember(block.target) { runCatching { Uri.parse(block.target) }.getOrNull() }
    val icon = block.kind.icon()
    val kindLabel = block.kind.label()
    val detail = displayTarget(block.target)
    val localPreview = uri?.scheme in setOf("content", "file") &&
        block.kind in setOf(ADConversationLinkKind.IMAGE, ADConversationLinkKind.VIDEO)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (userMessage) ADColors.Surface.copy(alpha = 0.72f) else ADColors.Surface,
                RoundedCornerShape(15.dp),
            )
            .clickable {
                val targetUri = uri ?: return@clickable
                val intent = Intent(Intent.ACTION_VIEW, targetUri).apply {
                    if (targetUri.scheme == "content") addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching { context.startActivity(intent) }
            }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (localPreview) 10.dp else 0.dp),
    ) {
        if (localPreview && uri != null) {
            ADConversationLocalMediaPreview(
                uri = uri,
                video = block.kind == ADConversationLinkKind.VIDEO,
                userMessage = userMessage,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(ADColors.SurfaceSubtle, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = ADColors.Ink,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(Modifier.padding(start = 11.dp).weight(1f)) {
                Text(
                    block.label.ifBlank { kindLabel },
                    style = MaterialTheme.typography.titleSmall,
                    color = ADColors.Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "$kindLabel · $detail",
                    style = MaterialTheme.typography.bodySmall,
                    color = ADColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.size(6.dp))
            Icon(
                Icons.Rounded.KeyboardArrowRight,
                contentDescription = "Open",
                tint = ADColors.Muted,
            )
        }
    }
}

@Composable
private fun ADConversationLocalMediaPreview(
    uri: Uri,
    video: Boolean,
    userMessage: Boolean,
) {
    val context = LocalContext.current
    var thumbnail by remember(uri.toString()) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri, video) {
        thumbnail = withContext(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext null
            when (uri.scheme) {
                "content" -> runCatching {
                    context.contentResolver.loadThumbnail(uri, Size(960, 600), null)
                }.getOrNull()
                "file" -> {
                    val path = uri.path ?: return@withContext null
                    val file = File(path)
                    if (!file.isFile) return@withContext null
                    runCatching {
                        if (video) {
                            ThumbnailUtils.createVideoThumbnail(file, Size(960, 600), null)
                        } else {
                            ThumbnailUtils.createImageThumbnail(file, Size(960, 600), null)
                        }
                    }.getOrNull()
                }
                else -> null
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clip(RoundedCornerShape(12.dp))
            .background(ADColors.SurfaceSubtle),
        contentAlignment = Alignment.Center,
    ) {
        thumbnail?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } ?: Icon(
            if (video) Icons.Outlined.Videocam else Icons.Outlined.Image,
            contentDescription = null,
            tint = ADColors.Muted,
            modifier = Modifier.size(34.dp),
        )

        if (video) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(ADColors.Ink.copy(alpha = 0.78f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.PlayArrow,
                    contentDescription = "Play video",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

internal fun parseADConversationBlocks(content: String): List<ADConversationBlock> {
    if (content.isBlank()) return emptyList()

    val blocks = mutableListOf<ADConversationBlock>()
    val textBuffer = mutableListOf<String>()
    val codeBuffer = mutableListOf<String>()
    var codeLanguage: String? = null
    var inCode = false

    fun flushText() {
        if (textBuffer.isEmpty()) return
        val text = textBuffer.joinToString("\n").trim()
        if (text.isNotEmpty()) blocks += ADConversationBlock.TextBlock(text)
        textBuffer.clear()
    }

    fun flushCode() {
        val code = codeBuffer.joinToString("\n").trimEnd()
        if (code.isNotEmpty()) blocks += ADConversationBlock.CodeBlock(codeLanguage, code)
        codeBuffer.clear()
        codeLanguage = null
    }

    content.lines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("```")) {
            if (inCode) {
                flushCode()
                inCode = false
            } else {
                flushText()
                codeLanguage = trimmed.removePrefix("```").trim().ifBlank { null }
                inCode = true
            }
            return@forEach
        }

        if (inCode) {
            codeBuffer += line
            return@forEach
        }

        val inline = parseInlineLinks(line)
        if (inline == null) {
            textBuffer += line
        } else {
            flushText()
            blocks += inline
        }
    }

    if (inCode) flushCode() else flushText()
    return blocks.ifEmpty { listOf(ADConversationBlock.TextBlock(content)) }
}

private fun parseInlineLinks(line: String): List<ADConversationBlock>? {
    val markdownMatches = MARKDOWN_INLINE.findAll(line).toList()
    if (markdownMatches.isNotEmpty()) {
        return buildInlineBlocks(line, markdownMatches) { match ->
            val imageHint = match.groupValues[1] == "!"
            val label = match.groupValues[2].trim().ifBlank {
                if (imageHint) "Image" else displayTarget(match.groupValues[3])
            }
            val target = match.groupValues[3].trim()
            ADConversationBlock.LinkBlock(label, target, inferLinkKind(target, imageHint))
        }
    }

    val rawMatches = RAW_URL.findAll(line).toList()
    if (rawMatches.isEmpty()) return null
    return buildInlineBlocks(line, rawMatches) { match ->
        val target = match.value.trimEnd('.', ',', ';')
        ADConversationBlock.LinkBlock(
            label = displayTarget(target),
            target = target,
            kind = inferLinkKind(target),
        )
    }
}

private fun buildInlineBlocks(
    line: String,
    matches: List<MatchResult>,
    linkFor: (MatchResult) -> ADConversationBlock.LinkBlock,
): List<ADConversationBlock> {
    val blocks = mutableListOf<ADConversationBlock>()
    var cursor = 0

    fun addText(fragment: String) {
        val text = fragment.trim()
        if (text.isNotEmpty() && !PUNCTUATION_ONLY.matches(text)) {
            blocks += ADConversationBlock.TextBlock(text)
        }
    }

    matches.forEach { match ->
        if (match.range.first > cursor) addText(line.substring(cursor, match.range.first))
        blocks += linkFor(match)
        cursor = match.range.last + 1
    }
    if (cursor < line.length) addText(line.substring(cursor))
    return blocks
}

private fun inferLinkKind(target: String, imageHint: Boolean = false): ADConversationLinkKind {
    if (imageHint) return ADConversationLinkKind.IMAGE
    val clean = target.substringBefore('?').substringBefore('#').lowercase()
    return when {
        clean.endsWith(".jpg") || clean.endsWith(".jpeg") || clean.endsWith(".png") ||
            clean.endsWith(".webp") || clean.endsWith(".gif") -> ADConversationLinkKind.IMAGE
        clean.endsWith(".mp4") || clean.endsWith(".mov") || clean.endsWith(".m4v") ||
            clean.endsWith(".webm") -> ADConversationLinkKind.VIDEO
        clean.endsWith(".mp3") || clean.endsWith(".wav") || clean.endsWith(".m4a") ||
            clean.endsWith(".aac") || clean.endsWith(".ogg") -> ADConversationLinkKind.AUDIO
        clean.endsWith(".pdf") || clean.endsWith(".doc") || clean.endsWith(".docx") ||
            clean.endsWith(".txt") || clean.endsWith(".csv") || clean.endsWith(".json") -> ADConversationLinkKind.DOCUMENT
        else -> ADConversationLinkKind.LINK
    }
}

private fun displayTarget(target: String): String {
    val uri = runCatching { Uri.parse(target) }.getOrNull()
    return when {
        uri == null -> target
        !uri.host.isNullOrBlank() -> uri.host.orEmpty().removePrefix("www.")
        uri.lastPathSegment?.isNotBlank() == true -> uri.lastPathSegment.orEmpty()
        else -> target.take(72)
    }
}

private fun ADConversationLinkKind.icon(): ImageVector = when (this) {
    ADConversationLinkKind.IMAGE -> Icons.Outlined.Image
    ADConversationLinkKind.VIDEO -> Icons.Outlined.Videocam
    ADConversationLinkKind.AUDIO -> Icons.Outlined.GraphicEq
    ADConversationLinkKind.DOCUMENT -> Icons.Outlined.Description
    ADConversationLinkKind.LINK -> Icons.Outlined.Link
}

private fun ADConversationLinkKind.label(): String = when (this) {
    ADConversationLinkKind.IMAGE -> "Image"
    ADConversationLinkKind.VIDEO -> "Video"
    ADConversationLinkKind.AUDIO -> "Audio"
    ADConversationLinkKind.DOCUMENT -> "Document"
    ADConversationLinkKind.LINK -> "Link"
}

private val MARKDOWN_INLINE = Regex("(!?)\\[([^]]*)]\\(([^)]+)\\)")
private val RAW_URL = Regex("(?:https?://|content://|file://)[^\\s<>()]+", RegexOption.IGNORE_CASE)
private val PUNCTUATION_ONLY = Regex("[.,;:!?\\])}]+")
