package com.snapspend.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.snapspend.app.data.local.AppDatabase
import com.snapspend.app.data.remote.TokenStore
import com.snapspend.app.data.remote.createApi
import com.snapspend.app.data.repository.RealRepository
import com.snapspend.app.data.repository.SnapSpendRepository
import com.snapspend.app.ui.screens.AuthScreen
import com.snapspend.app.ui.screens.DetailScreen
import com.snapspend.app.ui.screens.FormScreen
import com.snapspend.app.ui.screens.HomeScreen
import com.snapspend.app.ui.screens.Tab
import com.snapspend.app.ui.theme.SnapSpendTheme
import com.snapspend.app.ui.viewmodel.LocalVmFactory
import com.snapspend.app.ui.viewmodel.SnapSpendViewModelFactory

// Điểm vào: luôn dùng backend thật (RealRepository).
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokenStore = TokenStore(applicationContext)
        val repo: SnapSpendRepository =
            RealRepository(applicationContext, AppDatabase.create(applicationContext), createApi(tokenStore))
        setContent {
            SnapSpendTheme {
                CompositionLocalProvider(LocalVmFactory provides SnapSpendViewModelFactory(repo)) {
                    // Cho phép UI test (Appium/uiautomator2) đọc Modifier.testTag qua resource-id.
                    Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        RootNav(tokenStore)
                    }
                }
            }
        }
    }
}

// Route: auth, home (4 tab), detail/{id}, form. Logout xóa sạch stack về auth.
@Composable
fun RootNav(tokenStore: TokenStore) {
    val nav = rememberNavController()
    var tab by rememberSaveable { mutableStateOf(Tab.CAMERA) }
    var refreshTick by rememberSaveable { mutableIntStateOf(0) }
    val unauthorized by tokenStore.unauthorized.collectAsStateWithLifecycle()
    // Server trả 401 (token sai/hết hạn) -> điều hướng về Auth, không crash.
    LaunchedEffect(unauthorized) {
        if (unauthorized) {
            tokenStore.consumeUnauthorized()
            nav.navigate("auth") { popUpTo(0) { inclusive = true } }
        }
    }
    NavHost(nav, startDestination = if (tokenStore.token != null) "home" else "auth") {
        composable("auth") {
            AuthScreen(onLoggedIn = {
                tokenStore.token = it
                nav.navigate("home") { popUpTo("auth") { inclusive = true } }
            })
        }
        composable("home") {
            HomeScreen(
                nav = nav,
                tokenStore = tokenStore,
                tab = tab,
                onTab = { tab = it },
                refreshTick = refreshTick,
                onLoggedOut = {
                    tokenStore.clear()
                    nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                }
            )
        }
        composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
            DetailScreen(entry.arguments?.getLong("id") ?: -1, onBack = { nav.popBackStack() }, onChanged = { refreshTick++ })
        }
        composable(
            "form?imageUri={imageUri}",
            arguments = listOf(navArgument("imageUri") { nullable = true; defaultValue = null })
        ) { entry ->
            val uri = entry.arguments?.getString("imageUri")?.let { Uri.parse(it) }
            FormScreen(
                uri = uri,
                onDone = { refreshTick++; tab = Tab.ALBUM; nav.popBackStack() },
                onBack = { nav.popBackStack() }
            )
        }
    }
}
