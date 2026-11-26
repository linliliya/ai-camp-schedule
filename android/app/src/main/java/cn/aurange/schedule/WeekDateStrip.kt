package cn.aurange.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import java.time.DayOfWeek
import java.time.LocalDate

@Composable fun WeekDateStrip(selectedDate: LocalDate, today: LocalDate, onDate: (LocalDate) -> Unit) {
    val firstDate = remember { selectedDate }
    val calendar = rememberWeekCalendarState(startDate = LocalDate.of(2000, 1, 1), endDate = LocalDate.of(2100, 12, 31),
        firstVisibleWeekDate = firstDate, firstDayOfWeek = DayOfWeek.MONDAY)
    val currentDate by rememberUpdatedState(selectedDate)
    val onDateChange by rememberUpdatedState(onDate)
    LaunchedEffect(selectedDate) {
        if (calendar.firstVisibleWeek.days.none { it.date == selectedDate }) calendar.scrollToWeek(selectedDate)
    }
    LaunchedEffect(calendar) {
        snapshotFlow { calendar.isScrollInProgress to calendar.firstVisibleWeek.days.first().date }
            .distinctUntilChanged().filter { !it.first }.collect { (_, first) ->
                val date = currentDate
                if (date < first || date > first.plusDays(6)) {
                    onDateChange(first.plusDays((date.dayOfWeek.value - 1).toLong()))
                }
            }
    }
    WeekCalendar(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "周日历，左右滑动切换周" }, state = calendar,
        dayContent = { day ->
            val selected = day.date == selectedDate
            Column(Modifier.fillMaxWidth().padding(horizontal = 2.dp).clip(RoundedCornerShape(15.dp))
                .background(if (selected) Blue else Color.Transparent)
                .semantics { contentDescription = day.date.format(fullDate) }
                .selectable(selected, role = Role.Button, onClick = { onDate(day.date) })
                .heightIn(min = 66.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(day.date.format(weekDay).replace("周", ""), fontSize = 12.sp, color = if (selected) Color.White else Muted)
                Text("${day.date.dayOfMonth}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    color = if (selected) Color.White else Ink, modifier = Modifier.padding(top = 4.dp))
                Box(Modifier.padding(top = 5.dp).size(3.dp).background(
                    if (day.date == today) { if (selected) Color.White else Blue } else Color.Transparent, RoundedCornerShape(3.dp)))
            }
        })
}
