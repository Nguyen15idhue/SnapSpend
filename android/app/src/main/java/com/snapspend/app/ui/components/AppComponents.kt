package com.snapspend.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snapspend.app.model.categories
import com.snapspend.app.ui.format.formatVnd
import com.snapspend.app.ui.theme.Spacing

@Composable
fun CategoryChip(key: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = categories.find { it.key == key }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(if (c == null) key else "${c.emoji} ${c.label}") },
        modifier = modifier
    )
}

@Composable
fun CategoryLabel(key: String) {
    val c = categories.find { it.key == key }
    Text(if (c == null) key else "${c.emoji} ${c.label}", fontWeight = FontWeight.SemiBold)
}

@Composable
fun AmountText(value: Long, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium) {
    Text(formatVnd(value), style = style, fontWeight = FontWeight.Bold)
}

@Composable
fun EmptyState(title: String, desc: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.s24),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🧾", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Spacing.s8))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s4))
        Text(desc, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Spacing.s12))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingBox(label: String = "Đang tải…") {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.s24),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(Spacing.s8))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorRetry(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(Spacing.s24),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("⚠️", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(Spacing.s8))
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.s12))
        Button(onClick = onRetry) { Text("Thử lại") }
    }
}

/** Cột bar có bo góc + nhãn ngày, màu primary. Bấm vào cột để lọc theo ngày. */
@Composable
fun BarChartV2(data: Map<String, Long>, modifier: Modifier = Modifier, onBarClick: (String) -> Unit = {}) {
    val values = data.toList().sortedBy { it.first }.takeLast(14)
    val max = (values.maxOfOrNull { it.second } ?: 1L).toFloat()
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).pointerInput(values) {
            detectTapGestures { offset ->
                if (values.isEmpty()) return@detectTapGestures
                val barW = size.width / values.size
                val i = (offset.x / barW).toInt().coerceIn(values.indices)
                onBarClick(values[i].first)
            }
        }) {
            if (values.isEmpty()) return@Canvas
            val barW = size.width / values.size
            values.forEachIndexed { i, (_, value) ->
                val h = (size.height * (value / max)).coerceAtLeast(if (value > 0) 8f else 0f)
                drawRoundRect(
                    color = primary,
                    topLeft = androidx.compose.ui.geometry.Offset(i * barW + barW * .18f, size.height - h),
                    size = androidx.compose.ui.geometry.Size(barW * .64f, h),
                    cornerRadius = CornerRadius(12f, 12f)
                )
            }
        }
        if (values.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(values.first().first.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(values.last().first.takeLast(5), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Text("Chưa có số liệu", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Dòng byCategory kèm % và thanh tiến trình. */
@Composable
fun CategoryShareRow(key: String, value: Long, total: Long) {
    val pct = if (total > 0) (value * 100.0 / total) else 0.0
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.s4)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            CategoryLabel(key)
            AmountText(value, MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(Spacing.s4))
        LinearProgressIndicator(
            progress = { (pct / 100).toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small)
        )
        Text("${pct.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ConfidenceBadge(confidence: Double?) {
    if (confidence == null) return
    val good = confidence >= 0.7
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (good) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        )
    ) {
        Text(
            "AI ${(confidence * 100).toInt()}%",
            modifier = Modifier.padding(horizontal = Spacing.s8, vertical = Spacing.s4),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(Spacing.s16)) {
            Text(title, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(Spacing.s8))
            content()
        }
    }
}

@Composable
fun BulletList(items: List<String>) {
    Column {
        items.forEach { x ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("•  ")
                Text(x, Modifier.weight(1f))
            }
        }
    }
}

/** Biểu đồ tròn/donut cơ cấu chi tiêu theo danh mục. */
@Composable
fun DonutChart(shares: List<com.snapspend.app.data.remote.CategoryShareDto>, modifier: Modifier = Modifier) {
    if (shares.isEmpty()) {
        Text("Chưa có số liệu", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val palette = listOf(
        androidx.compose.ui.graphics.Color(0xFF16A34A),
        androidx.compose.ui.graphics.Color(0xFF2563EB),
        androidx.compose.ui.graphics.Color(0xFFF59E0B),
        androidx.compose.ui.graphics.Color(0xFFEF4444),
        androidx.compose.ui.graphics.Color(0xFF8B5CF6),
        androidx.compose.ui.graphics.Color(0xFF06B6D4),
        androidx.compose.ui.graphics.Color(0xFFEC4899)
    )
    val total = shares.sumOf { it.amount }.coerceAtLeast(1L)
    androidx.compose.foundation.Canvas(modifier.size(180.dp)) {
        var start = -90f
        shares.forEachIndexed { i, s ->
            val sweep = (s.amount.toFloat() / total) * 360f
            drawArc(palette[i % palette.size], start, sweep, useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 44f, cap = androidx.compose.ui.graphics.StrokeCap.Butt))
            start += sweep
        }
    }
    Spacer(Modifier.height(Spacing.s8))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        shares.take(5).forEachIndexed { i, s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(palette[i % palette.size], androidx.compose.foundation.shape.CircleShape))
                Spacer(Modifier.width(Spacing.s8))
                Text("${s.name}: ${com.snapspend.app.ui.format.formatVnd(s.amount)} (${s.share}%)", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
