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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
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
import com.cleantube.app.data.YouTubeApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Paste-your-own-key card. Used on Home (first run) and in Settings. */
@Composable
fun ApiKeySetup(modifier: Modifier = Modifier) {
    val store = Graph.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf(store.apiKey) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Connect YouTube",
            color = TextPrimary,
            fontFamily = NType,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        Text(
            "CleanTube uses YouTube's official Data API. You need your own free key:\n" +
                "1. Open Google Cloud Console and create a project.\n" +
                "2. Enable \"YouTube Data API v3\".\n" +
                "3. Credentials > Create credentials > API key, then paste it below.\n\n" +
                "Free quota: 10,000 units a day. A search costs 100, most other things cost 1.",
            color = TextMuted,
            fontSize = 13.sp
        )
        Pill(
            "Open Google Cloud Console",
            onClick = { openUrl(ctx, "https://console.cloud.google.com/apis/library/youtube.googleapis.com") }
        )
        PillTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "Paste API key (AIza...)",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Pill(
                if (busy) "Checking..." else "Save and test",
                onClick = {
                    if (busy) return@Pill
                    if (text.isBlank()) {
                        status = "Paste your key first."
                        return@Pill
                    }
                    busy = true
                    status = ""
                    scope.launch {
                        val problem = YouTubeApi.testKey(text)
                        if (problem == null) {
                            store.apiKey = text
                            status = "Key works."
                        } else {
                            status = problem
                        }
                        busy = false
                    }
                },
                active = true
            )
            if (status.isNotEmpty()) {
                Text(status, color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
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

        SectionTitle("YOUTUBE API")
        ApiKeySetup(Modifier.padding(horizontal = 12.dp))

        SectionTitle("TRENDING REGION (2-LETTER COUNTRY CODE)")
        PillTextField(
            value = regionText,
            onValueChange = {
                regionText = it.uppercase().take(2)
                if (regionText.length == 2) store.region = regionText
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
                "Browse, search and comments use the official YouTube Data API. Playback uses YouTube's official player, " +
                "so ads, quality and controls are decided by YouTube. Downloads and background play are not possible " +
                "with the official player.\n\n" +
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

    // Imported subscriptions often have no avatar: fetch them if a key is set (about 1 quota unit per 50 channels).
    val missing = store.subscriptions.filter { it.thumb.isEmpty() }.map { it.id }
    if (missing.isNotEmpty() && store.apiKey.isNotEmpty()) {
        try {
            store.refreshSubscriptions(YouTubeApi.channelsByIds(missing))
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
