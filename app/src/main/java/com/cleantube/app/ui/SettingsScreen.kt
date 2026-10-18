package com.cleantube.app.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cleantube.app.data.ApiException
import com.cleantube.app.data.Graph
import com.cleantube.app.data.Api
import com.cleantube.app.data.Session
import com.cleantube.app.data.Yt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sign-in card. Signing in makes Home, Feed and History your real YouTube ones. */
@Composable
fun AccountCard(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var tokenDialog by remember { mutableStateOf(false) }
    var tokenText by remember { mutableStateOf("") }
    var tokenError by remember { mutableStateOf("") }
    var tokenBusy by remember { mutableStateOf(false) }

    if (tokenDialog) {
        AlertDialog(
            onDismissRequest = { if (!tokenBusy) tokenDialog = false },
            containerColor = Card,
            title = { Text("Sign in with token") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste the token copied from CleanTube or from your NewPipe fork (\"Copy token\"), " +
                            "or just a cookie string that contains SAPISID.",
                        color = TextMuted, fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (tokenError.isNotEmpty()) Text(tokenError, color = Accent, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (tokenBusy) return@TextButton
                    tokenBusy = true
                    tokenError = ""
                    scope.launch {
                        val err = Session.loginWithToken(tokenText)
                        tokenBusy = false
                        if (err == null) {
                            tokenDialog = false
                            tokenText = ""
                        } else {
                            tokenError = err
                        }
                    }
                }) { Text(if (tokenBusy) "Checking..." else "Sign in", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { if (!tokenBusy) tokenDialog = false }) { Text("Cancel", color = TextPrimary) }
            }
        )
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (Session.loggedIn) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (Session.thumb.isNotEmpty()) {
                    AppImage(Session.thumb, Modifier.size(44.dp), androidx.compose.foundation.shape.CircleShape)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        Session.name.ifEmpty { "Signed in" },
                        color = TextPrimary,
                        fontFamily = NType,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (Session.handle.isNotEmpty()) Text(Session.handle, color = TextMuted, fontSize = 13.sp)
                }
            }
            Text(
                "Home, Feed and History now come from your YouTube account.",
                color = TextMuted,
                fontSize = 13.sp
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Import my subscriptions", onClick = {
                    scope.launch {
                        try {
                            val list = Api.accountChannels()
                            Graph.store.importChannels(list)
                        } catch (e: Exception) {
                            // shown nowhere: the Library tab simply stays as it was
                        }
                    }
                })
                Pill("Copy token", onClick = {
                    Toast.makeText(
                        ctx,
                        if (Session.copyToken(ctx)) "Token copied. It gives full access to your account: don't share it." else "Not signed in.",
                        Toast.LENGTH_LONG
                    ).show()
                })
                Pill("Sign out", onClick = {
                    Session.logout()
                    scope.launch { try { Yt.run("reset") } catch (e: Exception) { /* ignore */ } }
                })
            }
        } else {
            Text(
                "Sign in with your Google account",
                color = TextPrimary,
                fontFamily = NType,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                "CleanTube talks to YouTube the way the website does, so there is no API key. " +
                    "Without signing in you get YouTube's general home feed. After signing in, Home shows YouTube's " +
                    "recommendations for you, and Feed and History are your real ones.\n\n" +
                    "This is not an official YouTube API. It can break when YouTube changes things, " +
                    "and Google's terms don't allow unofficial clients, so use an account you can afford to risk.",
                color = TextMuted,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Sign in", onClick = { nav.push(Route.Login) }, active = true)
                Pill("Sign in with token", onClick = { tokenDialog = true })
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        color = TextMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var regionText by remember { mutableStateOf(store.region) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = importFromUri(ctx, uri) }
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch { message = exportToUri(ctx, uri) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Settings", onBack = { nav.back() })

        SectionTitle("ACCOUNT")
        AccountCard(Modifier.padding(horizontal = 12.dp))

        SectionTitle("REGION (2-LETTER COUNTRY CODE)")
        PillTextField(
            value = regionText,
            onValueChange = {
                regionText = it.uppercase().take(2)
                if (regionText.length == 2) {
                    store.region = regionText
                    Yt.setLocale("en", regionText)
                }
            },
            placeholder = "IN",
            modifier = Modifier.padding(horizontal = 12.dp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
        )

        SectionTitle("YOUR DATA (STORED ONLY ON THIS PHONE)")
        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                "Import subscriptions (NewPipe or CleanTube .json)",
                onClick = { importPicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            )
            Pill(
                "Export backup (.json)",
                onClick = { exportPicker.launch("cleantube-backup.json") },
                modifier = Modifier.fillMaxWidth()
            )
            Pill("Clear watch history", onClick = {
                store.clearHistory()
                message = "Watch history cleared."
            }, modifier = Modifier.fillMaxWidth())
            Pill("Clear search history", onClick = {
                store.clearSearches()
                message = "Search history cleared."
            }, modifier = Modifier.fillMaxWidth())
            if (message.isNotEmpty()) {
                Text(message, color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
            }
        }

        SectionTitle("ABOUT")
        Text(
            "CleanTube ${"2.0"}\n" +
                "Browse, search and comments use YouTube's own (unofficial) internal API, no key needed. " +
                "Playback uses YouTube's official player, so ads, quality and controls are decided by YouTube. " +
                "Downloads and background play are not possible with it.\n\n" +
                "Layout inspired by NewPipe. Not affiliated with YouTube, Google or NewPipe.",
            color = TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 40.dp)
        )
    }
}

private suspend fun importFromUri(ctx: Context, uri: Uri): String {
    val text = withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
    } ?: return "Couldn't read that file."

    val store = Graph.store
    val added = try {
        store.importJson(text)
    } catch (e: Exception) {
        return "That file isn't a valid NewPipe or CleanTube export."
    }

    // Imported subscriptions often have no avatar: fetch them.
    val missing = store.subscriptions.filter { it.thumb.isEmpty() }.map { it.id }
    if (missing.isNotEmpty()) {
        try {
            store.refreshSubscriptions(Api.channelsByIds(missing))
        } catch (e: ApiException) {
            return "Imported $added subscriptions (avatars will fill in later)."
        }
    }
    return "Imported $added new subscriptions."
}

private suspend fun exportToUri(ctx: Context, uri: Uri): String {
    val json = Graph.store.exportJson()
    return withContext(Dispatchers.IO) {
        try {
            ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            "Backup saved."
        } catch (e: Exception) {
            "Couldn't write the file."
        }
    }
}
