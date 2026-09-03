package com.snapspend.app

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.snapspend.app.data.AppConfig
import com.snapspend.app.data.local.AppDatabase
import com.snapspend.app.data.remote.TokenStore
import com.snapspend.app.data.remote.createApi
import com.snapspend.app.data.repository.MockRepository
import com.snapspend.app.data.repository.RealRepository
import com.snapspend.app.data.repository.SnapSpendRepository
import com.snapspend.app.model.categories
import com.snapspend.app.ui.format.formatVnd
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = TokenStore(applicationContext)
        // Demo mặc định: MockRepository, không cần DB/API/backend. Tắt demo mới khởi tạo Real.
        val repo: SnapSpendRepository = if (AppConfig.isDemo) {
            MockRepository()
        } else {
            val db = AppDatabase.create(applicationContext)
            val api = createApi(tokenStore)
            RealRepository(applicationContext, db, api)
        }
        setContent { SnapSpendApp(repo, tokenStore) }
    }
}

private enum class Tab { ALBUM, CAMERA, STATS, PROFILE }

@Composable
fun SnapSpendApp(repo: SnapSpendRepository, tokenStore: TokenStore) {
    var loggedIn by remember { mutableStateOf(tokenStore.token != null) }
    if (!loggedIn) {
        AuthScreen(repo) { tokenStore.token = it; loggedIn = true }
        return
    }
    var tab by remember { mutableStateOf(Tab.CAMERA) }
    var refresh by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(tab == Tab.ALBUM, { tab = Tab.ALBUM }, icon = { Text("▦") }, label = { Text("Album") })
                NavigationBarItem(tab == Tab.CAMERA, { tab = Tab.CAMERA }, icon = { Text("●") }, label = { Text("Camera") })
                NavigationBarItem(tab == Tab.STATS, { tab = Tab.STATS }, icon = { Text("▥") }, label = { Text("Stats") })
                NavigationBarItem(tab == Tab.PROFILE, { tab = Tab.PROFILE }, icon = { Text("○") }, label = { Text("Profile") })
            }
        }
    ) { padding ->
        when (tab) {
            Tab.ALBUM -> AlbumScreen(repo, refresh, Modifier.padding(padding))
            Tab.CAMERA -> CameraScreen(repo, onSaved = { refresh++ }, Modifier.padding(padding))
            Tab.STATS -> StatsScreen(repo, Modifier.padding(padding))
            Tab.PROFILE -> ProfileScreen(repo, tokenStore, onLoggedOut = { loggedIn = false }, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AuthScreen(repo: SnapSpendRepository, onLoggedIn: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("SnapSpend", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Ghi chi tiêu bằng một tấm ảnh.", color = Color.Gray)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        if (register) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(enabled = !loading && email.isNotBlank() && password.length >= 6, onClick = {
            scope.launch {
                loading = true; error = null
                runCatching {
                    if (register) repo.register(email, username.ifBlank { email.substringBefore('@') }, password)
                    else repo.login(email, password)
                }.onSuccess { onLoggedIn(it.token) }.onFailure { error = it.message ?: "Đăng nhập thất bại" }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (register) "Tạo tài khoản" else "Đăng nhập") }
        TextButton({ register = !register }, Modifier.align(Alignment.CenterHorizontally)) {
            Text(if (register) "Đã có tài khoản? Đăng nhập" else "Chưa có tài khoản? Đăng ký")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun CameraScreen(repo: SnapSpendRepository, onSaved: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedUri by remember { mutableStateOf<Uri?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCamera = it }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { capturedUri = uri; showForm = true } }

    if (!hasCamera) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Button({ permissionLauncher.launch(Manifest.permission.CAMERA) }) { Text("Cho phép Camera") } }
        return
    }

    if (showForm) {
        ExpenseForm(repo, capturedUri, onDone = { showForm = false; capturedUri = null; onSaved() }, modifier)
        return
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx ->
            PreviewView(ctx).also { view ->
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val capture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    imageCapture = capture
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        }, modifier = Modifier.fillMaxSize())
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(28.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Album", color = Color.White) }
            IconButton(onClick = {
                val output = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(output).build()
                imageCapture?.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {}
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        capturedUri = Uri.fromFile(output); showForm = true
                    }
                })
            }, modifier = Modifier.size(78.dp).clip(RoundedCornerShape(50)).background(Color.White)) { Text("●", color = Color.Black) }
            Spacer(Modifier.width(70.dp))
        }
    }
}

@Composable
private fun ExpenseForm(repo: SnapSpendRepository, uri: Uri?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("auto") }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Chi tiêu mới", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (uri != null) AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Số tiền (VNĐ)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text("Danh mục")
        FilterChip(selected = category == "auto", onClick = { category = "auto" }, label = { Text("✨ AI tự phân loại") }, modifier = Modifier.padding(vertical = 6.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.height(250.dp), userScrollEnabled = false) {
            items(categories) { c ->
                FilterChip(selected = category == c.key, onClick = { category = c.key }, label = { Text("${c.emoji} ${c.label}") }, modifier = Modifier.padding(3.dp))
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú (tuỳ chọn)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(enabled = !loading && amount.isNotBlank(), onClick = {
            scope.launch {
                loading = true; error = null
                runCatching { repo.createExpense(uri, amount.toLong(), category, note.ifBlank { null }, LocalDate.now().toString()) }
                    .onSuccess { onDone() }.onFailure { error = it.message ?: "Không lưu được" }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Đang lưu…" else "Lưu") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun AlbumScreen(repo: SnapSpendRepository, refresh: Int, modifier: Modifier = Modifier) {
    val expenses by repo.expenses.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(refresh) { runCatching { repo.refreshExpenses() } }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Album", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Chi tiêu theo thời gian", color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(expenses) { e -> ExpenseCard(e.imageUrl, e.category, e.amount, e.expenseDate, e.note) {
                scope.launch { runCatching { repo.deleteExpense(e.id) } }
            } }
        }
    }
}

@Composable
private fun ExpenseCard(imageUrl: String?, category: String, amount: Long, date: String, note: String?, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (imageUrl != null) AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(72.dp).background(Color.LightGray), contentAlignment = Alignment.Center) { Text("—") }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(category, fontWeight = FontWeight.SemiBold)
                Text(date, color = Color.Gray)
                note?.let { Text(it, color = Color.Gray) }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatVnd(amount), fontWeight = FontWeight.Bold)
                TextButton(onDelete) { Text("Xoá") }
            }
        }
    }
}

@Composable
private fun StatsScreen(repo: SnapSpendRepository, modifier: Modifier = Modifier) {
    var stats by remember { mutableStateOf<com.snapspend.app.data.remote.StatsDto?>(null) }
    var analysis by remember { mutableStateOf<com.snapspend.app.data.remote.AnalysisDto?>(null) }
    val scope = rememberCoroutineScope()
    val from = LocalDate.now().withDayOfMonth(1).toString()
    val to = LocalDate.now().toString()
    LaunchedEffect(Unit) { stats = runCatching { repo.stats(from, to) }.getOrNull() }
    Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Thống kê", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Tháng này", color = Color.Gray)
        Text(formatVnd(stats?.total ?: 0L), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        stats?.let {
            Spacer(Modifier.height(20.dp)); Text("Chi tiêu theo ngày", fontWeight = FontWeight.Bold)
            BarChart(it.byDay, Modifier.fillMaxWidth().height(220.dp))
            Spacer(Modifier.height(20.dp)); Text("Theo danh mục", fontWeight = FontWeight.Bold)
            it.byCategory.entries.sortedByDescending { e -> e.value }.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(key); Text(formatVnd(value), fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = { scope.launch { analysis = runCatching { repo.analyze(from, to) }.getOrNull() } }, modifier = Modifier.fillMaxWidth()) { Text("AI phân tích hành vi") }
        analysis?.let {
            Spacer(Modifier.height(16.dp)); Text("AI", fontWeight = FontWeight.Bold); Text(it.summary)
            if (it.trends.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Xu hướng", fontWeight = FontWeight.SemiBold); it.trends.forEach { x -> Text("• $x") } }
            if (it.anomalies.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Bất thường", fontWeight = FontWeight.SemiBold); it.anomalies.forEach { x -> Text("• $x") } }
            if (it.recommendations.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Gợi ý", fontWeight = FontWeight.SemiBold); it.recommendations.forEach { x -> Text("• $x") } }
        }
    }
}

@Composable
private fun BarChart(data: Map<String, Long>, modifier: Modifier) {
    val values = data.toList().sortedBy { it.first }.takeLast(14)
    val max = (values.maxOfOrNull { it.second } ?: 1L).toFloat()
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val barW = size.width / values.size
        values.forEachIndexed { i, (_, value) ->
            val h = size.height * (value / max)
            drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(i * barW + barW * .18f, size.height - h), size = androidx.compose.ui.geometry.Size(barW * .64f, h))
        }
    }
}

@Composable
private fun ProfileScreen(repo: SnapSpendRepository, tokenStore: TokenStore, onLoggedOut: () -> Unit, modifier: Modifier = Modifier) {
    var friends by remember { mutableStateOf<List<com.snapspend.app.data.remote.FriendDto>>(emptyList()) }
    var username by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { friends = runCatching { repo.friends() }.getOrDefault(emptyList()) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        Text("Bạn bè", fontWeight = FontWeight.Bold)
        friends.forEach { Text("@${it.username}", modifier = Modifier.padding(vertical = 6.dp)) }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Username bạn muốn thêm") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { scope.launch { runCatching { repo.addFriend(username) }.onSuccess { friends = friends + it; username = ""; msg = "Đã gửi lời mời" }.onFailure { msg = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text("Thêm bạn") }
        msg?.let { Text(it, color = Color.Gray) }
        Spacer(Modifier.height(32.dp))
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { tokenStore.clear(); onLoggedOut() }, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Xóa tài khoản", color = MaterialTheme.colorScheme.error) }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                confirmButton = { TextButton(onClick = { scope.launch { runCatching { repo.deleteAccount() }.onSuccess { tokenStore.clear(); onLoggedOut() }.onFailure { msg = it.message }; confirmDelete = false } }) { Text("Xóa") } },
                dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
                title = { Text("Xóa tài khoản?") },
                text = { Text("Tài khoản và dữ liệu chi tiêu liên quan sẽ bị xóa khỏi server.") }
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Privacy", fontWeight = FontWeight.Bold)
        Text("Ảnh và chi tiêu được coi là riêng tư mặc định. Chỉ chia sẻ khi bạn chủ động thực hiện.", color = Color.Gray)
    }
}
