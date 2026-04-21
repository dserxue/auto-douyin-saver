package com.example.autodouyinsaver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.autodouyinsaver.ui.theme.AutoDouyinSaverTheme
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoDouyinSaverTheme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        HistoryManager.autoRecoverIfEmpty(context)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("⚡") },
                    label = { Text("控制中心") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Text("🕒") },
                    label = { Text("下载历史") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { innerPadding ->
        if (selectedTab == 0) {
            SettingsScreen(modifier = Modifier.padding(innerPadding))
        } else {
            HistoryScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var accessibilityGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(context, AutoDownloadAccessibilityService::class.java)) }
    
    // 每次显示时刷新权限状态
    LaunchedEffect(Unit) {
        overlayGranted = Settings.canDrawOverlays(context)
        accessibilityGranted = isAccessibilityServiceEnabled(context, AutoDownloadAccessibilityService::class.java)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("抖音短视频无水印下载", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))

        PermissionItem(
            title = "悬浮窗权限",
            description = "用于在屏幕上显示'一键下载'悬浮球",
            isGranted = overlayGranted,
            onClick = {
                if (!overlayGranted) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        PermissionItem(
            title = "无障碍服务",
            description = "用于自动点击'分享'与'复制链接'",
            isGranted = accessibilityGranted,
            onClick = {
                if (!accessibilityGranted) {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            var storageGranted by remember { mutableStateOf(android.os.Environment.isExternalStorageManager()) }
            LaunchedEffect(Unit) {
                storageGranted = android.os.Environment.isExternalStorageManager()
            }

            PermissionItem(
                title = "所有文件访问权限",
                description = "用于卸载重装后能够自动恢复历史记录",
                isGranted = storageGranted,
                onClick = {
                    if (!storageGranted) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            // Android 10 以下自动具备物理备份对应写入权限，前提是清单文件中包含存储权限
            Spacer(modifier = Modifier.height(32.dp))
        }

        Button(
            onClick = {
                overlayGranted = Settings.canDrawOverlays(context)
                accessibilityGranted = isAccessibilityServiceEnabled(context, AutoDownloadAccessibilityService::class.java)
                
                if (overlayGranted && accessibilityGranted) {
                    val serviceIntent = Intent(context, FloatingWindowService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            },
            enabled = overlayGranted && accessibilityGranted
        ) {
            Text(if (overlayGranted && accessibilityGranted) "启动悬浮窗" else "请先授予必要权限")
        }
    }
}

// 单条操作事件枚举
enum class ItemMenuAction { OPEN, DELETE, RENAME, OPEN_DOWNLOAD_PAGE }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var historyList by remember { mutableStateOf(HistoryManager.getHistoryList(context)) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // 批量删除对话框
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var deletePhysicalFile by remember { mutableStateOf(false) }

    // 单条删除对话框
    var itemToDelete by remember { mutableStateOf<DownloadHistory?>(null) }
    var deleteSinglePhysicalFile by remember { mutableStateOf(false) }

    // 重命名对话框
    var itemToRename by remember { mutableStateOf<DownloadHistory?>(null) }
    var renameText by remember { mutableStateOf("") }

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("成功/进行中", "失败记录")

    // 刷新列表的快捷函数
    fun refresh() { historyList = HistoryManager.getHistoryList(context) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                historyList = HistoryManager.getHistoryList(context)
                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                val runningItems = historyList.filter { it.status == "下载中" }
                var changed = false
                for (item in runningItems) {
                    if (item.id != -1L) {
                        val query = android.app.DownloadManager.Query().setFilterById(item.id)
                        val cursor = dm.query(query)
                        if (cursor != null && cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                            if (statusIndex >= 0) {
                                val status = cursor.getInt(statusIndex)
                                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                                    HistoryManager.updateStatus(context, item.id, "已下载")
                                    changed = true
                                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                                    HistoryManager.updateStatus(context, item.id, "下载失败")
                                    changed = true
                                }
                            }
                            cursor.close()
                        }
                    }
                }
                if (changed) { historyList = HistoryManager.getHistoryList(context) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // ─── 批量删除对话框 ───
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("将删除 ${selectedIds.size} 条历史记录，此操作不可逆转。")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deletePhysicalFile, onCheckedChange = { deletePhysicalFile = it })
                        Text("同时从相册中彻底删除实际视频文件？", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    HistoryManager.removeHistories(context, selectedIds.toList(), deletePhysicalFile)
                    refresh(); isSelectionMode = false; selectedIds = setOf()
                    showBatchDeleteDialog = false; deletePhysicalFile = false
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }

    // ─── 单条删除对话框 ───
    itemToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除「${target.title}」的历史记录吗？")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = deleteSinglePhysicalFile, onCheckedChange = { deleteSinglePhysicalFile = it })
                        Text("同时从相册中彻底删除实际视频文件？", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    HistoryManager.removeHistories(context, listOf(target.id), deleteSinglePhysicalFile)
                    refresh(); itemToDelete = null; deleteSinglePhysicalFile = false
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { itemToDelete = null }) { Text("取消") } }
        )
    }

    // ─── 重命名对话框 ───
    itemToRename?.let { target ->
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("重命名") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("新文件名（不含扩展名）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameText.trim()
                        if (trimmed.isNotEmpty()) {
                            HistoryManager.renameHistory(context, target.id, trimmed)
                            refresh()
                        }
                        itemToRename = null
                    },
                    enabled = renameText.trim().isNotEmpty()
                ) { Text("确认") }
            },
            dismissButton = { TextButton(onClick = { itemToRename = null }) { Text("取消") } }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!isSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("下载历史", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = {
                    if (historyList.isNotEmpty()) {
                        isSelectionMode = true
                        selectedIds = historyList.map { it.id }.toSet()
                    }
                }) { Text("管理全部") }
            }
            PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(text = { Text(title) }, selected = selectedTabIndex == index, onClick = { selectedTabIndex = index })
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("已选择 ${selectedIds.size} 项", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = {
                        val currentFiltered = if (selectedTabIndex == 0) historyList.filter { !it.status.contains("失败") }
                        else historyList.filter { it.status.contains("失败") }
                        selectedIds = currentFiltered.map { it.id }.toSet()
                    }) { Text("全选本页") }
                    TextButton(onClick = { isSelectionMode = false; selectedIds = setOf() }) { Text("取消") }
                }
            }
        }

        val filteredList = if (selectedTabIndex == 0) historyList.filter { !it.status.contains("失败") }
        else historyList.filter { it.status.contains("失败") }

        if (filteredList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无对应的历史记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp)
                ) {
                    items(filteredList) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        HistoryItemCard(
                            item = item,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
                            onItemClick = {
                                if (isSelectionMode) {
                                    val newSet = selectedIds.toMutableSet()
                                    if (isSelected) newSet.remove(item.id) else newSet.add(item.id)
                                    selectedIds = newSet
                                } else {
                                    // 点击卡片 → 打开视频
                                    if (item.status == "已下载" && item.id != -1L) {
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        val uri = dm.getUriForDownloadedFile(item.id)
                                        if (uri != null) {
                                            val mimeType = dm.getMimeTypeForDownloadedFile(item.id) ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try { context.startActivity(intent) } catch (e: Exception) {
                                                Toast.makeText(context, "找不到可以打开该文件的应用", Toast.LENGTH_SHORT).show()
                                                e.printStackTrace()
                                            }
                                        } else {
                                            Toast.makeText(context, "物理文件已被移动或移除", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "无法播放（${item.status}）", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            onItemLongClick = {
                                if (!isSelectionMode) { isSelectionMode = true; selectedIds = setOf(item.id) }
                            },
                            onMenuAction = { action ->
                                when (action) {
                                    ItemMenuAction.OPEN -> {
                                    if (item.status == "已下载" && item.id != -1L) {
                                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                        val uri = dm.getUriForDownloadedFile(item.id)
                                        if (uri != null) {
                                            val mimeType = dm.getMimeTypeForDownloadedFile(item.id) ?: "*/*"
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, mimeType)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            try { context.startActivity(intent) } catch (e: Exception) {
                                                Toast.makeText(context, "找不到可以打开该文件的应用", Toast.LENGTH_SHORT).show()
                                                e.printStackTrace()
                                            }
                                        } else {
                                            Toast.makeText(context, "物理文件已被移动或移除", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "无法打开（${item.status}）", Toast.LENGTH_SHORT).show()
                                    }
                                    }
                                    ItemMenuAction.DELETE -> { itemToDelete = item }
                                    ItemMenuAction.RENAME -> {
                                        renameText = item.title
                                        itemToRename = item
                                    }
                                    ItemMenuAction.OPEN_DOWNLOAD_PAGE -> {
                                        // 提取并打开原始链接
                                        try {
                                            val text = item.originUrl
                                            val matcher = java.util.regex.Pattern.compile("https?://[-A-Za-z0-9+&@#/%?=~_|!:,.;]+[-A-Za-z0-9+&@#/%=~_|]").matcher(text)
                                            if (matcher.find()) {
                                                val urlString = matcher.group()
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlString))
                                                context.startActivity(intent)
                                            } else {
                                                Toast.makeText(context, "未找到有效的链接", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "无法打开原链接", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                if (isSelectionMode) {
                    Button(
                        onClick = { if (selectedIds.isNotEmpty()) showBatchDeleteDialog = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除所选") }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HistoryItemCard(
    item: DownloadHistory,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onMenuAction: (ItemMenuAction) -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧封面缩略图
            Box(
                modifier = Modifier
                    .size(100.dp, 64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                if (item.coverUrl.isNotEmpty()) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = "Cover Image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // 中间信息区
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = if (item.title.endsWith(".mp4")) item.title else "${item.title}.mp4",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val statusColor = if (item.status == "已下载") Color.Gray
                    else if (item.status.contains("失败")) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.secondary
                Text(
                    text = "${item.status}  ${sdf.format(Date(item.timestamp))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusColor
                )
                Spacer(modifier = Modifier.height(2.dp))
                val durationSec = item.durationMs / 1000
                Text(
                    text = "媒体时长：${String.format("%02d:%02d", durationSec / 60, durationSec % 60)}",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = "任务类型：流媒体提取", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }

            // 右侧三点菜单
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "更多操作", tint = Color.Gray)
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("打开") },
                        leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = { menuExpanded = false; onMenuAction(ItemMenuAction.OPEN) }
                    )
                    DropdownMenuItem(
                        text = { Text("重命名") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuExpanded = false; onMenuAction(ItemMenuAction.RENAME) }
                    )
                    DropdownMenuItem(
                        text = { Text("打开原链接") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        onClick = { menuExpanded = false; onMenuAction(ItemMenuAction.OPEN_DOWNLOAD_PAGE) }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onMenuAction(ItemMenuAction.DELETE) }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = if (isGranted) "已授权" else "去授权",
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

fun isAccessibilityServiceEnabled(context: Context, accessibilityService: Class<*>): Boolean {
    var accessibilityEnabled = 0
    val service = context.packageName + "/" + accessibilityService.canonicalName
    try {
        accessibilityEnabled = Settings.Secure.getInt(
            context.applicationContext.contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED
        )
    } catch (e: Settings.SettingNotFoundException) { }
    val mStringColonSplitter = TextUtils.SimpleStringSplitter(':')
    if (accessibilityEnabled == 1) {
        val settingValue = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        if (settingValue != null) {
            mStringColonSplitter.setString(settingValue)
            while (mStringColonSplitter.hasNext()) {
                val accessibilityServiceStr = mStringColonSplitter.next()
                if (accessibilityServiceStr.equals(service, ignoreCase = true)) {
                    return true
                }
            }
        }
    }
    return false
}