package com.oashobi.musclehabit.desktop.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oashobi.musclehabit.desktop.AutoStart
import com.oashobi.musclehabit.desktop.EngineState
import com.oashobi.musclehabit.desktop.Network
import java.time.format.DateTimeFormatter

private val SYNC_TIME = DateTimeFormatter.ofPattern("M/d H:mm")

/** スマホとつなぐ：IP アドレスとペアリングコードを表示する */
@Composable
fun ConnectScreen(state: EngineState, onRegenerate: () -> Unit, onForget: () -> Unit) {
    val ips = remember { Network.localAddresses() }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("スマホとつなぐ", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "スマホの MuscleHabit で「設定 → PC と同期 → つなぐ」を開き、下の IP アドレスとコードを入れてください。スマホと PC は同じ Wi-Fi（家のネットワーク）につないでおきます。",
            fontSize = 14.sp,
            color = AppColors.SubText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Card(Modifier.weight(1f)) {
                Text("IP アドレス", fontSize = 13.sp, color = AppColors.SubText)
                SelectionContainer {
                    Column {
                        if (ips.isEmpty()) Text("見つかりません（ネットワークにつながっていますか？）", color = AppColors.Danger)
                        ips.forEachIndexed { i, ip ->
                            Text(ip, fontSize = if (i == 0) 30.sp else 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                Text("ポート ${state.config.port}（入力は IP アドレスだけで大丈夫です）", fontSize = 12.sp, color = AppColors.SubText)
            }
            Card(Modifier.weight(1f)) {
                Text("ペアリングコード", fontSize = 13.sp, color = AppColors.SubText)
                Text(state.config.pairingCode.chunked(3).joinToString(" "), fontSize = 40.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text("一度使うと新しいコードに変わります", fontSize = 12.sp, color = AppColors.SubText)
                TextButton(onClick = onRegenerate) { Text("コードを作り直す") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Text("状態", fontSize = 13.sp, color = AppColors.SubText)
            StatusLine("同期サーバー", state.serverRunning, if (state.serverRunning) "動いています" else state.serverError ?: "止まっています")
            StatusLine("MySQL", state.dbConnected, if (state.dbConnected) "つながっています" else state.dbError ?: "つながっていません")
            val phones = state.config.tokens.size
            Text(
                if (phones == 0) "つないだスマホはまだありません" else "つないだスマホ：${phones}台",
                fontSize = 14.sp,
            )
            Text(
                state.lastSyncAt?.let { "最後に記録が届いた日時：${it.format(SYNC_TIME)}" } ?: "このアプリを起動してから、まだ記録は届いていません",
                fontSize = 14.sp,
                color = AppColors.SubText,
            )
            if (phones > 0) TextButton(onClick = onForget) { Text("つないだスマホを全部外す", color = AppColors.SubText) }
        }
        Card(Modifier.fillMaxWidth(), spacing = 6) {
            Text("つながらないとき", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Hint("Windows の「設定 → ネットワークとインターネット」で、家の Wi-Fi が「プライベート ネットワーク」になっているか確かめてください。")
            Hint("初回は Windows Defender ファイアウォールの確認が出ます。「プライベート ネットワーク」だけ許可してください（パブリックは不要です）。")
            Hint("ルーターで PC の IP アドレスを固定しておくと、つなぎ直す手間がなくなります。")
        }
    }
}

@Composable
private fun StatusLine(label: String, ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, modifier = Modifier.width(120.dp))
        Text(text, fontSize = 14.sp, color = if (ok) AppColors.PrimaryDark else AppColors.Danger, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Hint(text: String) {
    Text("・$text", fontSize = 13.sp, color = AppColors.SubText)
}

/** 設定：MySQL の接続先・自動起動・通知の確認 */
@Composable
fun SettingsScreen(
    state: EngineState,
    onSaveDb: (url: String, user: String, password: String) -> Unit,
    onTestNotification: () -> Unit,
) {
    var url by remember(state.config.dbUrl) { mutableStateOf(state.config.dbUrl) }
    var user by remember(state.config.dbUser) { mutableStateOf(state.config.dbUser) }
    var password by remember(state.config.dbPassword) { mutableStateOf(state.config.dbPassword) }
    var autoStart by remember { mutableStateOf(AutoStart.isEnabled()) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("設定", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Card(Modifier.fillMaxWidth()) {
            Text("MySQL", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "最初に sql/setup.sql で データベース musclehabit とユーザーを作ってください。テーブルはこのアプリが作ります。",
                fontSize = 13.sp,
                color = AppColors.SubText,
            )
            OutlinedTextField(url, { url = it }, label = { Text("接続先（JDBC URL）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(user, { user = it }, label = { Text("ユーザー名") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("パスワード") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = { onSaveDb(url, user, password) }) { Text("保存してつなぐ") }
                Text(
                    if (state.dbConnected) "つながっています" else state.dbError ?: "",
                    fontSize = 13.sp,
                    color = if (state.dbConnected) AppColors.PrimaryDark else AppColors.Danger,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Windows のログイン時に起動する", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (AutoStart.available) "起動するとタスクトレイに入り、予定の前に通知します" else "インストールした MuscleHabit から起動したときに設定できます",
                        fontSize = 13.sp,
                        color = AppColors.SubText,
                    )
                }
                Switch(
                    checked = autoStart,
                    enabled = AutoStart.available,
                    onCheckedChange = { on -> if (AutoStart.setEnabled(on)) autoStart = on },
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Text("通知", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "スマホから届いた予定をもとに、予定の${state.snapshot.settings.leadMinutes}分前に Windows の通知を出します（スマホでチェックイン済みの日は出しません）。通知が出ないときは、Windows の「集中モード（応答不可）」の例外に MuscleHabit を加えてください。",
                fontSize = 13.sp,
                color = AppColors.SubText,
            )
            OutlinedButton(onClick = onTestNotification) { Text("通知を試す") }
        }
    }
}
