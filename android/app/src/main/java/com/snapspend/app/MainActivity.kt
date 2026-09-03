package com.snapspend.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.snapspend.app.data.AppConfig
import com.snapspend.app.data.local.AppDatabase
import com.snapspend.app.data.remote.AnalysisDto
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.data.remote.FriendDto
import com.snapspend.app.data.remote.StatsDto
import com.snapspend.app.data.remote.TokenStore
import com.snapspend.app.data.remote.createApi
import com.snapspend.app.data.repository.MockRepository
import com.snapspend.app.data.repository.RealRepository
import com.snapspend.app.data.repository.SnapSpendRepository
import com.snapspend.app.model.categories
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.BarChartV2
import com.snapspend.app.ui.components.BulletList
import com.snapspend.app.ui.components.CategoryChip
import com.snapspend.app.ui.components.CategoryLabel
import com.snapspend.app.ui.components.CategoryShareRow
import com.snapspend.app.ui.components.ConfidenceBadge
import com.snapspend.app.ui.components.EmptyState
import com.snapspend.app.ui.components.ErrorRetry
import com.snapspend.app.ui.components.LoadingBox
import com.snapspend.app.ui.components.SectionCard
import com.snapspend.app.ui.format.formatVnd
import com.snapspend.app.ui.screens.DetailScreen
import com.snapspend.app.ui.theme.SnapSpendTheme
import com.snapspend.app.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
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
        setContent { SnapSpendTheme { SnapSpendApp(repo, tokenStore) } }
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
    var refreshTick by remember { mutableIntStateOf(0) }
    var openedId by remember { mutableStateOf<Long?>(null) }
    val snack = remember { SnackbarHostState() }
    val expenses by repo.expenses.collectAsState(initial = emptyList())
    var friends by remember { mutableStateOf<List<FriendDto>>(emptyList()) }
    LaunchedEffect(loggedIn) { friends = runCatching { repo.friends() }.getOrDefault(emptyList()) }

    openedId?.let { id ->
        expenses.find { it.id == id }?.let { e ->
            DetailScreen(repo, e, friends, onBack = { openedId = null }, onChanged = { refreshTick++ })
            return
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.ALBUM, { tab = Tab.ALBUM }, icon = { Icon(Icons.AutoMirrored.Filled.List, "Album") }, label = { Text("Album") })
                NavigationBarItem(tab == Tab.CAMERA, { tab = Tab.CAMERA }, icon = { Icon(Icons.Filled.PhotoCamera, "Camera") }, label = { Text("Camera") })
                NavigationBarItem(tab == Tab.STATS, { tab = Tab.STATS }, icon = { Icon(Icons.Filled.BarChart, "Stats") }, label = { Text("Stats") })
                NavigationBarItem(tab == Tab.PROFILE, { tab = Tab.PROFILE }, icon = { Icon(Icons.Filled.Person, "Profile") }, label = { Text("Profile") })
            }
        }
    ) { padding ->
        when (tab) {
            Tab.ALBUM -> AlbumScreen(repo, refreshTick, onOpen = { openedId = it }, snack = snack, modifier = Modifier.padding(padding))
            Tab.CAMERA -> CameraScreen(repo, onSaved = { refreshTick++; tab = Tab.ALBUM }, modifier = Modifier.padding(padding))
            Tab.STATS -> StatsScreen(repo, modifier = Modifier.padding(padding))
            Tab.PROFILE -> ProfileScreen(repo, tokenStore, onLoggedOut = { loggedIn = false }, modifier = Modifier.padding(padding))
        }
    }
}

@Composable
private fun AuthScreen(repo: SnapSpendRepository, onLoggedIn: (String) -> Unit) {
    var register by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val emailOk = email.contains("@")
    val canSubmit = !loading && emailOk && password.length >= 6 && (!register || username.isNotBlank())

    Column(Modifier.fillMaxSize().padding(Spacing.s24), verticalArrangement = Arrangement.Center) {
        Text("SnapSpend", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text("Ghi chi tiêu bằng một tấm ảnh.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (AppConfig.isDemo) {
            Spacer(Modifier.height(Spacing.s8))
            Text("Chế độ Demo — nhập bất kỳ email hợp lệ là vào được.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(Spacing.s32))
        OutlinedTextField(email, { email = it; error = null }, label = { Text("Email") }, isError = email.isNotBlank() && !emailOk, supportingText = { if (email.isNotBlank() && !emailOk) Text("Email phải có @") }, modifier = Modifier.fillMaxWidth())
        if (register) {
            Spacer(Modifier.height(Spacing.s8))
            OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(Spacing.s8))
        OutlinedTextField(
            password, { password = it; error = null }, label = { Text("Mật khẩu (≥ 6 ký tự)") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton(onClick = { showPass = !showPass }) { Icon(if (showPass) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Hiện/ẩn") } },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(Spacing.s16))
        Button(enabled = canSubmit, onClick = {
            scope.launch {
                loading = true; error = null
                runCatching {
                    if (register) repo.register(email, username.ifBlank { email.substringBefore('@') }, password)
                    else repo.login(email, password)
                }.onSuccess { onLoggedIn(it.token) }.onFailure { error = it.message ?: "Đăng nhập thất bại" }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(if (register) "Tạo tài khoản" else "Đăng nhập")
        }
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

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface)) {
        androidx.compose.ui.viewinterop.AndroidView(factory = { ctx ->
            androidx.camera.view.PreviewView(ctx).also { view ->
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
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(Spacing.s24), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Album", color = MaterialTheme.colorScheme.surface) }
                TextButton(onClick = {
                    capturedUri = createSampleImage(context); showForm = true
                }) { Text("Ảnh mẫu", color = MaterialTheme.colorScheme.surface) }
            }
            IconButton(onClick = {
                val output = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                val options = ImageCapture.OutputFileOptions.Builder(output).build()
                imageCapture?.takePicture(options, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {}
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        capturedUri = Uri.fromFile(output); showForm = true
                    }
                })
            }, modifier = Modifier.size(78.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surface)) {
                Text("●", color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(70.dp))
        }
    }
}

private fun createSampleImage(context: android.content.Context): Uri {
    val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    canvas.drawColor(AndroidColor.LTGRAY)
    val paint = Paint().apply { color = AndroidColor.DKGRAY; textSize = 48f }
    canvas.drawText("SnapSpend sample", 180f, 300f, paint)
    val f = File(context.cacheDir, "sample_${System.currentTimeMillis()}.jpg")
    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    return Uri.fromFile(f)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseForm(repo: SnapSpendRepository, uri: Uri?, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("auto") }
    var note by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val amountOk = (amount.toLongOrNull() ?: 0L) > 0

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s16)) {
        Text("Chi tiêu mới", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s12))
        if (uri != null) AsyncImage(model = uri, contentDescription = null, modifier = Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(18.dp)), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(Spacing.s16))
        OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("Số tiền (VNĐ)") }, isError = amount.isNotBlank() && !amountOk, supportingText = { if (amount.isNotBlank() && !amountOk) Text("Số tiền phải lớn hơn 0") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.s12))
        Text("Danh mục", fontWeight = FontWeight.SemiBold)
        FlowRow(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), horizontalArrangement = Arrangement.spacedBy(Spacing.s8), verticalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            FilterChip(selected = category == "auto", onClick = { category = "auto" }, label = { Text("✨ AI tự phân loại") })
            categories.forEach { c ->
                FilterChip(selected = category == c.key, onClick = { category = c.key }, label = { Text("${c.emoji} ${c.label}") })
            }
        }
        OutlinedTextField(note, { note = it }, label = { Text("Ghi chú (tuỳ chọn)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.s16))
        Button(enabled = !loading && amountOk, onClick = {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlbumScreen(repo: SnapSpendRepository, refreshTick: Int, onOpen: (Long) -> Unit, snack: SnackbarHostState, modifier: Modifier = Modifier) {
    val expenses by repo.expenses.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var filterCat by remember { mutableStateOf<String?>(null) }
    var sortDesc by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var lastDeleted by remember { mutableStateOf<ExpenseDto?>(null) }

    LaunchedEffect(refreshTick) {
        refreshing = true; loadError = null
        runCatching { repo.refreshExpenses() }.onFailure { loadError = it.message ?: "Tải thất bại" }
        refreshing = false
    }

    val visible = expenses
        .filter { (query.isBlank() || (it.note ?: "").contains(query, ignoreCase = true)) && (filterCat == null || it.category == filterCat) }
        .let { if (sortDesc) it.sortedWith(compareByDescending<ExpenseDto> { it.expenseDate }.thenByDescending { it.id }) else it.sortedWith(compareBy<ExpenseDto> { it.expenseDate }.thenBy { it.id }) }

    fun deleteWithUndo(e: ExpenseDto) {
        scope.launch {
            runCatching { repo.deleteExpense(e.id) }.onSuccess {
                lastDeleted = e
                val r = snack.showSnackbar("Đã xóa ${formatVnd(e.amount)}", actionLabel = "Hoàn tác", duration = SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) {
                    lastDeleted?.let { d -> runCatching { repo.createExpense(null, d.amount, d.category, d.note, d.expenseDate) } }
                    lastDeleted = null
                }
            }.onFailure { snack.showSnackbar(it.message ?: "Xóa thất bại") }
        }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(Spacing.s16)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Album", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${visible.size} khoản chi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = { sortDesc = !sortDesc }) { Icon(Icons.Filled.Refresh, if (sortDesc) "Mới nhất" else "Cũ nhất") }
                }
            }
            Spacer(Modifier.height(Spacing.s8))
            OutlinedTextField(query, { query = it }, label = { Text("Tìm kiếm ghi chú…") }, leadingIcon = { Icon(Icons.Filled.Search, "Tìm") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Spacing.s8))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                item { FilterChip(selected = filterCat == null, onClick = { filterCat = null }, label = { Text("Tất cả") }) }
                items(categories) { c -> FilterChip(selected = filterCat == c.key, onClick = { filterCat = if (filterCat == c.key) null else c.key }, label = { Text("${c.emoji} ${c.label}") }) }
            }
        }

        loadError?.let { msg ->
            ErrorRetry(msg) {
                scope.launch {
                    refreshing = true; loadError = null
                    runCatching { repo.refreshExpenses() }.onFailure { loadError = it.message }
                    refreshing = false
                }
            }
        }

        if (!refreshing && visible.isEmpty() && loadError == null) {
            EmptyState("Chưa có chi tiêu", "Chụp ảnh món đầu tiên ở tab Camera nhé.", "Mở Camera", null)
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true; loadError = null
                        runCatching { repo.refreshExpenses() }.onFailure { loadError = it.message }
                        refreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    items(visible, key = { it.id }) { e ->
                        val dismissState = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            if (v == SwipeToDismissBoxValue.EndToStart) { deleteWithUndo(e); true } else false
                        })
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.error), contentAlignment = Alignment.CenterEnd) {
                                    Text("Xóa  ", color = MaterialTheme.colorScheme.surface, fontWeight = FontWeight.Bold)
                                }
                            }
                        ) {
                            ExpenseCard(e, onClick = { onOpen(e.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseCard(e: ExpenseDto, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.padding(Spacing.s12), verticalAlignment = Alignment.CenterVertically) {
            if (e.imageUrl != null) AsyncImage(model = e.imageUrl, contentDescription = null, modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
            else Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outline), contentAlignment = Alignment.Center) { Text("🧾") }
            Spacer(Modifier.width(Spacing.s12))
            Column(Modifier.weight(1f)) {
                CategoryLabel(e.category)
                Text(e.expenseDate, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                e.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                ConfidenceBadge(e.aiConfidence)
            }
            AmountText(e.amount)
        }
    }
}

@Composable
private fun StatsScreen(repo: SnapSpendRepository, modifier: Modifier = Modifier) {
    var range by remember { mutableStateOf("30D") }
    var stats by remember { mutableStateOf<StatsDto?>(null) }
    var analysis by remember { mutableStateOf<AnalysisDto?>(null) }
    var loading by remember { mutableStateOf(false) }
    var analyzing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val now = LocalDate.now()
    val from = when (range) { "7D" -> now.minusDays(6).toString(); "Tháng này" -> now.withDayOfMonth(1).toString(); else -> now.minusDays(29).toString() }
    val to = now.toString()

    LaunchedEffect(range) {
        loading = true; analysis = null
        stats = runCatching { repo.stats(from, to) }.getOrNull()
        loading = false
    }

    Column(modifier.fillMaxSize().padding(Spacing.s16).verticalScroll(rememberScrollState())) {
        Text("Thống kê", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s8))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
            listOf("7D", "30D", "Tháng này").forEach { r ->
                FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r) })
            }
        }
        Spacer(Modifier.height(Spacing.s12))
        if (loading) { LoadingBox(); return@Column }
        if (stats == null) { ErrorRetry("Không tải được thống kê") { range = range }; return@Column }
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
        Button(enabled = !analyzing, onClick = {
            scope.launch { analyzing = true; analysis = runCatching { repo.analyze(from, to) }.getOrNull(); analyzing = false }
        }, modifier = Modifier.fillMaxWidth()) { Text(if (analyzing) "AI đang phân tích…" else "AI phân tích hành vi") }
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

@Composable
private fun ProfileScreen(repo: SnapSpendRepository, tokenStore: TokenStore, onLoggedOut: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendDto>>(emptyList()) }
    var username by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        friends = runCatching { repo.friends() }.getOrDefault(emptyList())
    }
    val expenses by repo.expenses.collectAsState(initial = emptyList())
    val newest = expenses.firstOrNull()

    Column(modifier.fillMaxSize().padding(Spacing.s16).verticalScroll(rememberScrollState())) {
        Text("Profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Spacing.s24))
        SectionCard("Chế độ dữ liệu") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Chế độ Demo (Mock)", fontWeight = FontWeight.SemiBold)
                    Text(if (AppConfig.isDemo) "Đang dùng data mẫu, không cần mạng." else "Đang dùng backend thật.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = AppConfig.isDemo, onCheckedChange = {
                    AppConfig.isDemo = it
                    (context as? Activity)?.recreate()
                })
            }
        }
        Spacer(Modifier.height(Spacing.s16))
        Text("Bạn bè (${friends.size})", fontWeight = FontWeight.Bold)
        friends.forEach { f ->
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.s8), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text(f.username.firstOrNull()?.uppercase() ?: "?", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(Spacing.s12))
                Text("@${f.username}", modifier = Modifier.weight(1f))
                if (newest != null) {
                    IconButton(onClick = {
                        scope.launch {
                            runCatching { repo.shareExpense(newest.id, f.id) }
                                .onSuccess { msg = "Đã chia sẻ ${formatVnd(newest.amount)} với @${f.username}" }
                                .onFailure { msg = it.message }
                        }
                    }) { Icon(Icons.Filled.Share, "Chia sẻ khoản mới nhất") }
                }
            }
        }
        Spacer(Modifier.height(Spacing.s8))
        OutlinedTextField(username, { username = it }, label = { Text("Username bạn muốn thêm") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Spacing.s8))
        Button(onClick = {
            scope.launch {
                runCatching { repo.addFriend(username) }
                    .onSuccess { friends = friends + it; username = ""; msg = "Đã thêm @${it.username}" }
                    .onFailure { msg = it.message }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Thêm bạn") }
        msg?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Spacer(Modifier.height(Spacing.s32))
        OutlinedButton(onClick = { tokenStore.clear(); onLoggedOut() }, modifier = Modifier.fillMaxWidth()) { Text("Đăng xuất") }
        Spacer(Modifier.height(Spacing.s8))
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Xóa tài khoản", color = MaterialTheme.colorScheme.error) }
        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                confirmButton = { TextButton(onClick = { scope.launch { runCatching { repo.deleteAccount() }.onSuccess { tokenStore.clear(); onLoggedOut() }.onFailure { msg = it.message }; confirmDelete = false } }) { Text("Xóa", color = MaterialTheme.colorScheme.error) } },
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
