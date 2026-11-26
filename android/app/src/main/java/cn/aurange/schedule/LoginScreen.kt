package cn.aurange.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun LoginScreen(state: AppState, model: ScheduleViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val context = LocalContext.current
    val submit = { focus.clearFocus(); model.signIn(email, password); password = "" }
    LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(140.dp).background(Lavender, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
                Box(Modifier.offset(x = 108.dp, y = (-40).dp).size(120.dp).background(Color.White.copy(alpha = .35f), CircleShape))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp), shadowElevation = 2.dp) {
                        Column(Modifier.width(86.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${state.today.monthValue} 月", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("${state.today.dayOfMonth}", color = Ink, fontSize = 35.sp, lineHeight = 43.sp, fontWeight = FontWeight.Bold)
                            Text(state.today.format(weekDay), color = Muted, fontSize = 12.sp)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIcon(Glyph.CircleCheck, tint = Blue, modifier = Modifier.size(18.dp))
                            Box(Modifier.width(70.dp).height(6.dp).background(Blue.copy(alpha = .22f), RoundedCornerShape(3.dp)))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppIcon(Glyph.Clock, tint = Blue.copy(alpha = .5f), modifier = Modifier.size(18.dp))
                            Box(Modifier.width(48.dp).height(6.dp).background(Blue.copy(alpha = .12f), RoundedCornerShape(3.dp)))
                        }
                    }
                }
            }
        }
        item {
            Text("今天从这里开始。", style = MaterialTheme.typography.headlineMedium)
            Text("查看营期安排，收到及时提醒。", style = MaterialTheme.typography.bodyMedium, color = Muted, modifier = Modifier.padding(top = 7.dp))
        }
        item {
            WhiteCard {
                Text("登录账号", style = MaterialTheme.typography.titleMedium)
                Text("使用网页版相同的邮箱与密码", fontSize = 12.sp, color = Muted)
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("邮箱") }, singleLine = true,
                    leadingIcon = { AppIcon(Glyph.Mail) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("密码") }, singleLine = true,
                    leadingIcon = { AppIcon(Glyph.Lock) }, trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) { AppIcon(if (showPassword) Glyph.EyeOff else Glyph.Eye, if (showPassword) "隐藏密码" else "显示密码") }
                    }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), enabled = !state.busy,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (email.isNotBlank() && password.isNotEmpty() && !state.busy) submit() }))
                Button(onClick = submit, modifier = Modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(14.dp),
                    enabled = !state.busy && email.isNotBlank() && password.isNotEmpty()) {
                    Text(if (state.busy) "正在登录…" else "登录并同步排期")
                    Spacer(Modifier.width(10.dp)); AppIcon(Glyph.ArrowRight, tint = if (state.busy || email.isBlank() || password.isEmpty()) Muted else Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        item {
            TextButton(onClick = model::demo, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                Text("先看看应用", fontSize = 13.sp); Spacer(Modifier.width(6.dp)); AppIcon(Glyph.ArrowUpRight, tint = Blue, modifier = Modifier.size(14.dp))
            }
            TextButton(onClick = { ExternalLinks.document(context, "https://internal.aurange.cn/ai-camp-schedule/") }, modifier = Modifier.fillMaxWidth()) {
                Text("打开网页版", color = Muted, fontSize = 12.sp)
            }
        }
    }
}
