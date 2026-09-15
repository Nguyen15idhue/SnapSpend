package com.snapspend.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.CategoryLabel
import com.snapspend.app.ui.components.ConfidenceBadge
import com.snapspend.app.ui.components.LoadingBox
import com.snapspend.app.ui.components.SectionCard
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.DetailViewModel
import com.snapspend.app.ui.viewmodel.LocalVmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(id: Long, onBack: () -> Unit, onChanged: () -> Unit) {
    val context = LocalContext.current
    val vm: DetailViewModel = viewModel(factory = LocalVmFactory.current)
    // Id đi theo nav arg (tự sống sót qua recreate); field sửa nằm trong SavedStateHandle.
    LaunchedEffect(id) { vm.open(id) }
    val expense by vm.expense.collectAsStateWithLifecycle()
    val notFound by vm.notFound.collectAsStateWithLifecycle()
    val friends by vm.friends.collectAsStateWithLifecycle()
    val editing by vm.editing.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val note by vm.note.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }

    LaunchedEffect(notFound) { if (notFound) onBack() }
    LaunchedEffect(msg) { msg?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.clearMsg() } }

    val e = expense
    if (e == null) {
        LoadingBox()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                actions = {
                    IconButton(onClick = vm::toggleEditing, modifier = Modifier.testTag("btn_edit")) { Icon(Icons.Filled.Edit, "Sửa") }
                    IconButton(onClick = { shareMenu = true }, modifier = Modifier.testTag("btn_share")) { Icon(Icons.Filled.Share, "Chia sẻ") }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        if (friends.isEmpty()) DropdownMenuItem(text = { Text("Chưa có bạn bè") }, onClick = { shareMenu = false })
                        friends.forEach { f ->
                            DropdownMenuItem(text = { Text("@${f.username}") }, onClick = { shareMenu = false; vm.share(f) })
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("btn_delete")) { Icon(Icons.Filled.Delete, "Xoá", tint = MaterialTheme.colorScheme.error) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.s16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12)
        ) {
            if (e.imageUrl != null) {
                AsyncImage(
                    model = e.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                SectionCard("Ảnh") { Text("🧾  Chưa có ảnh — placeholder theo danh mục.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            SectionCard("Số tiền") {
                if (!editing) {
                    AmountText(e.amount, MaterialTheme.typography.displaySmall)
                } else {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = vm::onAmount,
                        label = { Text("Số tiền (VNĐ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionCard("Thông tin") {
                if (!editing) {
                    CategoryLabel(e.category)
                    Text(e.expenseDate, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    e.note?.let { Text(it) }
                    Spacer(Modifier.height(Spacing.s8))
                    ConfidenceBadge(e.aiConfidence)
                } else {
                    OutlinedTextField(value = category, onValueChange = vm::onCategory, label = { Text("Danh mục (food, shopping…)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.s8))
                    OutlinedTextField(value = note, onValueChange = vm::onNote, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.s8))
                    OutlinedTextField(value = date, onValueChange = vm::onDate, label = { Text("Ngày (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                }
            }

            if (editing) {
                Button(
                    enabled = !saving && amount.isNotBlank(),
                    onClick = { vm.save { onChanged(); onBack() } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (saving) "Đang lưu…" else "Lưu thay đổi") }
                OutlinedButton(onClick = vm::cancelEditing, modifier = Modifier.fillMaxWidth()) { Text("Hủy") }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = { vm.delete { confirmDelete = false; onChanged(); onBack() } }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
            title = { Text("Xóa chi tiêu?") },
            text = { Text("Không thể hoàn tác nếu không dùng Undo ở Album.") }
        )
    }
}
