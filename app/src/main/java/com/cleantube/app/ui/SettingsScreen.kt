package com.cleantube.app.ui

import androidx.compose.foundation.border
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
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
import com.cleantube.app.data.DefaultHiddenTabs
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

/** Sign-in card. Signing in makes Home, Feed and Notification your real YouTube ones. */
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
                    if (tokenError.isNotEmpty()) Text(tokenError, color = Danger, fontSize = 13.sp)
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    if (Session.handle.isNotEmpty()) Text(Session.handle, color = TextMuted, fontSize = 13.sp)
                }
            }
            Text(
                "Home, Feed and Notification now come from your YouTube account.",
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
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                "CleanTube talks to YouTube the way the website does, so there is no API key. " +
                    "Without signing in you get YouTube's general home feed. After signing in, Home shows YouTube's " +
                    "recommendations for you, and Feed and Notification are your real ones.\n\n" +
                    "This is not an official YouTube API. It can break when YouTube changes things, " +
                    "and Google's terms don't allow unofficial clients, so use an account you can afford to risk.",
                color = TextMuted,
                fontSize = 13.sp
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 22.dp, top = 22.dp, bottom = 8.dp)
    )
}

/** Order and visibility of the bottom bar tabs: arrows move a tab, the switch shows / hides it. */
@Composable
private fun TabsSettings(modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val store = Graph.store
    val all = orderedTabs(store)
    val hidden = store.hiddenTabs
    val shown = all.count { it.id !in hidden }

    fun move(index: Int, delta: Int) {
        val ids = all.map { it.id }.toMutableList()
        val to = index + delta
        if (to !in ids.indices) return
        val moved = ids.removeAt(index)
        ids.add(to, moved)
        store.tabOrder = ids
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        all.forEachIndexed { i, t ->
            val on = t.id !in hidden
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(t.icon, contentDescription = null, tint = if (on) TextPrimary else TextMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(
                    t.label,
                    color = if (on) TextPrimary else TextMuted,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                val canUp = t.id != "shorts" && i > 1  // Shorts always stays first
                val canDown = t.id != "shorts" && i < all.lastIndex
                IconButton(onClick = { move(i, -1) }, enabled = canUp) {
                    Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move ${t.label} up", tint = if (canUp) TextPrimary else TextMuted.copy(alpha = 0.4f))
                }
                IconButton(onClick = { move(i, 1) }, enabled = canDown) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move ${t.label} down", tint = if (canDown) TextPrimary else TextMuted.copy(alpha = 0.4f))
                }
                Switch(
                    checked = on,
                    // at least one tab always stays on
                    enabled = !(on && shown <= 1),
                    onCheckedChange = { checked ->
                        store.hiddenTabs = if (checked) hidden - t.id else hidden + t.id
                        // The tab under the Settings page may be the one that was just turned off:
                        // swap it for the first visible tab so Back doesn't land on a hidden one.
                        val visibleRoutes = visibleTabs(store).map { it.route }
                        if (nav.stack.isNotEmpty() && nav.stack[0] in TabRoutes && nav.stack[0] !in visibleRoutes) {
                            nav.stack[0] = visibleRoutes.first()
                        }
                    }
                )
            }
        }
        Text(
            "A hidden tab disappears from the bottom bar. Downloads stay reachable from Settings and the download notification.",
            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)
        )
        Pill(
            "Reset tabs",
            onClick = { store.tabOrder = emptyList(); store.hiddenTabs = DefaultHiddenTabs },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private enum class SettingsPage(val title: String) {
    Account("Account"),
    Data("Your data"),
    Appearance("Appearance"),
    Navigation("Navigation bar"),
    Content("Content"),
    Downloads("Downloads"),
    Player("Player"),
    About("About")
}

/** One row of the settings list: round coloured icon, title and a one-line subtitle. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconBg: Color,
    title: String,
    subtitle: String,
    shape: RoundedCornerShape,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .bouncyClickable(pressedScale = 0.98f, onClick = onClick)
            .fillMaxWidth()
            .clip(shape)
            .background(Card)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(17.dp)).background(Bg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = TextMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private class SettingsItem(
    val page: SettingsPage,
    val icon: ImageVector,
    val iconBg: Color,
    val subtitle: String
)

private val IconBlue = Color(0xFF4A5D8E)
private val IconGrey = Color(0xFF5A5F70)
private val IconPurple = Color(0xFF6E5780)

/** A group of rows: big rounded corners on the outside of the group, small ones between the rows. */
@Composable
private fun SettingsGroup(items: List<SettingsItem>, onOpen: (SettingsPage) -> Unit) {
    val outer = 28.dp
    val inner = 6.dp
    Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.forEachIndexed { i, it ->
            val shape = RoundedCornerShape(
                topStart = if (i == 0) outer else inner,
                topEnd = if (i == 0) outer else inner,
                bottomStart = if (i == items.lastIndex) outer else inner,
                bottomEnd = if (i == items.lastIndex) outer else inner
            )
            SettingsRow(it.icon, it.iconBg, it.page.title, it.subtitle, shape) { onOpen(it.page) }
        }
    }
}

@Composable
fun SettingsScreen() {
    val nav = LocalNav.current
    val store = Graph.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var regionText by remember { mutableStateOf(store.region) }
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }

    BackHandler(enabled = page != null) { page = null }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { message = importFromUri(ctx, uri) }
    }
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) scope.launch { message = exportToUri(ctx, uri) }
    }

    val current = page
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        if (current == null) {
            // Main list
            IconButton(onClick = { nav.back() }, modifier = Modifier.padding(start = 12.dp, top = 12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Text(
                "Settings",
                color = TextPrimary,
                fontSize = 44.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 24.dp, top = 24.dp, bottom = 20.dp)
            )
            SettingsGroup(
                listOf(
                    SettingsItem(
                        SettingsPage.Account, Icons.Rounded.AccountCircle, IconBlue,
                        if (Session.loggedIn) Session.name.ifEmpty { "Signed in" } else "Sign in and manage account"
                    ),
                    SettingsItem(SettingsPage.Data, Icons.Rounded.Storage, IconBlue, "Import, export and history")
                ),
                onOpen = { page = it }
            )
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                listOf(
                    SettingsItem(SettingsPage.Appearance, Icons.Rounded.Palette, IconGrey, "Font and visual style"),
                    SettingsItem(SettingsPage.Navigation, Icons.Rounded.Tab, IconPurple, "Order and visibility of tabs"),
                    SettingsItem(SettingsPage.Content, Icons.Rounded.Language, IconBlue, "Region and country"),
                    SettingsItem(SettingsPage.Downloads, Icons.Rounded.Download, IconPurple, "Location and connections"),
                    SettingsItem(SettingsPage.Player, Icons.Rounded.PictureInPictureAlt, IconBlue, "Popup and mini player")
                ),
                onOpen = { page = it }
            )
            Spacer(Modifier.height(16.dp))
            SettingsGroup(
                listOf(SettingsItem(SettingsPage.About, Icons.Rounded.Info, IconGrey, "Version and info")),
                onOpen = { page = it }
            )
            Spacer(Modifier.height(40.dp))
        } else {
            ScreenTitle(current.title, onBack = { page = null })
            when (current) {
                SettingsPage.Account -> AccountCard(Modifier.padding(horizontal = 12.dp))

                SettingsPage.Appearance -> {
                    SectionTitle("COLOUR")
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
                                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Colours from the video", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(checked = store.videoColor, onCheckedChange = { store.videoColor = it })
                        }
                        Text(
                            "While a video is open, the app takes its colours from that video's thumbnail and changes smoothly with every new video.",
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    SectionTitle("FONT")
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("Poppins", onClick = { store.appFont = "poppins" }, active = store.appFont != "system")
                            Pill("System font", onClick = { store.appFont = "system" }, active = store.appFont == "system")
                        }
                        Text(
                            "Poppins is the default. System font uses your phone's own font.",
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                SettingsPage.Navigation -> {
                    SectionTitle("TABS (BOTTOM BAR)")
                    TabsSettings(Modifier.padding(horizontal = 12.dp))
                }

                SettingsPage.Content -> {
                    SectionTitle("REGION (2-LETTER COUNTRY CODE)")
                    PillTextField(
                        value = regionText,
                        onValueChange = {
                            regionText = it.uppercase().take(2)
                            if (regionText.length == 2) {
                                store.region = regionText
                                Yt.setLocale("en", regionText)
                                com.cleantube.app.data.TabCache.clear()
                            }
                        },
                        placeholder = "IN",
                        modifier = Modifier.padding(horizontal = 12.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                    )
                }

                SettingsPage.Downloads -> {
                    SectionTitle("DOWNLOADS")
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("In app", onClick = { store.downloadWhere = "app" }, active = store.downloadWhere == "app")
                            Pill("Phone storage", onClick = { store.downloadWhere = "phone" }, active = store.downloadWhere == "phone")
                        }
                        Text(
                            "Default place for new downloads. You can change it each time.",
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)
                        )
                        Text("Connections per download (more is faster, but YouTube may refuse too many):", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1, 4, 8, 16, 32).forEach { n ->
                                Pill("$n", onClick = { store.downloadThreads = n }, active = store.downloadThreads == n)
                            }
                        }
                        Pill("Open downloads", onClick = { nav.push(Route.Downloads) }, modifier = Modifier.fillMaxWidth())
                    }
                }

                SettingsPage.Player -> {
                    SectionTitle("POPUP")
                    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card)
                                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text("Open popup when I leave the app", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Switch(checked = store.autoPopup, onCheckedChange = { store.autoPopup = it })
                        }
                        Text(
                            "While a video is playing, going Home or switching apps shrinks it into a floating window with play / pause, speed and quality buttons.",
                            color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                SettingsPage.Data -> {
                    SectionTitle("STORED ONLY ON THIS PHONE")
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
                }

                SettingsPage.About -> {
                    Text(
                        "CleanTube ${"2.0"}\n" +
                            "Browse, search and comments use YouTube's own (unofficial) internal API, no key needed. " +
                            "Playback uses YouTube's official player, so ads, quality and controls are decided by YouTube. " +
                            "Background play is not possible with it. Downloads use separate links (see the Download button on a video).\n\n" +
                            "Layout inspired by NewPipe. Not affiliated with YouTube, Google or NewPipe.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(40.dp))
        }
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
