package com.snapspend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snapspend.app.data.remote.TokenStore
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.CategoryLabel
import com.snapspend.app.ui.components.SectionCard
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.LocalVmFactory
import com.snapspend.app.ui.viewmodel.ProfileViewModel

@Composable
fun ProfileScreen(tokenStore: TokenStore, onLoggedOut: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfileViewModel = viewModel(factory = LocalVmFactory.current)
    val friends by vm.friends.collectAsStateWithLifecycle()
    val shared by vm.shared.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val newest = expenses.firstOrNull()
    var confirmDelete by remember { mutableStateOf(false) }
    // Tải lại danh sách được chia sẻ mỗi lần mở tab (chia sẻ từ máy khác sẽ hiện ra).
    LaunchedEffect(Unit) { vm.loadShared() }

    Column(modifier.fillMaxSize().padding(Spacing.s16).verticalScroll(rememberScrollState())) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s24))
        Text("Bạn bè (${friends.size})", fontWeight = FontWeight.Bold)
        friends.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(f.username.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(Spacing.s12))
                Text("@${f.username}", modifier = Modifier.weight(1f))
                if (newest != null) {
                    IconButton(onClick = { vm.shareNewest(newest, f) }) { Icon(Icons.Filled.Share, "Chia sẻ khoản mới nhất") }
                }
            }
        }
        Spacer(Modifier.height(Spacing.s8))
        Text("Được chia sẻ với tôi (${shared.size})", fontWeight = FontWeight.Bold)
        if (shared.isEmpty()) {
            Text("Chưa có khoản nào được chia sẻ.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
        shared.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    CategoryLabel(s.category)
                    Text("@${s.ownerUsername} · ${s.expenseDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                AmountText(s.amount)
            }
        }
        Spacer(Modifier.height(Spacing.s8))
        OutlinedTextField(username, vm::onUsername, label = { Text("Username bạn muốn thêm") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.s8))
        Button(onClick = vm::addFriend, modifier = Modifier.fillMaxWidth()) { Text("Thêm bạn") }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(Spacing.s32))
        OutlinedButton(onClick = { tokenStore.clear(); onLoggedOut() }, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
        Spacer(Modifier.height(Spacing.s8))
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Xóa tài khoản", color = MaterialTheme.colorScheme.error) }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                confirmButton = { TextButton(onClick = { vm.deleteAccount { tokenStore.clear(); onLoggedOut() }; confirmDelete = false }) { Text("Xóa", color = MaterialTheme.colorScheme.error) } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
                title = { Text("Xóa tài khoản?") },
                text = { Text("Tài khoản và dữ liệu chi tiêu liên quan sẽ bị xóa.") }
            )
        }
        Spacer(Modifier.height(Spacing.s12))
        Text("Privacy", fontWeight = FontWeight.Bold)
        Text("Ảnh và chi tiêu riêng tư mặc định. Chỉ chia sẻ khi bạn chủ động thực hiện.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
