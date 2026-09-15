package com.snapspend.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavController
import com.snapspend.app.data.remote.TokenStore

enum class Tab { ALBUM, CAMERA, STATS, PROFILE }

// Cụm home: BottomBar 4 tab. Detail/form là route riêng (back stack chuẩn).
@Composable
fun HomeScreen(
    nav: NavController,
    tokenStore: TokenStore,
    tab: Tab,
    onTab: (Tab) -> Unit,
    refreshTick: Int,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snack = remember { SnackbarHostState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == Tab.ALBUM, { onTab(Tab.ALBUM) }, modifier = Modifier.testTag("tab_album"), icon = { Icon(Icons.AutoMirrored.Filled.List, "Album") }, label = { Text("Album") })
                NavigationBarItem(tab == Tab.CAMERA, { onTab(Tab.CAMERA) }, modifier = Modifier.testTag("tab_camera"), icon = { Icon(Icons.Filled.PhotoCamera, "Camera") }, label = { Text("Camera") })
                NavigationBarItem(tab == Tab.STATS, { onTab(Tab.STATS) }, modifier = Modifier.testTag("tab_stats"), icon = { Icon(Icons.Filled.BarChart, "Stats") }, label = { Text("Stats") })
                NavigationBarItem(tab == Tab.PROFILE, { onTab(Tab.PROFILE) }, modifier = Modifier.testTag("tab_profile"), icon = { Icon(Icons.Filled.Person, "Profile") }, label = { Text("Profile") })
            }
        }
    ) { padding ->
        when (tab) {
            Tab.ALBUM -> AlbumScreen(refreshTick, onOpen = { nav.navigate("detail/$it") }, snack = snack, modifier = Modifier.padding(padding))
            Tab.CAMERA -> CameraScreen(onCaptured = { uri ->
                val route = if (uri == null) "form" else "form?imageUri=${Uri.encode(uri.toString())}"
                nav.navigate(route)
            }, modifier = Modifier.padding(padding))
            Tab.STATS -> StatsScreen(modifier = Modifier.padding(padding))
            Tab.PROFILE -> ProfileScreen(tokenStore, onLoggedOut = onLoggedOut, modifier = Modifier.padding(padding))
        }
    }
}
