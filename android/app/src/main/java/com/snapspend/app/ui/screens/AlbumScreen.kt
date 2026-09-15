package com.snapspend.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.snapspend.app.data.remote.ExpenseDto
import com.snapspend.app.ui.components.AmountText
import com.snapspend.app.ui.components.CategoryLabel
import com.snapspend.app.ui.components.ConfidenceBadge
import com.snapspend.app.ui.components.EmptyState
import com.snapspend.app.ui.components.ErrorRetry
import com.snapspend.app.ui.format.formatVnd
import com.snapspend.app.ui.theme.Spacing
import com.snapspend.app.ui.viewmodel.AlbumViewModel
import com.snapspend.app.ui.viewmodel.CategoryViewModel
import com.snapspend.app.ui.viewmodel.LocalVmFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(refreshTick: Int, onOpen: (Long) -> Unit, snack: SnackbarHostState, modifier: Modifier = Modifier) {
    val vm: AlbumViewModel = viewModel(factory = LocalVmFactory.current)
    val catVm: CategoryViewModel = viewModel(factory = LocalVmFactory.current)
    val cats by catVm.categories.collectAsStateWithLifecycle()
    val visible by vm.visible.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filterCat by vm.filterCat.collectAsStateWithLifecycle()
    val sortDesc by vm.sortDesc.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val loadError by vm.loadError.collectAsStateWithLifecycle()
    val lastDeleted by vm.lastDeleted.collectAsStateWithLifecycle()

    LaunchedEffect(refreshTick) { vm.refresh() }

    // Snackbar hoàn tác: hiện khi vừa xóa, ẩn khi đã xử lý.
    LaunchedEffect(lastDeleted) {
        val d = lastDeleted ?: return@LaunchedEffect
        val r = snack.showSnackbar("Đã xóa ${formatVnd(d.amount)}", actionLabel = "Hoàn tác", duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.clearUndo()
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(Spacing.s16)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Album", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("${visible.size} khoản chi", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row {
                    IconButton(onClick = vm::toggleSort) { Icon(Icons.Filled.Refresh, if (sortDesc) "Mới nhất" else "Cũ nhất") }
                }
            }
            Spacer(Modifier.height(Spacing.s8))
            OutlinedTextField(query, vm::onQuery, label = { Text("Tìm kiếm ghi chú…") }, leadingIcon = { Icon(Icons.Filled.Search, "Tìm") }, modifier = Modifier.fillMaxWidth().testTag("field_search"))
            Spacer(Modifier.height(Spacing.s8))
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s8)) {
                item { FilterChip(selected = filterCat == null, onClick = { vm.onFilter(null) }, label = { Text("Tất cả") }) }
                items(cats, key = { it.key }) { c -> FilterChip(selected = filterCat == c.key, onClick = { vm.onFilter(if (filterCat == c.key) null else c.key) }, label = { Text("${c.emoji} ${c.name}") }) }
            }
        }

        loadError?.let { msg -> ErrorRetry(msg) { vm.refresh() } }

        if (!refreshing && visible.isEmpty() && loadError == null) {
            EmptyState("Chưa có chi tiêu", "Chụp ảnh món đầu tiên ở tab Camera nhé.", "Mở Camera", null)
        } else {
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(Modifier.fillMaxSize().padding(horizontal = Spacing.s16), verticalArrangement = Arrangement.spacedBy(Spacing.s12)) {
                    items(visible, key = { it.id }) { e ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        // Chỉ xóa khi đã settle sang EndToStart (tránh gọi trùng khi đang kéo).
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) vm.delete(e)
                        }
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
