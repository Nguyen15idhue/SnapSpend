package com.snapspend.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.snapspend.app.data.ocr.ReceiptOcr
import com.snapspend.app.ui.components.ConfidenceBadge
import com.snapspend.app.ui.format.formatVnd
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.CategoryViewModel
import com.snapspend.app.ui.viewmodel.ExpenseFormViewModel
import com.snapspend.app.ui.viewmodel.LocalVmFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormScreen(uri: Uri?, onDone: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ExpenseFormViewModel = viewModel(factory = LocalVmFactory.current)
    val catVm: CategoryViewModel = viewModel(factory = LocalVmFactory.current)
    val cats by catVm.categories.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val confidence by vm.confidence.collectAsStateWithLifecycle()
    val candidates by vm.candidates.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val itemCats by vm.itemCats.collectAsStateWithLifecycle()
    val classifying by vm.classifying.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val amountOk = (amount.toLongOrNull() ?: 0L) > 0
    val context = LocalContext.current

    // Có ảnh -> OCR (text thô) -> hiện ngay bằng luật nội bộ (nhanh) -> AI làm sạch ở nền (ghi đè nếu hợp lệ).
    LaunchedEffect(uri) {
        if (uri != null) {
            val text = ReceiptOcr.readText(context, uri)
            if (text.isNotBlank()) {
                vm.setOcrText(text)
                vm.applyOcr(text, ReceiptOcr.summarize(text))
                // Chỉ tự điền số tiền khi bắt được dòng Tổng (chắc chắn); còn không để trống cho người dùng nhập.
                ReceiptOcr.extractTotal(text)?.let { vm.prefillAmount(it) }
                vm.analyzeReceipt()
                if (vm.extractViaAi(text)) vm.analyzeReceipt()
            }
        }
    }

    // Tự động phân loại khi có ảnh hoặc ghi chú; debounce để dùng ghi chú mới nhất.
    LaunchedEffect(uri, note) {
        if (uri != null || note.isNotBlank()) {
            delay(800)
            vm.classify(uri)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiêu mới") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } }
            )
        }
    ) { padding ->
        Column(modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.s16)) {
            if (uri != null) {
                // Hiện đủ ảnh dọc (Fit) thay vì Crop cắt mất nửa trên/dưới.
                AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Fit)
            } else {
                Text("Không dùng ảnh — chỉ nhập thông tin.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(Spacing.s16))
            OutlinedTextField(amount, vm::onAmount, label = { Text("Số tiền (VNĐ)") }, isError = amount.isNotBlank() && !amountOk, supportingText = { if (amount.isNotBlank() && !amountOk) Text("Số tiền phải lớn hơn 0") }, modifier = Modifier.fillMaxWidth().testTag("field_amount"))
            Spacer(Modifier.height(Spacing.s12))
            OutlinedTextField(note, vm::onNote, label = { Text("Ghi chú (VD: cơm tấm sườn)") }, modifier = Modifier.fillMaxWidth().testTag("field_note"))
            Spacer(Modifier.height(Spacing.s12))

            Text("Danh mục", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Spacing.s4))
            OutlinedButton(onClick = { vm.classify(uri) }, enabled = !classifying, modifier = Modifier.testTag("btn_classify")) {
                Text(if (classifying) "AI đang phân loại…" else "AI phân loại")
            }
            Spacer(Modifier.height(Spacing.s8))
            if (category.isNotBlank()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("AI gợi ý:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.s8))
                    ConfidenceBadge(confidence)
                }
                Spacer(Modifier.height(Spacing.s8))
            }
            if (candidates.size > 1) {
                val names = candidates.map { key -> cats.find { it.key == key }?.name ?: key }
                Text("Hóa đơn có thể gồm: " + names.joinToString(", ") + ". Chọn danh mục chính bên dưới.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Spacing.s8))
            }
            if (items.isNotEmpty()) {
                Text("Các món phát hiện:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Spacing.s4))
                items.forEach { item ->
                    val cname = itemCats[item.name]?.let { key -> cats.find { it.key == key }?.name ?: key }
                    Text("• ${item.name} — ${formatVnd(item.amount)}" + (cname?.let { " → $it" } ?: ""), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (items.size > 1) {
                    Spacer(Modifier.height(Spacing.s8))
                    OutlinedButton(onClick = { vm.splitExpenses(onDone) }, enabled = !loading, modifier = Modifier.fillMaxWidth().testTag("btn_split")) {
                        Text("Tách thành ${items.size} khoản")
                    }
                }
                Spacer(Modifier.height(Spacing.s8))
            }
            FlowRow(Modifier.fillMaxWidth().padding(vertical = Spacing.s4), horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                cats.forEach { c ->
                    FilterChip(selected = category == c.key, onClick = { vm.onCategory(c.key) }, modifier = Modifier.testTag("chip_category_${c.key}"), label = { Text("${c.emoji} ${c.name}") })
                }
            }

            Spacer(Modifier.height(Spacing.s16))
            Button(enabled = vm.canSave(), onClick = { vm.submit(uri) { vm.reset(); onDone() } }, modifier = Modifier.fillMaxWidth().testTag("btn_save")) {
                Text(if (loading) "Đang lưu…" else "Lưu")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}
