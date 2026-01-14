package com.itelly.birthdays

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.icu.util.Calendar
import android.icu.util.ChineseCalendar
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.itelly.birthdays.ui.theme.BirthdaysTheme
import java.util.*

data class BirthdayRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val month: Int,
    val day: Int,
    val isLunar: Boolean,
    val note: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BirthdaysTheme {
                BirthdayApp()
            }
        }
    }
}

@Composable
fun BirthdayApp() {
    val birthdayList = remember { mutableStateListOf<BirthdayRecord>() }
    var showDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
            syncAllToCalendar(context, birthdayList)
        } else {
            Toast.makeText(context, "需要日历权限才能同步", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加生日")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "生日列表",
                    style = MaterialTheme.typography.headlineMedium
                )
                if (birthdayList.isNotEmpty()) {
                    Button(
                        onClick = {
                            val permissions = arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                            if (permissions.all {
                                    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                                }) {
                                syncAllToCalendar(context, birthdayList)
                            } else {
                                permissionLauncher.launch(permissions)
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("同步全部")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (birthdayList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无生日记录，点击右下角添加", color = MaterialTheme.colorScheme.outline)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(birthdayList, key = { it.id }) { record ->
                        BirthdayItem(
                            record = record,
                            onDelete = { birthdayList.remove(record) }
                        )
                    }
                }
            }
        }

        if (showDialog) {
            AddBirthdayDialog(
                onDismiss = { showDialog = false },
                onConfirm = { record ->
                    birthdayList.add(record)
                    showDialog = false
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BirthdayItem(record: BirthdayRecord, onDelete: () -> Unit) {
    var showDelete by remember { mutableStateOf(false) }
    val typeColor = if (record.isLunar) Color(0xFFE91E63) else Color(0xFF2196F3) 
    val typeBg = typeColor.copy(alpha = 0.05f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { showDelete = false },
                onLongClick = { showDelete = !showDelete }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = typeBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(typeColor)
            )

            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (record.isLunar) Icons.Default.NightsStay else Icons.Default.WbSunny,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = record.name, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val displayDate = "${numberToChinese(record.month)}月${numberToChinese(record.day)}日"

                    Text(
                        text = "${if (record.isLunar) "农历" else "公历"}: $displayDate",
                        style = MaterialTheme.typography.bodyLarge,
                        color = typeColor
                    )
                    if (record.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "备注: ${record.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                
                if (showDelete) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

fun numberToChinese(num: Int): String {
    val chineseNums = arrayOf("零", "一", "二", "三", "四", "五", "六", "七", "八", "九", "十")
    return when {
        num <= 10 -> chineseNums[num]
        num < 20 -> "十${chineseNums[num % 10]}"
        num % 10 == 0 -> "${chineseNums[num / 10]}十"
        else -> "${chineseNums[num / 10]}十${chineseNums[num % 10]}"
    }
}

@Composable
fun AddBirthdayDialog(onDismiss: () -> Unit, onConfirm: (BirthdayRecord) -> Unit) {
    var name by remember { mutableStateOf("") }
    var month by remember { mutableStateOf("1") }
    var day by remember { mutableStateOf("1") }
    var isLunar by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加生日") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (!isLunar) Color(0xFF2196F3) else Color.Transparent)
                            .clickable { isLunar = false },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "公历",
                            color = if (!isLunar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(if (isLunar) Color(0xFFE91E63) else Color.Transparent)
                            .clickable { isLunar = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "农历",
                            color = if (isLunar) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it },
                        label = { Text("月") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = day,
                        onValueChange = { day = it },
                        label = { Text("日") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注 (可选)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isBlank()) {
                        Toast.makeText(context, "请输入姓名", Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    onConfirm(
                        BirthdayRecord(
                            name = name,
                            month = month.toIntOrNull() ?: 1,
                            day = day.toIntOrNull() ?: 1,
                            isLunar = isLunar,
                            note = note
                        )
                    )
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

fun syncAllToCalendar(context: android.content.Context, list: List<BirthdayRecord>) {
    // 1. 获取有效的 Calendar ID
    val calendarId = getDefaultCalendarId(context)
    if (calendarId == -1L) {
        Toast.makeText(context, "未找到系统日历，请确保已登录账户", Toast.LENGTH_LONG).show()
        return
    }

    var successCount = 0
    list.forEach { record ->
        if (syncSingleToCalendar(context, record, calendarId)) {
            successCount++
        }
    }
    Toast.makeText(context, "同步完成: 成功 $successCount/${list.size}", Toast.LENGTH_SHORT).show()
}

// 动态查询系统默认的可写日历 ID
fun getDefaultCalendarId(context: android.content.Context): Long {
    val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
    val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
    
    val cursor: Cursor? = context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        projection,
        selection,
        null,
        null
    )
    
    cursor?.use {
        if (it.moveToFirst()) {
            return it.getLong(0) // 返回第一个找到的有效日历 ID
        }
    }
    return -1L
}

fun syncSingleToCalendar(context: android.content.Context, record: BirthdayRecord, calendarId: Long): Boolean {
    try {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val eventTime = Calendar.getInstance()
        
        if (record.isLunar) {
            val cc = ChineseCalendar()
            cc.set(ChineseCalendar.MONTH, record.month - 1)
            cc.set(ChineseCalendar.DAY_OF_MONTH, record.day)
            cc.set(ChineseCalendar.EXTENDED_YEAR, currentYear + 2637)
            eventTime.timeInMillis = cc.timeInMillis
        } else {
            eventTime.set(Calendar.YEAR, currentYear)
            eventTime.set(Calendar.MONTH, record.month - 1)
            eventTime.set(Calendar.DAY_OF_MONTH, record.day)
        }

        eventTime.set(Calendar.HOUR_OF_DAY, 9)
        eventTime.set(Calendar.MINUTE, 0)
        eventTime.set(Calendar.SECOND, 0)

        val values = ContentValues().apply {
            put(CalendarContract.Events.DTSTART, eventTime.timeInMillis)
            put(CalendarContract.Events.DTEND, eventTime.timeInMillis + 60 * 60 * 1000)
            put(CalendarContract.Events.TITLE, "${record.name}的生日")
            val desc = StringBuilder()
            val dateStr = "${numberToChinese(record.month)}月${numberToChinese(record.day)}日"
            desc.append(if (record.isLunar) "农历生日: $dateStr" else "公历生日: $dateStr")
            if (record.note.isNotBlank()) {
                desc.append("\n备注: ${record.note}")
            }
            put(CalendarContract.Events.DESCRIPTION, desc.toString())
            put(CalendarContract.Events.CALENDAR_ID, calendarId) 
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
        }

        val uri: Uri? = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        return uri != null
    } catch (e: Exception) {
        return false
    }
}
