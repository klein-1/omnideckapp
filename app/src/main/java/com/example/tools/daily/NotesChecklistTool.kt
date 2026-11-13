package com.example.tools.daily

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.design.ChapterHue
import com.example.design.DotkitType
import com.example.design.LocalDotkitLook
import com.example.design.PixelBox
import com.example.design.PixelButton
import com.example.design.PixelButtonVariant
import com.example.design.PixelChip
import com.example.design.PixelNumberField
import com.example.design.PixelStamp
import com.example.design.PixelSwitch
import com.example.design.PixelTextField
import com.example.design.ToolStateBanner
import com.example.design.ToolStateKind
import com.example.registry.DataKind
import com.example.registry.ToolMeta
import com.example.services.storage.NoteEntity
import com.example.tools.common.ToolWorkbenchChrome

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NotesChecklistTool(
    meta: ToolMeta,
    initialText: String,
    notes: List<NoteEntity>,
    filterNoteType: String = "NOTE", // "NOTE", "TODO", "SHOPPING"
    isPinned: Boolean,
    onTogglePin: () -> Unit,
    onBack: () -> Unit,
    onCreateNote: (title: String, body: String, isChecklist: Boolean, noteType: String, metaTag: String) -> Unit,
    onUpdateNote: (NoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onSaveToFiles: (String, DataKind, String) -> Unit,
    onSendToTool: (String, String) -> Unit,
    onStatusToast: (String) -> Unit
) {
    val look = LocalDotkitLook.current
    val palette = look.palette
    val hue = ChapterHue.TANGERINE
    val currency = look.regionFormat.currencySymbol

    var noteSubTab by remember(filterNoteType) { mutableStateOf("NOTES") } // NOTES, FLASHCARDS, KIT

    val filteredNotes = remember(notes, filterNoteType) {
        notes.filter {
            it.noteType == filterNoteType && (filterNoteType != "NOTE" || it.metaTag != "FLASHCARD")
        }
    }
    val flashcardNotes = remember(notes) {
        notes.filter { it.noteType == "NOTE" && it.metaTag == "FLASHCARD" }
    }

    var selectedId by remember(filteredNotes.size, filterNoteType) {
        mutableLongStateOf(filteredNotes.firstOrNull()?.id ?: 0L)
    }
    val activeNote = filteredNotes.firstOrNull { it.id == selectedId } ?: filteredNotes.firstOrNull()

    var showNewForm by remember(filterNoteType) { mutableStateOf(initialText.isNotBlank()) }
    var newTitle by remember(filterNoteType) { mutableStateOf(if (initialText.isNotBlank()) "Imported" else "") }
    var newBody by remember(initialText, filterNoteType) { mutableStateOf(initialText) }
    var newIsChecklist by remember(filterNoteType) { mutableStateOf(filterNoteType != "NOTE") }
    var newPriority by remember { mutableStateOf("MED") }

    var editTitle by remember(activeNote?.id) { mutableStateOf(activeNote?.title.orEmpty()) }
    var editBody by remember(activeNote?.id) { mutableStateOf(activeNote?.body.orEmpty()) }
    var newChecklistItem by remember { mutableStateOf("") }
    var newShopPrice by remember { mutableStateOf("") }
    var todoFilter by remember { mutableStateOf("ALL") } // ALL, OPEN, DONE

    LaunchedEffect(activeNote?.updatedAt, activeNote?.id) {
        if (activeNote != null) {
            editTitle = activeNote.title
            editBody = activeNote.body
        }
    }

    val exportMarkdown = remember(activeNote, editTitle, editBody) {
        if (activeNote == null) "" else "# $editTitle\n\n$editBody"
    }

    val modeLabel = when (filterNoteType) {
        "TODO" -> "TASKS"
        "SHOPPING" -> "SHOPPING LISTS"
        else -> "NOTES"
    }

    ToolWorkbenchChrome(
        meta = meta,
        isPinned = isPinned,
        onTogglePin = onTogglePin,
        onBack = onBack,
        outputText = exportMarkdown,
        outputFileName = "${filterNoteType.lowercase()}_${activeNote?.id ?: 0}.txt",
        outputKind = DataKind.TEXT,
        onSaveToFiles = onSaveToFiles,
        onSendToTool = onSendToTool,
        onStatusToast = onStatusToast,
        helpParagraph = when (filterNoteType) {
            "TODO" -> "Manage persistent tasks with priority badges (HIGH/MED/LOW), status filters, and single-tap completion in local SQLite."
            "SHOPPING" -> "Build grocery & shopping lists with optional @price tags (e.g. Milk @65) to automatically sum your basket total."
            else -> "Create persistent offline notes, interactive spaced Flashcard decks, Team splitters & APA/MLA/Chicago citations."
        }
    ) {
        if (filterNoteType == "NOTE") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("NOTES" to "Notes", "FLASHCARDS" to "Flashcards (${flashcardNotes.size})", "KIT" to "Teams & Citations").forEach { (k, lbl) ->
                    PixelChip(lbl, selected = noteSubTab == k, chapterHue = hue, onClick = { noteSubTab = k })
                }
            }
        }

        if (filterNoteType == "NOTE" && noteSubTab == "FLASHCARDS") {
            var fcQuestion by remember { mutableStateOf("") }
            var fcAnswer by remember { mutableStateOf("") }
            var fcIndex by remember { mutableIntStateOf(0) }
            var fcRevealed by remember { mutableStateOf(false) }
            val currentCard = flashcardNotes.getOrNull(fcIndex.coerceIn(0, (flashcardNotes.size - 1).coerceAtLeast(0)))

            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("ADD FLASHCARD (SAVED TO SQLITE)", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(fcQuestion, { fcQuestion = it }, "Front (Question / Prompt)…", chapterHue = hue)
                    PixelTextField(fcAnswer, { fcAnswer = it }, "Back (Answer / Definition)…", chapterHue = hue)
                    PixelButton(
                        text = "+ Save Flashcard",
                        chapterHue = hue,
                        enabled = fcQuestion.isNotBlank() && fcAnswer.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onCreateNote(fcQuestion.trim(), fcAnswer.trim(), false, "NOTE", "FLASHCARD")
                            fcQuestion = ""; fcAnswer = ""
                            onStatusToast("Flashcard saved.")
                        }
                    )
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    if (currentCard == null) {
                        Text("Create flashcards above to study interactively.", style = DotkitType.bodyUi(13), color = palette.ink2)
                    } else {
                        Text("CARD ${(fcIndex % flashcardNotes.size) + 1} OF ${flashcardNotes.size}", style = DotkitType.monoLabel(10), color = palette.ink2)
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.paper).border(1.5.dp, palette.ink, RoundedCornerShape(10.dp)).clickable { fcRevealed = !fcRevealed }.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Q: ${currentCard.title}", style = DotkitType.bodyUi(16, bold = true), color = palette.ink)
                            if (fcRevealed) {
                                HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                                Text("A: ${currentCard.body}", style = DotkitType.bodyUi(15), color = palette.chapterColor(hue))
                            } else {
                                Text("Tap card to reveal answer", style = DotkitType.monoLabel(10), color = palette.ink3)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PixelButton("Prev", variant = PixelButtonVariant.OUTLINE, chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                fcIndex = (fcIndex - 1 + flashcardNotes.size) % flashcardNotes.size; fcRevealed = false
                            })
                            PixelButton("Next", chapterHue = hue, modifier = Modifier.weight(1f), onClick = {
                                fcIndex = (fcIndex + 1) % flashcardNotes.size; fcRevealed = false
                            })
                            PixelButton("Del", variant = PixelButtonVariant.DESTRUCTIVE, onClick = {
                                onDeleteNote(currentCard.id); fcIndex = 0; fcRevealed = false
                            })
                        }
                    }
                }
            }
        } else if (filterNoteType == "NOTE" && noteSubTab == "KIT") {
            var rosterRaw by remember { mutableStateOf("") }
            var teamCountStr by remember { mutableStateOf("2") }
            var shuffledGroups by remember { mutableStateOf<List<List<String>>>(emptyList()) }

            var citeAuthor by remember { mutableStateOf("") }
            var citeYear by remember { mutableStateOf("") }
            var citeTitle by remember { mutableStateOf("") }
            var citePub by remember { mutableStateOf("") }
            var citeStyle by remember { mutableStateOf("APA") }

            val citationOut = remember(citeAuthor, citeYear, citeTitle, citePub, citeStyle) {
                val a = citeAuthor.ifBlank { "Author, A." }
                val y = citeYear.ifBlank { "2026" }
                val t = citeTitle.ifBlank { "Title of Work" }
                val p = citePub.ifBlank { "Publisher" }
                when (citeStyle) {
                    "APA" -> "$a ($y). $t. $p."
                    "MLA" -> "$a. \"$t.\" $p, $y."
                    else -> "$a. $y. \"$t.\" $p."
                }
            }
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RANDOM TEAM SPLITTER", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelTextField(rosterRaw, { rosterRaw = it }, "Names (one per line or comma-separated)…", singleLine = false, minHeight = 76.dp, chapterHue = hue)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PixelNumberField(teamCountStr, { teamCountStr = it }, "Groups", allowDecimal = false, chapterHue = hue, modifier = Modifier.weight(0.7f))
                        PixelButton("Split Teams", chapterHue = hue, modifier = Modifier.weight(1.3f), onClick = {
                            val names = rosterRaw.split("\n", ",").map { it.trim() }.filter { it.isNotEmpty() }.shuffled()
                            val n = (teamCountStr.toIntOrNull() ?: 2).coerceIn(1, names.size.coerceAtLeast(1))
                            val groups = List(n) { mutableListOf<String>() }
                            names.forEachIndexed { idx, s -> groups[idx % n].add(s) }
                            shuffledGroups = groups
                        })
                    }
                    shuffledGroups.forEachIndexed { i, grp ->
                        Text("Team ${i + 1}: ${grp.joinToString(", ")}", style = DotkitType.monoData(12), color = palette.ink)
                    }
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    Text("ACADEMIC CITATION FORMATTER", style = DotkitType.monoLabel(10), color = palette.ink2)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("APA", "MLA", "Chicago").forEach { st ->
                            PixelChip(st, selected = citeStyle == st, chapterHue = hue, onClick = { citeStyle = st })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelTextField(citeAuthor, { citeAuthor = it }, "Author", chapterHue = hue, modifier = Modifier.weight(1.3f))
                        PixelTextField(citeYear, { citeYear = it }, "Year", chapterHue = hue, modifier = Modifier.weight(0.6f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PixelTextField(citeTitle, { citeTitle = it }, "Title", chapterHue = hue, modifier = Modifier.weight(1f))
                        PixelTextField(citePub, { citePub = it }, "Source / Publisher", chapterHue = hue, modifier = Modifier.weight(1f))
                    }
                    Text(citationOut, style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                }
            }
        } else {
        PixelBox {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("$modeLabel (${filteredNotes.size})", style = DotkitType.monoLabel(10), color = palette.ink2)
                    PixelChip(
                        label = if (showNewForm) "✕" else "+ New",
                        selected = showNewForm,
                        chapterHue = hue,
                        onClick = { showNewForm = !showNewForm }
                    )
                }

                if (filteredNotes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        filteredNotes.forEach { n ->
                            val prefix = buildString {
                                if (n.pinned) append("★ ")
                                if (filterNoteType == "TODO" && n.metaTag.isNotBlank()) append("[${n.metaTag}] ")
                            }
                            PixelChip(
                                label = "$prefix${n.title.ifBlank { "Untitled" }}",
                                selected = n.id == activeNote?.id,
                                chapterHue = hue,
                                onClick = { selectedId = n.id }
                            )
                        }
                    }
                } else if (!showNewForm) {
                    ToolStateBanner(
                        kind = ToolStateKind.EMPTY,
                        title = "No ${modeLabel.lowercase()} yet",
                        detail = "Tap + New to create your first local record.",
                        chapterHue = hue,
                        actionLabel = "+ Create",
                        onAction = { showNewForm = true }
                    )
                }

                if (showNewForm) {
                    HorizontalDivider(thickness = 1.dp, color = palette.hairline)
                    PixelTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = when (filterNoteType) {
                            "TODO" -> "Task group title (e.g. Sprint, Errands)…"
                            "SHOPPING" -> "List title (e.g. Groceries, Hardware)…"
                            else -> "Note title…"
                        },
                        chapterHue = hue
                    )
                    if (filterNoteType == "TODO") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("HIGH", "MED", "LOW").forEach { pri ->
                                PixelChip(
                                    label = pri,
                                    selected = newPriority == pri,
                                    chapterHue = hue,
                                    onClick = { newPriority = pri }
                                )
                            }
                        }
                    }
                    PixelTextField(
                        value = newBody,
                        onValueChange = { newBody = it },
                        placeholder = if (filterNoteType == "SHOPPING") {
                            "Items one per line (add @price like 'Coffee @320')…"
                        } else {
                            "Items or note text…"
                        },
                        singleLine = false,
                        minHeight = 84.dp,
                        chapterHue = hue
                    )
                    if (filterNoteType == "NOTE") {
                        PixelSwitch("Format as Checklist", newIsChecklist, { newIsChecklist = it }, hue)
                    }
                    PixelButton(
                        text = "Save",
                        chapterHue = hue,
                        onClick = {
                            val title = newTitle.trim().ifEmpty { "${modeLabel.dropLast(1)} ${filteredNotes.size + 1}" }
                            val rawBody = newBody.trim()
                            val checklist = filterNoteType != "NOTE" || newIsChecklist
                            val formattedBody = if (checklist && rawBody.isNotEmpty()) {
                                rawBody.lines().filter { it.isNotBlank() }.joinToString("\n") { line ->
                                    val t = line.trim()
                                    if (t.startsWith("[x] ") || t.startsWith("[ ] ")) t else "[ ] $t"
                                }
                            } else rawBody
                            val tag = if (filterNoteType == "TODO") newPriority else ""
                            onCreateNote(title, formattedBody, checklist, filterNoteType, tag)
                            newTitle = ""
                            newBody = ""
                            showNewForm = false
                            onStatusToast("Created '$title'.")
                        }
                    )
                }
            }
        }

        if (activeNote != null) {
            PixelBox {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PixelChip(
                                label = if (activeNote.pinned) "★" else "☆",
                                selected = activeNote.pinned,
                                chapterHue = hue,
                                onClick = {
                                    onUpdateNote(activeNote.copy(pinned = !activeNote.pinned, updatedAt = System.currentTimeMillis()))
                                }
                            )
                            if (filterNoteType == "NOTE") {
                                PixelChip(
                                    label = if (activeNote.isChecklist) "Checklist" else "Plain Note",
                                    selected = activeNote.isChecklist,
                                    chapterHue = hue,
                                    onClick = {
                                        val toggled = !activeNote.isChecklist
                                        val nextBody = if (toggled) {
                                            editBody.lines().filter { it.isNotBlank() }.joinToString("\n") { line ->
                                                val t = line.trim()
                                                if (t.startsWith("[x] ") || t.startsWith("[ ] ")) t else "[ ] $t"
                                            }
                                        } else {
                                            editBody.lines().joinToString("\n") { line ->
                                                line.removePrefix("[x] ").removePrefix("[ ] ")
                                            }
                                        }
                                        editBody = nextBody
                                        onUpdateNote(activeNote.copy(isChecklist = toggled, body = nextBody, updatedAt = System.currentTimeMillis()))
                                    }
                                )
                            } else if (filterNoteType == "TODO") {
                                listOf("HIGH", "MED", "LOW").forEach { pri ->
                                    PixelChip(
                                        label = pri,
                                        selected = activeNote.metaTag == pri,
                                        chapterHue = hue,
                                        onClick = {
                                            onUpdateNote(activeNote.copy(metaTag = pri, updatedAt = System.currentTimeMillis()))
                                        }
                                    )
                                }
                            }
                        }
                        PixelChip(
                            label = "Delete",
                            chapterHue = ChapterHue.ROSE,
                            onClick = {
                                onDeleteNote(activeNote.id)
                                onStatusToast("Deleted '${activeNote.title}'.")
                            }
                        )
                    }

                    PixelTextField(
                        value = editTitle,
                        onValueChange = {
                            editTitle = it
                            onUpdateNote(activeNote.copy(title = it, body = editBody, updatedAt = System.currentTimeMillis()))
                        },
                        placeholder = "Title…",
                        chapterHue = hue
                    )

                    if (activeNote.isChecklist) {
                        val allLines = editBody.lines().filter { it.isNotBlank() }
                        val doneCount = allLines.count { it.trimStart().startsWith("[x]", ignoreCase = true) }

                        if (filterNoteType == "SHOPPING") {
                            val totalBasket = allLines.sumOf { extractLinePrice(it) }
                            val remainingBasket = allLines.filter { !it.trimStart().startsWith("[x]", ignoreCase = true) }
                                .sumOf { extractLinePrice(it) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(palette.paper)
                                    .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("BASKET TOTAL: $currency${"%.2f".format(totalBasket)}", style = DotkitType.monoData(12), color = palette.ink)
                                Text("LEFT: $currency${"%.2f".format(remainingBasket)}", style = DotkitType.monoData(12), color = palette.chapterColor(hue))
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("ALL", "OPEN", "DONE").forEach { f ->
                                    PixelChip(label = f, selected = todoFilter == f, chapterHue = hue, onClick = { todoFilter = f })
                                }
                            }
                            PixelStamp("$doneCount/${allLines.size}", if (allLines.isNotEmpty() && doneCount == allLines.size) palette.ok else palette.ink2)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            allLines.forEachIndexed { idx, line ->
                                val trimmed = line.trimStart()
                                val checked = trimmed.startsWith("[x]", ignoreCase = true)
                                if (todoFilter == "OPEN" && checked) return@forEachIndexed
                                if (todoFilter == "DONE" && !checked) return@forEachIndexed
                                val labelText = trimmed
                                    .removePrefix("[x] ").removePrefix("[X] ")
                                    .removePrefix("[ ] ").trim()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (checked) palette.paper3 else palette.paper)
                                        .border(1.dp, palette.hairline, RoundedCornerShape(6.dp))
                                        .clickable {
                                            val updatedLines = allLines.toMutableList()
                                            updatedLines[idx] = (if (checked) "[ ] " else "[x] ") + labelText
                                            val nextBody = updatedLines.joinToString("\n")
                                            editBody = nextBody
                                            onUpdateNote(activeNote.copy(body = nextBody, updatedAt = System.currentTimeMillis()))
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (checked) "✓" else "○",
                                        style = DotkitType.monoData(14),
                                        color = if (checked) palette.ok else palette.ink2
                                    )
                                    Text(
                                        text = labelText,
                                        style = DotkitType.bodyUi(14, bold = !checked),
                                        color = if (checked) palette.ink3 else palette.ink,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "✕",
                                        style = DotkitType.monoLabel(12),
                                        color = palette.ink3,
                                        modifier = Modifier
                                            .clickable {
                                                val updatedLines = allLines.toMutableList().apply { removeAt(idx) }
                                                val nextBody = updatedLines.joinToString("\n")
                                                editBody = nextBody
                                                onUpdateNote(activeNote.copy(body = nextBody, updatedAt = System.currentTimeMillis()))
                                            }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelTextField(
                                value = newChecklistItem,
                                onValueChange = { newChecklistItem = it },
                                placeholder = if (filterNoteType == "SHOPPING") "Item name…" else "Add item…",
                                chapterHue = hue,
                                modifier = Modifier.weight(1f)
                            )
                            if (filterNoteType == "SHOPPING") {
                                PixelTextField(
                                    value = newShopPrice,
                                    onValueChange = { newShopPrice = it },
                                    placeholder = "$currency",
                                    chapterHue = hue,
                                    modifier = Modifier.weight(0.45f)
                                )
                            }
                            PixelButton(
                                text = "+",
                                chapterHue = hue,
                                enabled = newChecklistItem.isNotBlank(),
                                onClick = {
                                    val priceSuffix = newShopPrice.trim().toDoubleOrNull()?.let { " @${"%.2f".format(it)}" } ?: ""
                                    val clean = newChecklistItem.trim() + priceSuffix
                                    val nextBody = if (editBody.isBlank()) "[ ] $clean" else "$editBody\n[ ] $clean"
                                    editBody = nextBody
                                    newChecklistItem = ""
                                    newShopPrice = ""
                                    onUpdateNote(activeNote.copy(body = nextBody, updatedAt = System.currentTimeMillis()))
                                }
                            )
                        }
                    } else {
                        PixelTextField(
                            value = editBody,
                            onValueChange = {
                                editBody = it
                                onUpdateNote(activeNote.copy(title = editTitle, body = it, updatedAt = System.currentTimeMillis()))
                            },
                            placeholder = "Write your note…",
                            singleLine = false,
                            minHeight = 160.dp,
                            chapterHue = hue
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun extractLinePrice(line: String): Double {
    val m = Regex("@([0-9]+(?:\\.[0-9]+)?)").find(line) ?: return 0.0
    return m.groupValues[1].toDoubleOrNull() ?: 0.0
}
