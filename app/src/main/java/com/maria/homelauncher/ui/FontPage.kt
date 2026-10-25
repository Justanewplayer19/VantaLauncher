package com.maria.homelauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.maria.homelauncher.FontChoice
import com.maria.homelauncher.FontHit
import com.maria.homelauncher.FontRef
import com.maria.homelauncher.FontSource
import com.maria.homelauncher.Fonts
import com.maria.homelauncher.LocalInk
import com.maria.homelauncher.Style

private const val PAGE = 14

/**
 * Pick a font from any of the places one can come from.
 *
 * The tabs are the providers. "All" searches every one of them at once and folds the
 * duplicates together, since the phone ships a dozen families that are also in the Google
 * catalogue, Fontsource is almost entirely a mirror of Google, and listing the same
 * name three times helps nobody.
 *
 * Only a pageful is drawn at a time, because every row on a web tab draws its own name in
 * its own font, and that is a download each. A screen of two hundred would ask for two
 * hundred of them before anyone had chosen anything.
 */
@Composable
fun FontPicker(
    selected: String,
    onPick: (String) -> Unit,
    googleOk: Boolean,
    leading: (@Composable () -> Unit)? = null,
    onAddFile: (() -> Unit)? = null
) {
    val ctx = LocalContext.current

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf<FontSource?>(null) }
    var group by remember { mutableStateOf(0) }
    var shown by remember { mutableStateOf(PAGE) }
    var files by remember { mutableStateOf(Fonts.fileRefs(ctx)) }
    var saving by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // each list is fetched once a week, and only for the tab being looked at. Three
    // catalogues up front would be three requests nobody asked for; "All providers" is
    // the one place that really does need them all, since it searches them all.
    LaunchedEffect(tab) {
        val want = if (tab == null) FontSource.entries.filter { it.web } else listOfNotNull(tab)
        want.filter { it.web }.forEach { Fonts.refreshCatalogue(ctx, it) }
    }
    // a plain val, because the state delegate above cannot be smart cast
    val here = tab

    // what is already in the user's own folder, so a web row can say it has been kept
    // rather than offering to keep it a second time
    val keptPaths = remember(files) { files.map { it.value }.toSet() }
    val webGroups = here?.takeIf { it.web }?.let { Fonts.webGroups(it) }.orEmpty()

    LaunchedEffect(query, tab, group) { shown = PAGE }
    // a search typed against one provider means nothing in the next, and leaving it there
    // just shows an empty list with no obvious reason
    LaunchedEffect(tab) { query = "" }

    val tabs = listOf<FontSource?>(null) + FontSource.entries
    ProviderDots(tabs, tab) { tab = it }

    Spacer(Modifier.height(10.dp))
    SearchBox(query) { query = it }

    // a web catalogue is the only kind big enough to want splitting up
    if (webGroups.size > 1 && query.isBlank()) {
        ChipRow(webGroups.map { it.first }, group.coerceIn(0, webGroups.lastIndex)) { group = it }
    }

    Spacer(Modifier.height(10.dp))
    leading?.invoke()

    val hits: List<FontHit> = remember(query, tab, group, files, Fonts.webCatalogues) {
        when {
            here == null -> Fonts.searchAll(ctx, query)
            here.web && query.isBlank() ->
                webGroups.getOrNull(group.coerceIn(0, webGroups.lastIndex))?.second.orEmpty()
                    .map { FontHit(FontRef(here, it, it), emptyList()) }
            here == FontSource.Google -> {
                val found = Fonts.googleRefs.filter { it.matches(query) }
                // Google holds far more than ships in that list, so a name typed off
                // fonts.google.com is offered even when nothing here matches it
                val typed = query.trim()
                val exact = found.any { it.name.equals(typed, true) }
                (if (exact) found else listOf(FontRef(FontSource.Google, typed, typed)) + found)
                    .map { FontHit(it, emptyList()) }
            }
            here == FontSource.Files ->
                files.filter { it.matches(query) }.map { FontHit(it, emptyList()) }
            else -> Fonts.refsFor(ctx, here).filter { it.matches(query) }
                .map { FontHit(it, emptyList()) }
        }
    }

    if (tab == FontSource.Google && !googleOk) {
        Hint(
            "No Google Play services on this phone, so these are fetched straight from " +
                "Google's own servers instead and kept. Everything still works, it just " +
                "costs a little storage per font."
        )
    }

    if (hits.isEmpty()) {
        Hint(
            when {
                tab == FontSource.Files -> "No font files added yet."
                here != null && here.web && query.isBlank() &&
                    Fonts.catalogueOf(here).isEmpty() ->
                    "Fetching the list. This needs a connection the first time, once."
                else -> "Nothing by that name."
            }
        )
    }

    hits.take(shown).forEach { hit ->
        FontRow(
            name = hit.ref.name,
            selected = hit.ref.id == selected,
            family = familyOf(hit.ref),
            source = if (tab == null) hit.ref.source else null,
            alsoIn = hit.alsoIn,
            onRemove = if (hit.ref.source == FontSource.Files) {
                {
                    Fonts.forget(hit.ref.value)
                    files = Fonts.fileRefs(ctx)
                    if (hit.ref.id == selected) onPick("")
                }
            } else {
                null
            },
            onSave = if (hit.ref.source.web) {
                {
                    // saving a copy is not the same as choosing it, so this only adds
                    // the file; picking it is still a tap on the row
                    saving = hit.ref.name
                    failed = null
                    scope.launch {
                        val got = Fonts.keepOffline(ctx, hit.ref.source, hit.ref.name)
                        files = Fonts.fileRefs(ctx)
                        saving = null
                        if (got == null) failed = hit.ref.name
                    }
                }
            } else {
                null
            },
            busy = saving == hit.ref.name,
            kept = Fonts.keptPath(ctx, hit.ref.name) in keptPaths,
            failed = failed == hit.ref.name
        ) { onPick(hit.ref.id) }
    }

    failed?.let {
        Hint("$it could not be downloaded. That usually means no connection.")
    }

    if (hits.size > shown) {
        TextButton(onClick = { shown += PAGE }) {
            Text("Show ${minOf(PAGE, hits.size - shown)} more")
        }
    }

    if (tab == FontSource.Files && onAddFile != null) {
        OutlinedButton(onClick = onAddFile) { Text("Add a font file") }
        Hint(
            "A .ttf or .otf file. It is copied into the launcher, so moving or deleting " +
                "the original afterwards is fine."
        )
    }

    if (here != null && here.web) {
        val known = Fonts.catalogueOf(here).size
        Hint(
            if (known == 0) {
                "${here.blurb} The list is fetched the first time this tab has a connection."
            } else {
                "$known families. ${here.blurb} \"Keep\" downloads one into your files so " +
                    "it works with no connection."
            }
        )
    }

    if (tab == FontSource.Device) {
        Hint(
            "${Fonts.deviceRefs.size} fonts already on this phone. Nothing to fetch, and " +
                "they work with no connection. Most of them are for one alphabet each."
        )
    }

    if (tab == null) {
        Hint(
            "Searching every provider at once. Where the same name is in more than one, " +
                "the local copy is used and the row says where else it was found."
        )
    }
}

@Composable
private fun familyOf(ref: FontRef): FontFamily? = when {
    ref.source == FontSource.BuiltIn -> Fonts.family(ref.id, FontChoice.Default)
    ref.source == FontSource.Google -> Fonts.google(ref.value)
    ref.source.web -> Fonts.web(ref.source, ref.value)
    else -> Fonts.fromFile(ref.value)
}

/** The provider switch. Dots rather than chips, so it does not read as another filter. */
@Composable
private fun ProviderDots(
    tabs: List<FontSource?>,
    selected: FontSource?,
    onPick: (FontSource?) -> Unit
) {
    val ink = LocalInk.current
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        tabs.forEach { t ->
            val on = t == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(t) }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (on) ink else ink.copy(alpha = 0.12f))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    t?.label ?: "All providers",
                    color = if (on) ink else ink.copy(alpha = 0.65f),
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun SearchBox(query: String, onChange: (String) -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ink.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        if (query.isEmpty()) {
            Text("Search by name", color = ink.copy(alpha = 0.35f), fontSize = 14.sp)
        }
        BasicTextField(
            value = query,
            onValueChange = { onChange(it.take(32)) },
            singleLine = true,
            cursorBrush = SolidColor(ink),
            textStyle = TextStyle(color = ink, fontSize = 14.sp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FontPage(style: Style, onStyle: (Style) -> Unit, onPickFile: () -> Unit, googleOk: Boolean) {
    val ink = LocalInk.current

    Label("In use")
    Text(
        Fonts.label(style.fontId, style.font),
        color = ink,
        fontSize = 20.sp,
        fontFamily = style.fontFamily,
        modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
    )

    Label("Where from")
    FontPicker(
        selected = style.fontId,
        onPick = { onStyle(style.copy(fontId = it)) },
        googleOk = googleOk,
        onAddFile = onPickFile
    )
}

@Composable
fun FontRow(
    name: String,
    selected: Boolean,
    family: FontFamily?,
    source: FontSource? = null,
    alsoIn: List<FontSource> = emptyList(),
    trailing: String = "In use",
    onRemove: (() -> Unit)? = null,
    onSave: (() -> Unit)? = null,
    busy: Boolean = false,
    kept: Boolean = false,
    failed: Boolean = false,
    onClick: () -> Unit
) {
    val ink = LocalInk.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(ink.copy(alpha = if (selected) 0.16f else 0.05f))
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = ink,
                fontSize = 17.sp,
                fontFamily = family,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val note = buildList {
                source?.let { add(it.label) }
                if (alsoIn.isNotEmpty()) add("also in ${alsoIn.joinToString { it.label }}")
            }.joinToString(" + ")
            if (note.isNotEmpty()) {
                Text(note, color = ink.copy(alpha = 0.45f), fontSize = 11.sp)
            }
        }
        if (selected) {
            Text(trailing, color = ink.copy(alpha = 0.5f), fontSize = 12.sp)
        }
        onRemove?.let {
            TextButton(onClick = it) { Text("Remove", fontSize = 12.sp) }
        }
        onSave?.let {
            // a finished save used to drop straight back to "Keep", which reads as though
            // nothing happened. The three outcomes now each say so.
            val word = when {
                busy -> "Saving"
                kept -> "Kept"
                failed -> "Retry"
                else -> "Keep"
            }
            TextButton(onClick = it, enabled = !busy && !kept) {
                Text(
                    word,
                    fontSize = 12.sp,
                    color = if (kept) ink.copy(alpha = 0.45f) else Color.Unspecified
                )
            }
        }
    }
}
