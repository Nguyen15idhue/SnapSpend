package com.snapspend.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.BarChartV2
import com.snapspend.app.ui.components.BulletList
import com.snapspend.app.ui.components.CategoryShareRow
import com.snapspend.app.ui.components.ErrorRetry
import com.snapspend.app.ui.components.LoadingBox
import com.snapspend.app.ui.components.SectionCard
import com.snapspend.app.ui.format.formatVnd
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.LocalVmFactory
import com.snapspend.app.ui.viewmodel.StatsViewModel

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val vm: StatsViewModel = viewModel(factory = LocalVmFactory.current)
    val range by vm.range.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val analysis by vm.analysis.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()

    // Tải lại mỗi lần mở tab để số liệu không bị cũ.
    LaunchedEffect(Unit) { vm.load() }

    Column(modifier.fillMaxSize().padding(Spacing.s16).verticalScroll(rememberScrollState())) {
        Text("Thống kê", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s8))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            listOf("7D", "30D", "Tháng này").forEach { r ->
                FilterChip(selected = range == r, onClick = { vm.onRange(r) }, label = { Text(r) })
            }
        }
        Spacer(Modifier.height(Spacing.s12))
        if (loading) { LoadingBox(); return@Column }
        if (stats == null) { ErrorRetry("Không tải được thống kê") { vm.load() }; return@Column }
        val s = stats!!
        Text(range, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(s.total, MaterialTheme.typography.displaySmall)
        Text("Trung bình ${formatVnd(s.averageDaily.toLong())}/ngày", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s20))
        SectionCard("Chi tiêu theo ngày") { BarChartV2(s.byDay, Modifier.fillMaxWidth()) }
        Spacer(Modifier.height(Spacing.s12))
        SectionCard("Theo danh mục") {
            if (s.byCategory.isEmpty()) Text("Chưa có số liệu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.byCategory.entries.sortedByDescending { it.value }.forEach { (key, value) -> CategoryShareRow(key, value, s.total) }
        }
        Spacer(Modifier.height(Spacing.s20))
        Button(enabled = !analyzing, onClick = vm::analyze, modifier = Modifier.fillMaxWidth()) { Text(if (analyzing) "AI đang phân tích…" else "AI phân tích hành vi") }
        if (analyzing) { Spacer(Modifier.height(Spacing.s12)); LoadingBox("AI đang đọc số liệu…") }
        analysis?.let {
            Spacer(Modifier.height(Spacing.s12))
            SectionCard("AI phân tích") {
                Text(it.summary)
                if (it.trends.isNotEmpty()) { Spacer(Modifier.height(Spacing.s8)); Text("Xu hướng", fontWeight = FontWeight.SemiBold); BulletList(it.trends) }
                if (it.anomalies.isNotEmpty()) { Spacer(Modifier.height(Spacing.s8)); Text("Bất thường", fontWeight = FontWeight.SemiBold); BulletList(it.anomalies) }
                if (it.recommendations.isNotEmpty()) { Spacer(Modifier.height(Spacing.s8)); Text("Gợi ý", fontWeight = FontWeight.SemiBold); BulletList(it.recommendations) }
            }
        }
    }
}
