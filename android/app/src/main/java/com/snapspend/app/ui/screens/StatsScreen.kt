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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.BarChartV2
import com.snapspend.app.ui.components.BulletList
import com.snapspend.app.ui.components.CategoryLabel
import com.snapspend.app.ui.components.CategoryShareRow
import com.snapspend.app.ui.components.DonutChart
import com.snapspend.app.ui.components.ErrorRetry
import com.snapspend.app.ui.components.LoadingBox
import com.snapspend.app.ui.components.SectionCard
import com.snapspend.app.ui.format.formatVnd
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.LocalVmFactory
import com.snapspend.app.ui.viewmodel.StatsViewModel
import java.time.LocalDate

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val vm: StatsViewModel = viewModel(factory = LocalVmFactory.current)
    val range by vm.range.collectAsStateWithLifecycle()
    val stats by vm.stats.collectAsStateWithLifecycle()
    val basic by vm.basic.collectAsStateWithLifecycle()
    val full by vm.full.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val analyzing by vm.analyzing.collectAsStateWithLifecycle()
    val dayExpenses by vm.dayExpenses.collectAsStateWithLifecycle()
    val dayLoading by vm.dayLoading.collectAsStateWithLifecycle()
    val dayError by vm.dayError.collectAsStateWithLifecycle()
    var analysisTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    var asked by rememberSaveable { mutableStateOf<Int?>(null) }

    // Tải số liệu + phân tích cơ bản ngay khi mở tab (để hỏi nhanh dùng được luôn).
    LaunchedEffect(Unit) { vm.load(); vm.analyzeBasic() }

    Column(modifier.fillMaxSize().padding(Spacing.s16).verticalScroll(rememberScrollState())) {
        Text("Thống kê", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s8))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            listOf("7D", "30D", "Tháng này").forEach { r ->
                FilterChip(selected = range == r, onClick = { vm.onRange(r); selectedDay = null; vm.clearDay() }, label = { Text(r) })
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
        SectionCard("Chi tiêu theo ngày (bấm vào cột để lọc)") {
            BarChartV2(s.byDay, Modifier.fillMaxWidth(), onBarClick = { selectedDay = it; vm.loadDay(it) })
        }
        selectedDay?.let { day ->
            val dayList = dayExpenses
            Spacer(Modifier.height(Spacing.s12))
            SectionCard("Ngày $day — ${formatVnd(dayList.sumOf { it.amount })}") {
                if (dayLoading) Text("Đang tải…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (dayError != null) {
                    Text(dayError!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { vm.loadDay(day) }, modifier = Modifier.fillMaxWidth()) { Text("Thử lại") }
                } else {
                    if (dayList.isEmpty()) Text("Không có khoản chi nào.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dayList.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s4), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                CategoryLabel(e.category)
                                e.note?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                            AmountText(e.amount)
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.s8))
                TextButton(onClick = { selectedDay = null; vm.clearDay() }, modifier = Modifier.fillMaxWidth()) { Text("Bỏ lọc") }
            }
        }
        Spacer(Modifier.height(Spacing.s12))
        SectionCard("Theo danh mục") {
            if (s.byCategory.isEmpty()) Text("Chưa có số liệu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.byCategory.entries.sortedByDescending { it.value }.forEach { (key, value) -> CategoryShareRow(key, value, s.total) }
        }
        Spacer(Modifier.height(Spacing.s20))

        // 2 tab phân tích thay vì xếp chồng phải kéo dài.
        TabRow(selectedTabIndex = analysisTab) {
            Tab(selected = analysisTab == 0, onClick = { analysisTab = 0 }, text = { Text("Cơ bản") })
            Tab(selected = analysisTab == 1, onClick = { analysisTab = 1 }, text = { Text("AI") })
        }
        Spacer(Modifier.height(Spacing.s12))
        if (analysisTab == 0) {
            // 1) Phân tích cơ bản: chỉ số liệu, đánh giá mức độ chi tiêu.
            OutlinedButton(enabled = !analyzing, onClick = vm::analyzeBasic, modifier = Modifier.fillMaxWidth().testTag("btn_basic")) {
                Text(if (analyzing) "Đang phân tích…" else "Phân tích cơ bản")
            }
            basic?.let { b ->
                Spacer(Modifier.height(Spacing.s12))
                SectionCard("Phân tích cơ bản") {
                    Text(b.summary)
                    Spacer(Modifier.height(Spacing.s8))
                    Text("Mức độ: ${b.level}", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Spacing.s12))
                    Text("Cơ cấu chi tiêu", fontWeight = FontWeight.SemiBold)
                    DonutChart(b.breakdown, Modifier.fillMaxWidth())
                    if (b.breakdown.size > 5) {
                        Spacer(Modifier.height(Spacing.s8))
                        Text("Top 5 nhóm chi nhiều nhất", fontWeight = FontWeight.SemiBold)
                        BulletList(b.breakdown.take(5).map { "${it.name}: ${formatVnd(it.amount)} (${it.share}%)" })
                    }
                    if (b.topExpenses.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.s8))
                        Text("Top khoản chi lớn nhất", fontWeight = FontWeight.SemiBold)
                        BulletList(b.topExpenses.map { "${formatVnd(it.amount)} — ${it.note ?: it.category} (${it.date})" })
                    }
                    if (b.biggestDay != null) {
                        Spacer(Modifier.height(Spacing.s8))
                        Text("Ngày chi nhiều nhất: ${b.biggestDay} (${formatVnd(b.biggestDayAmount)})", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(Spacing.s8))
                    Text("Cuối tuần: ${formatVnd(b.weekendTotal)} · Ngày thường: ${formatVnd(b.weekdayTotal)}", style = MaterialTheme.typography.labelSmall)
                    if (b.recurring.isNotEmpty()) {
                        Spacer(Modifier.height(Spacing.s8))
                        Text("Khoản lặp lại", fontWeight = FontWeight.SemiBold)
                        BulletList(b.recurring.map { "\"${it.note}\" ${it.count} lần (${formatVnd(it.total)})" })
                    }
                }
            }
        } else {
            // 2) AI phân tích: phân tích cơ bản + AI đánh giá/lời khuyên.
            Button(enabled = !analyzing, onClick = vm::analyzeFull, modifier = Modifier.fillMaxWidth().testTag("btn_ai")) {
                Text(if (analyzing) "AI đang phân tích…" else "AI phân tích hành vi")
            }
            if (analyzing) { Spacer(Modifier.height(Spacing.s12)); LoadingBox("AI đang đọc số liệu…") }
            full?.let { f ->
                Spacer(Modifier.height(Spacing.s12))
                SectionCard("AI phân tích") {
                    Text(f.summary)
                    f.evaluation?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(Spacing.s8))
                        Text("AI đánh giá", fontWeight = FontWeight.SemiBold)
                        Text(it)
                    }
                    if (f.trends.isNotEmpty()) { Spacer(Modifier.height(Spacing.s8)); Text("Xu hướng", fontWeight = FontWeight.SemiBold); BulletList(f.trends) }
                    if (f.anomalies.isNotEmpty()) { Spacer(Modifier.height(Spacing.s8)); Text("Bất thường", fontWeight = FontWeight.SemiBold); BulletList(f.anomalies) }
                    if (f.recommendations.isNotEmpty()) { Spacer(Modifier.height(Spacing.s8)); Text("Gợi ý", fontWeight = FontWeight.SemiBold); BulletList(f.recommendations) }
                }
            }
        }

        // Hỏi nhanh: đáp án tính sẵn từ số liệu (không cần mạng/AI ngoài).
        Spacer(Modifier.height(Spacing.s20))
        SectionCard("Hỏi nhanh") {
            val qs = listOf(
                "Tháng này tôi tiêu tiền vào đâu nhiều nhất?",
                "Tại sao chi tiêu của tôi tăng?",
                "Khoản nào tôi có thể cắt giảm?",
                "So sánh tháng này với tháng trước.",
                "Tôi có đang chi tiêu bất thường không?",
                "Nếu mỗi tháng tôi tiết kiệm 2 triệu thì cuối năm có bao nhiêu?"
            )
            qs.forEachIndexed { i, q ->
                TextButton(onClick = { asked = if (asked == i) null else i }, modifier = Modifier.fillMaxWidth()) {
                    Text(q, modifier = Modifier.fillMaxWidth())
                }
                if (asked == i) {
                    Text(answerFor(i, basic, full), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.s8))
                }
            }
        }
    }
}

private fun answerFor(i: Int, basic: com.snapspend.app.data.remote.BasicAnalysisDto?, full: com.snapspend.app.data.remote.AiAnalysisDto?): String {
    if (basic == null) return "Đang tải số liệu, vui lòng đợi giây lát rồi hỏi lại."
    return when (i) {
        0 -> {
            val t = basic.breakdown.firstOrNull()
            if (t == null) "Chưa có chi tiêu trong kỳ này."
            else "Nhóm ${t.name} nhiều nhất: ${formatVnd(t.amount)} (${t.share}% tổng chi)."
        }
        1 -> "Mức độ chi tiêu: ${basic.level}."
        2 -> {
            val t = basic.breakdown.firstOrNull()
            if (t == null) "Chưa có chi tiêu để đánh giá."
            else "Hãy xem lại nhóm ${t.name} (${formatVnd(t.amount)}). " + (full?.recommendations?.firstOrNull() ?: "")
        }
        3 -> "Kỳ này ${formatVnd(basic.total)}; kỳ trước ${formatVnd(basic.previousTotal)}. ${basic.level}."
        4 -> {
            val a = full?.anomalies ?: emptyList()
            if (a.isEmpty()) "Không phát hiện chi tiêu bất thường trong kỳ này." else a.joinToString(" ")
        }
        5 -> {
            val left = 12 - LocalDate.now().monthValue + 1
            "Còn $left tháng tới cuối năm. Tiết kiệm 2 triệu/tháng → khoảng ${formatVnd(2_000_000L * left)}."
        }
        else -> ""
    }
}
