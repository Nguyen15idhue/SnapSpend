package com.snapspend.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.AuthViewModel
import com.snapspend.app.ui.viewmodel.LocalVmFactory

@Composable
fun AuthScreen(onLoggedIn: (String) -> Unit) {
    val vm: AuthViewModel = viewModel(factory = LocalVmFactory.current)
    val register by vm.register.collectAsStateWithLifecycle()
    val email by vm.email.collectAsStateWithLifecycle()
    val username by vm.username.collectAsStateWithLifecycle()
    val password by vm.password.collectAsStateWithLifecycle()
    val showPass by vm.showPass.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val emailOk = vm.emailOk()

    Column(Modifier.fillMaxSize().padding(Spacing.s24), verticalArrangement = Arrangement.Center) {
        Text("SnapSpend", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Ghi chi tiêu bằng một tấm ảnh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.s32))
        OutlinedTextField(email, vm::onEmail, label = { Text("Email") }, isError = email.isNotBlank() && !emailOk, supportingText = { if (email.isNotBlank() && !emailOk) Text("Email phải có @") }, modifier = Modifier.fillMaxWidth().testTag("field_email"))
        if (register) {
            Spacer(Modifier.height(Spacing.s8))
            OutlinedTextField(username, vm::onUsername, label = { Text("Username") }, modifier = Modifier.fillMaxWidth().testTag("field_username"))
        }
        Spacer(Modifier.height(Spacing.s8))
        OutlinedTextField(
            password, vm::onPassword, label = { Text("Mật khẩu (≥ 6 ký tự)") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = vm::toggleShowPass) { Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Hiện/ẩn") } },
            modifier = Modifier.fillMaxWidth().testTag("field_password")
        )
        Spacer(Modifier.height(Spacing.s16))
        Button(enabled = vm.canSubmit(), onClick = { vm.submit(onLoggedIn) }, modifier = Modifier.fillMaxWidth().testTag("btn_submit")) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(if (register) "Tạo tài khoản" else "Đăng nhập")
        }
        TextButton(vm::toggleMode, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
