package cn.aurange.schedule

import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

@Composable fun SettingsScreen(state: AppState, model: ScheduleViewModel, permissionVersion: Int, askNotifications: () -> Unit, issueModel: IssueCaptureViewModel) {
    val context = LocalContext.current
    val scheduler = remember { ReminderScheduler(context) }
    val notificationEnabled = remember(permissionVersion) { scheduler.canNotify() }
    val summaryEnabled = remember(permissionVersion) { scheduler.canNotify(ReminderScheduler.SUMMARY_CHANNEL) }
    val exactAllowed = remember(permissionVersion) { scheduler.exactAllowed() }
    val battery = remember(permissionVersion) { ExternalLinks.batteryUnrestricted(context) }
    var chooseLead by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 60.dp, bottom = floatingContentBottom()),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("偏好设置", style = MaterialTheme.typography.headlineMedium)
            Text("管理提醒和系统权限", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        }
        item { WhiteCard { IssueCaptureScreen(issueModel, configureOnly = true) } }
        item {
            WhiteCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(42.dp).background(Lavender, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { AppIcon(Glyph.Bell, tint = Blue) }
                    Column(Modifier.weight(1f)) {
                        Text("日程提醒", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (state.settings.enabled) "每日汇总与任务时间提醒" else "当前已暂停", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                    Switch(checked = state.settings.enabled, onCheckedChange = { model.settings(state.settings.copy(enabled = it)) })
                }
                HorizontalDivider(color = Line)
                ActionRow(Glyph.Sun, "每日汇总", "当天待办、逾期与持续跟进", state.settings.summaryTime.toString()) {
                    TimePickerDialog(context, { _, hour, minute -> model.settings(state.settings.copy(summaryTime = LocalTime.of(hour, minute))) },
                        state.settings.summaryTime.hour, state.settings.summaryTime.minute, true).show()
                }
                ActionRow(Glyph.Clock, "任务提前提醒", "用于有明确时刻的任务", if (state.settings.leadMinutes == 0) "准时" else "${state.settings.leadMinutes} 分钟") { chooseLead = true }
            }
        }
        item { SectionTitle("通知与后台") }
        item {
            WhiteCard {
                ActionRow(Glyph.Bell, "通知权限", "允许发送任务通知", if (notificationEnabled) "已开启" else "去开启", askNotifications)
                HorizontalDivider(color = Line, modifier = Modifier.padding(start = 47.dp))
                ActionRow(Glyph.CalendarCheck, "每日汇总通知", "声音、横幅与锁屏显示", if (summaryEnabled) "已开启" else "去开启") { ExternalLinks.notifications(context) }
                HorizontalDivider(color = Line, modifier = Modifier.padding(start = 47.dp))
                ActionRow(Glyph.Zap, "精准提醒", if (exactAllowed) "按计划时刻触发" else "未授权时提醒可能延迟", if (exactAllowed) "已允许" else "去授权") { ExternalLinks.exactAlarms(context) }
                HorizontalDivider(color = Line, modifier = Modifier.padding(start = 47.dp))
                ActionRow(Glyph.Phone, "小米后台自启动", "在系统列表中开启「营期日程」") {
                    ExternalLinks.autostart(context)
                    model.message("若未进入自启动列表：设置 → 应用设置 → 权限管理 → 自启动管理，开启「营期日程」。")
                }
                HorizontalDivider(color = Line, modifier = Modifier.padding(start = 47.dp))
                ActionRow(Glyph.Battery, "电池与后台运行", "在应用省电策略中选择「无限制」", if (battery) "无限制" else "查看") { ExternalLinks.battery(context) }
                OutlinedButton(onClick = {
                    if (!scheduler.test()) { model.message("请先开启任务通知权限，并检查系统通知类别。"); askNotifications() }
                    else model.message("测试通知已发送，请查看通知栏。")
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    AppIcon(Glyph.Bell, tint = Blue, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text("发送测试通知", fontSize = 12.sp)
                }
            }
        }
        item { SectionTitle("账号与同步") }
        item {
            WhiteCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(if (state.demo) Glyph.Sparkles else Glyph.Shield, tint = Sage)
                    Column {
                        Text(if (state.demo) "演示预览" else if (state.loggedIn) "已登录团队账号" else "未登录团队账号", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (state.demo) "示例数据，仅供浏览" else if (state.loggedIn) state.email else "随手记可独立使用，日程需要团队权限。", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Text("已同步日程可离线查看和提醒。云端改动需联网同步后生效，打开应用可立即刷新。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                Text("手机重启后会恢复提醒。系统强行停止应用后，请重新打开一次。", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
                if (state.loggedIn || state.demo) ActionRow(Glyph.Logout, if (state.demo) "退出演示，登录账号" else "退出登录") { if (!state.busy) confirmLogout = true }
            }
        }
        item {
            Text("本应用使用 MiSans 字体 · 英文点缀 Lora", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Text("模糊时间的任务会进入每日汇总，可在任务详情中单独设定提醒。所有日程以北京时间为准。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("营期日程 ${BuildConfig.VERSION_NAME}", fontSize = 12.sp, color = Muted)
                TextButton(onClick = { showLicenses = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("字体与组件许可", color = Muted, fontSize = 12.sp) }
            }
        }
    }
    if (chooseLead) AlertDialog(onDismissRequest = { chooseLead = false }, title = { Text("任务提前提醒") }, text = {
        Column {
            for (minutes in listOf(0, 5, 15, 30, 60)) Row(Modifier.fillMaxWidth().clickable {
                model.settings(state.settings.copy(leadMinutes = minutes)); chooseLead = false
            }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = state.settings.leadMinutes == minutes, onClick = { model.settings(state.settings.copy(leadMinutes = minutes)); chooseLead = false })
                Text(if (minutes == 0) "按任务时间准时提醒" else "提前 $minutes 分钟")
            }
        }
    }, confirmButton = { TextButton(onClick = { chooseLead = false }) { Text("取消") } })
    if (confirmLogout) AlertDialog(onDismissRequest = { confirmLogout = false }, title = { Text(if (state.demo) "退出演示？" else "退出登录？") },
        text = { Text("清除本机排期缓存和待发提醒，团队排期与完成记录会保留。") },
        confirmButton = { TextButton(onClick = { confirmLogout = false; model.signOut() }) { Text("退出") } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } })
    if (showLicenses) {
        val notice = remember {
            "Kizitonwose Calendar 2.6.1\n\n" + context.assets.open("calendar-license.txt").bufferedReader().use { it.readText() } +
                "\n\nLucide Icons\n\n" + context.assets.open("lucide-license.txt").bufferedReader().use { it.readText() } +
                "\n\nHaze 1.1.1\n\n" + context.assets.open("haze-license.txt").bufferedReader().use { it.readText() } +
                "\n\nMiSans · Xiaomi Inc.\n\n" + context.assets.open("font-license.txt").bufferedReader().use { it.readText() } +
                "\n\nLora · SIL Open Font License 1.1\n\n" + context.assets.open("lora-license.txt").bufferedReader().use { it.readText() }
        }
        AlertDialog(onDismissRequest = { showLicenses = false }, title = { Text("字体与组件许可") }, text = {
            Text(notice, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()))
        }, confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } })
    }
}
