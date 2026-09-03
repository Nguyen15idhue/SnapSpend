package com.snapspend.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.repository.SnapSpendRepository
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.CategoryLabel
import com.snapspend.app.ui.components.ConfidenceBadge
import com.snapspend.app.ui.components.SectionCard
import com.snapspend.app.ui.theme.Spacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repo: SnapSpendRepository,
    expense: ExpenseDto,
    friends: List<FriendDto>,
    onBack: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var amount by remember(expense.id) { mutableStateOf(expense.amount.toString()) }
    var category by remember(expense.id) { mutableStateOf(expense.category) }
    var note by remember(expense.id) { mutableStateOf(expense.note ?: "") }
    var date by remember(expense.id) { mutableStateOf(expense.expenseDate) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var shareMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Quay lại") } },
                actions = {
                    IconButton(onClick = { editing = !editing }) { Icon(Icons.Filled.Edit, "Sửa") }
                    IconButton(onClick = { shareMenu = true }) { Icon(Icons.Filled.Share, "Chia sẻ") }
                    DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                        if (friends.isEmpty()) DropdownMenuItem(text = { Text("Chưa có bạn bè") }, onClick = { shareMenu = false })
                        friends.forEach { f ->
                            DropdownMenuItem(
                                text = { Text("@${f.username}") },
                                onClick = {
                                    shareMenu = false
                                    scope.launch {
                                        runCatching { repo.shareExpense(expense.id, f.id) }
                                            .onSuccess { Toast.makeText(context, "Đã chia sẻ với @${f.username}", Toast.LENGTH_SHORT).show() }
                                            .onFailure { Toast.makeText(context, it.message ?: "Chia sẻ thất bại", Toast.LENGTH_SHORT).show() }
                                    }
                                }
                            )
                        }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Xoá", tint = MaterialTheme.colorScheme.error) }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(Spacing.s16)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.s12)
        ) {
            if (expense.imageUrl != null) {
                AsyncImage(
                    model = expense.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                SectionCard("Ảnh") { Text("🧾  Chưa có ảnh — placeholder theo danh mục.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            SectionCard("Số tiền") {
                if (!editing) {
                    AmountText(expense.amount, MaterialTheme.typography.displaySmall)
                } else {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter(Char::isDigit) },
                        label = { Text("Số tiền (VNĐ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionCard("Thông tin") {
                if (!editing) {
                    CategoryLabel(expense.category)
                    Text(expense.expenseDate, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    expense.note?.let { Text(it) }
                    Spacer(Modifier.height(Spacing.s8))
                    ConfidenceBadge(expense.aiConfidence)
                } else {
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Danh mục (food, shopping…)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.s8))
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Ghi chú") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(Spacing.s8))
                    OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Ngày (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                }
            }

            if (editing) {
                Button(
                    enabled = !saving && amount.isNotBlank(),
                    onClick = {
                        scope.launch {
                            saving = true; error = null
                            runCatching { repo.updateExpense(expense.id, amount.toLong(), category, note.ifBlank { null }, date) }
                                .onSuccess { editing = false; onChanged(); onBack() }
                                .onFailure { error = it.message }
                            saving = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (saving) "Đang lưu…" else "Lưu thay đổi") }
                OutlinedButton(onClick = { editing = false }, modifier = Modifier.fillMaxWidth()) { Text("Hủy") }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { repo.deleteExpense(expense.id) }
                            .onSuccess { confirmDelete = false; onChanged(); onBack() }
                            .onFailure { error = it.message; confirmDelete = false }
                    }
                }) { Text("Xóa", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
            title = { Text("Xóa chi tiêu?") },
            text = { Text("Không thể hoàn tác nếu không dùng Undo ở Album.") }
        )
    }
}
