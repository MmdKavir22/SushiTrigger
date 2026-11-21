package com.sushitriggers.ctjs.gui

import com.sushitriggers.ctjs.Reference
import com.sushitriggers.ctjs.commands.Command
import com.sushitriggers.ctjs.engine.langs.js.JSLoader
import com.sushitriggers.ctjs.engine.module.ModuleManager
import com.sushitriggers.ctjs.engine.module.ScriptRepo
import com.sushitriggers.ctjs.utils.Config
import com.sushitriggers.ctjs.utils.Refl
import com.sushitriggers.ctjs.utils.console.ActivityLog
import com.sushitriggers.ctjs.utils.console.CTLog
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import java.io.File
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

private enum class Tab(val title: String) {
    HOME("Home"),
    SCRIPTS("Scripts"),
    MODULES("Modules"),
    TERMINAL("Terminal"),
    EVENTS("Events"),
    EDITOR("Editor"),
    SETTINGS("Settings"),
    INFO("Info"),
}

private class Row(
    val text: String,
    val onClick: (() -> Unit)? = null,
    val side: String? = null,
    val onSide: (() -> Unit)? = null,
    val input: Input? = null,
)

private class Input(val id: String, val hint: String, val apply: (String) -> Unit, val button: String = "Set")

class ScriptGui : Screen(Component.literal("SushiTriggers")) {
    override fun init() {
        build()
    }

    fun rebuild() {
        flush()
        clearWidgets()
        build()
    }

    private var panelX = 0
    private var panelW = 0

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        val left = panelX - 8
        val right = panelX + panelW + 8
        val contentX = panelX + sidebar + 4

        graphics.fill(left, 2, right, height - 2, 0x4D140A26)
        graphics.outline(left, 2, right - left, height - 4, 0x66D9B8FF)

        graphics.fill(left + 1, 3, right - 1, 28, 0x33FFFFFF)
        graphics.fill(left + 1, 3, right - 1, 4, 0x55FFFFFF)
        graphics.text(font, "SUSHITRIGGERS", panelX, 11, 0xFFF0DCFF.toInt(), true)
        graphics.text(font, "v${Reference.MODVERSION}  |  ${Tab.values()[activeIndex].title}", panelX + 100, 11, 0xCCCDA6F5.toInt(), true)

        graphics.fill(left + 1, 28, right - 1, 29, 0xAAB362FF.toInt())

        graphics.fill(panelX - 4, 34, panelX + sidebar + 4, height - 54, 0x22FFFFFF)
        graphics.fill(panelX - 4, 36 + activeIndex * 22, panelX - 1, 56 + activeIndex * 22, 0xFFB362FF.toInt())
        graphics.fill(contentX, 34, contentX + 1, height - 54, 0x44D9B8FF)

        graphics.fill(left + 1, height - 52, right - 1, height - 3, 0x33000000)

        if (tab == Tab.TERMINAL) drawTerminal(graphics)

        super.extractRenderState(graphics, mouseX, mouseY, partialTick)
    }


    private var termX = 0
    private var termW = 0
    private var termTop = 56
    private var termBottom = 100

    private fun termFilterName() = listOf("ALL", "ERRORS", "EVENTS", "LOG")[termFilter]

    private fun termLines(): List<String> {
        val search = termSearch.trim()
        return CTLog.last(500).filter {
            when (termFilter) {
                1 -> "[ERROR]" in it || "[WARN]" in it
                2 -> "[EVENT]" in it
                3 -> "[EVENT]" !in it
                else -> true
            }
        }.filter { search.isEmpty() || it.contains(search, ignoreCase = true) }
    }

    private fun termColor(line: String): Int = when {
        "[ERROR]" in line -> 0xFF5555
        "[WARN]" in line -> 0xFFFF55
        "[TERM] >" in line -> 0x55FF55
        "[TERM]" in line -> 0xAAFFAA
        "[EVENT] CHAT" in line -> 0xFFAA00
        "[EVENT]" in line -> 0x55FFFF
        else -> 0xCCCCCC
    }

    private fun termClean(line: String, max: Int): String {
        var t = line.replace("[INFO] ", "").replace('§', '?').replace("\t", "  ")
        while (t.length > 4 && com.sushitriggers.ctjs.minecraft.libs.HudOverlay.textWidth(t) > max) {
            t = t.take(t.length - maxOf(1, (t.length - max / 6) / 2).coerceAtLeast(1))
        }
        return t
    }

    private fun termOut(text: String) = CTLog.add(com.sushitriggers.ctjs.utils.console.LogType.INFO, "[TERM] $text")

    private fun termRun(raw: String) {
        val command = raw.trim()
        if (command.isEmpty()) return
        termOut("> $command")
        termScroll = 0

        val parts = command.split(Regex("\\s+"), limit = 2)
        val name = parts[0].lowercase()
        val arg = parts.getOrNull(1)?.trim() ?: ""

        when (name) {
            "help" -> {
                termOut("help | clear | dump | rec | reload | find <text> | filter all|err|evt|log")
                termOut("events on|off | cats | hide <category> | show <category> | js <code>")
            }
            "clear" -> CTLog.clear()
            "dump" -> termOut(CTLog.dump()?.let { "dumped: ${it.path}" } ?: "dump failed")
            "rec" -> com.sushitriggers.ctjs.utils.console.Recorder.toggle()
            "reload" -> {
                Reference.loadCT()
                termOut("reloading scripts...")
            }
            "find" -> {
                termSearch = arg
                termOut(if (arg.isEmpty()) "search cleared" else "searching: $arg")
            }
            "filter" -> {
                termFilter = when (arg.lowercase()) {
                    "err", "errors" -> 1
                    "evt", "events" -> 2
                    "log" -> 3
                    else -> 0
                }
                termOut("filter: ${termFilterName()}")
            }
            "events" -> {
                Config.logEvents = arg.lowercase() != "off"
                Config.save()
                termOut("event log: " + if (Config.logEvents) "on" else "off")
            }
            "cats" -> ActivityLog.categories.forEach {
                termOut((if (it in Config.disabledEventCategories) "[off] " else "[on]  ") + it + " - " + (ActivityLog.categoryHelp[it] ?: ""))
            }
            "hide", "show" -> {
                val category = arg.lowercase()
                if (category !in ActivityLog.categories) {
                    termOut("unknown category, use: cats")
                } else {
                    if (name == "hide") Config.disabledEventCategories.add(category) else Config.disabledEventCategories.remove(category)
                    Config.save()
                    termOut("$name $category")
                }
            }
            "js" -> {
                try {
                    termOut("= " + JSLoader.eval(arg))
                } catch (e: Throwable) {
                    termOut("JS error: ${e.message}")
                }
            }
            else -> termOut("unknown command: $name (type help)")
        }
    }

    private fun buildTerminal(x: Int, w: Int) {
        termX = x
        termW = w
        termTop = 56
        termBottom = height - 82

        val quarter = (w - 6) / 4
        addRenderableWidget(action("Filter: " + termFilterName(), x, 32, quarter) {
            termFilter = (termFilter + 1) % 4
            termScroll = 0
            rebuild()
        })
        addRenderableWidget(action(if (CTLog.recording) "Stop rec" else "Record", x + quarter + 2, 32, quarter) {
            com.sushitriggers.ctjs.utils.console.Recorder.toggle()
            rebuild()
        })
        addRenderableWidget(action("Dump", x + (quarter + 2) * 2, 32, quarter) {
            val f = CTLog.dump()
            status = if (f != null) "Dumped: ${f.path}" else "Dump failed"
            rebuild()
        })
        addRenderableWidget(action("Clear", x + (quarter + 2) * 3, 32, quarter) {
            CTLog.clear()
            termScroll = 0
            rebuild()
        })

        val box = makeBox(x, height - 78, w - 74, 20, inputs["term"] ?: "", "> type a command (help)")
        if (box != null) {
            boxes["term"] = box
            addRenderableWidget(box)
        }
        addRenderableWidget(action("Run", x + w - 70, height - 78, 70) {
            flush()
            val c = inputs["term"] ?: ""
            inputs["term"] = ""
            termRun(c)
            rebuild()
        })

        addRenderableWidget(label(status, x, height - 52, w))

        val third = (w - 80 - 6) / 3
        addRenderableWidget(action("Older", x, height - 24, third) { termScroll += 8; rebuild() })
        addRenderableWidget(action("Newer", x + third + 2, height - 24, third) { termScroll = maxOf(0, termScroll - 8); rebuild() })
        addRenderableWidget(action("Latest", x + (third + 2) * 2, height - 24, third) { termScroll = 0; rebuild() })
        addRenderableWidget(action("Close", x + w - 76, height - 24, 76) { show(null) })
    }

    private fun drawTerminal(graphics: GuiGraphicsExtractor) {
        val x = termX
        val w = termW
        graphics.fill(x, termTop, x + w, termBottom, 0xB0000000.toInt())
        graphics.outline(x, termTop, w, termBottom - termTop, 0x6655FF55)

        val lines = termLines()
        val visible = maxOf(1, (termBottom - termTop - 6) / 10)
        val end = (lines.size - termScroll).coerceIn(0, lines.size)
        val start = maxOf(0, end - visible)

        for (i in start until end) {
            val line = lines[i]
            graphics.text(font, termClean(line, w - 10), x + 4, termTop + 3 + (i - start) * 10, termColor(line) or (0xFF shl 24), false)
        }

        val info = "${lines.size} lines" + (if (termScroll > 0) " | scrolled $termScroll" else "") +
                (if (termSearch.isNotEmpty()) " | find: $termSearch" else "") + (if (CTLog.recording) " | REC" else "")
        graphics.text(font, info, x + 4, termBottom + 4, 0xFF88AA88.toInt(), false)
    }

    private fun homeRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val counts = JSLoader.triggerCounts()
        val modules = ModuleManager.modulesFolder.listFiles()?.count { it.isDirectory } ?: 0
        val commands = Command.activeCommands.keys.sorted()

        rows += header("WELCOME TO SUSHITRIGGERS")
        rows += Row("§7Scripts: " + (if (Reference.isLoaded) "§a● loaded" else "§c○ not loaded") +
                "   §7Modules: §f$modules   §7Triggers: §f${counts.values.sum()}")
        rows += Row("§7Event log: " + (if (Config.logEvents) "§aON" else "§cOFF") +
                "   §7Hidden categories: §f${Config.disabledEventCategories.size}" +
                "   §7Recording: " + (if (CTLog.recording) "§c● ${CTLog.recordedCount()} lines" else "§7off"))
        rows += Row("§7Script commands: §f" + (if (commands.isEmpty()) "none" else commands.joinToString(", ") { "/$it" }))
        rows += Row("§7GitHub repo: §f${ScriptRepo.spec()}   §7Token: " + (if (ScriptRepo.hasToken()) "§aset" else "§cnot set"))

        rows += header("QUICK ACTIONS")
        rows += Row("§a⟳ Reload all scripts", {
            status = "Reloading scripts..."
            Reference.loadCT()
            rebuild()
        })
        rows += Row("§eOpen modules folder", { openModulesFolder(); rebuild() })
        rows += Row("§b✎ Open code editor", { tab = Tab.EDITOR; page = 0; rebuild() })
        rows += Row("§b☁ Import scripts from GitHub", { tab = Tab.SCRIPTS; page = 0; rebuild() })
        rows += Row(if (CTLog.recording) "§c■ Stop recording log" else "§a● Start recording log", {
            com.sushitriggers.ctjs.utils.console.Recorder.toggle()
            rebuild()
        })
        rows += Row("§e⎙ Dump log to file", {
            val f = CTLog.dump()
            status = if (f != null) "Dumped: ${f.path}" else "Dump failed"
            rebuild()
        })

        rows += header("TIPS")
        rows += Row("§7• Tabs: Scripts, Modules, Terminal, Events, Editor, Settings, Info")
        rows += Row("§7• Record key: §f${com.sushitriggers.ctjs.utils.console.Recorder.keyName(Config.recordKey)} §7(works with no GUI open)")
        rows += Row("§7• /st opens this window, /st help lists all commands")
        return rows
    }

    private fun label(text: String, x: Int, y: Int, w: Int): Button {
        val button = Button.builder(Component.literal(text)) { }.bounds(x, y, w, 20).build()
        button.active = false
        return button
    }

    private fun action(text: String, x: Int, y: Int, w: Int, onPress: () -> Unit): Button {
        return Button.builder(Component.literal(text)) { onPress() }.bounds(x, y, w, 20).build()
    }

    private val sidebar = 96

    private fun build() {
        val w = minOf(width - 20, 560)
        boxes.clear()
        val x = (width - w) / 2
        panelX = x
        panelW = w

        val tabs = Tab.values()
        tabs.forEachIndexed { index, t ->
            val button = action((if (t == tab) "> " else "  ") + t.title, x, 36 + index * 22, sidebar) {
                tab = t
                page = 0
                rebuild()
            }
            button.active = t != tab
            addRenderableWidget(button)
        }
        activeIndex = tabs.indexOf(tab)

        buildContent(x + sidebar + 8, w - sidebar - 8)
    }

    private var activeIndex = 0

    private fun buildContent(x: Int, w: Int) {
        if (tab == Tab.TERMINAL) {
            buildTerminal(x, w)
            return
        }

        if (tab == Tab.EDITOR && editFile != null) {
            if (!buildEditorMulti(x, w)) buildEditor(x, w)
            return
        }

        val rows = when (tab) {
            Tab.HOME -> homeRows()
            Tab.EDITOR -> editorBrowserRows()
            Tab.SCRIPTS -> scriptRows()
            Tab.MODULES -> moduleRows()
            Tab.TERMINAL -> emptyList()
            Tab.EVENTS -> eventRows()
            Tab.SETTINGS -> settingRows()
            Tab.INFO -> infoRows()
        }

        val top = 32
        val perPage = maxOf(3, (height - top - 54) / 22)
        val pages = maxOf(1, (rows.size + perPage - 1) / perPage)
        if (page >= pages) page = pages - 1
        if (page < 0) page = 0

        rows.drop(page * perPage).take(perPage).forEachIndexed { index, row ->
            val y = top + index * 22
            val onClick = row.onClick

            if (row.input != null) {
                val input = row.input
                val mainWidth = w - 74
                val box = makeBox(x, y, mainWidth, 20, inputs[input.id] ?: "", input.hint)
                if (box != null) {
                    boxes[input.id] = box
                    addRenderableWidget(box)
                } else {
                    addRenderableWidget(label("(text box unavailable)", x, y, mainWidth))
                }
                addRenderableWidget(action(input.button, x + mainWidth + 4, y, 70) {
                    flush()
                    input.apply(inputs[input.id] ?: "")
                    rebuild()
                })
            } else if (row.side != null) {
                val mainWidth = w - 74
                addRenderableWidget(if (onClick != null) action(row.text, x, y, mainWidth) { onClick() } else label(row.text, x, y, mainWidth))
                addRenderableWidget(action(row.side, x + mainWidth + 4, y, 70) { row.onSide?.invoke() })
            } else {
                addRenderableWidget(if (onClick != null) action(row.text, x, y, w) { onClick() } else label(row.text, x, y, w))
            }
        }

        addRenderableWidget(label(status, x, height - 48, w))

        val pageWidth = w - 40 - 40 - 70 - 12
        addRenderableWidget(action("<", x, height - 24, 40) {
            page--
            rebuild()
        })
        addRenderableWidget(label("Page ${page + 1}/$pages (${rows.size} rows)", x + 44, height - 24, pageWidth))
        addRenderableWidget(action(">", x + 44 + pageWidth + 4, height - 24, 40) {
            page++
            rebuild()
        })
        addRenderableWidget(action("Close", x + w - 70, height - 24, 70) { show(null) })
    }


    private val boxes = mutableMapOf<String, Any>()

    private fun makeBox(x: Int, y: Int, w: Int, h: Int, value: String, hint: String): EditBox? {
        return try {
            val mc = Minecraft.getInstance()
            val font = Refl.get(mc, "font")
            val ctor = EditBox::class.java.constructors.first {
                it.parameterCount == 6 && it.parameterTypes[5].name.contains("Component")
            }
            val box = ctor.newInstance(font, x, y, w, h, Component.literal(hint)) as EditBox
            Refl.callArg(box, "setMaxLength", 4096)
            Refl.callArg(box, "setValue", value)
            Refl.callArg(box, "setHint", Component.literal("§7$hint"))
            box
        } catch (e: Throwable) {
            CTLog.add(com.sushitriggers.ctjs.utils.console.LogType.WARN, "EditBox unavailable: $e")
            null
        }
    }

    private fun boxValue(box: Any?): String? = Refl.call(box, "getValue") as? String

    private fun flush() {
        boxes.forEach { (id, box) ->
            val v = boxValue(box) ?: return@forEach
            if (id == "multi") {
                editText = v
            } else if (id == "line") {
                if (editLine in editLines.indices && editLines[editLine] != v) {
                    editLines[editLine] = v
                    editDirty = true
                }
            } else inputs[id] = v
        }
    }

    private fun openModulesFolder() {
        try {
            val dir = ModuleManager.modulesFolder.absoluteFile.apply { mkdirs() }
            val os = System.getProperty("os.name").lowercase()
            val command = when {
                "win" in os -> listOf("explorer", dir.path)
                "mac" in os -> listOf("open", dir.path)
                else -> listOf("xdg-open", dir.path)
            }
            ProcessBuilder(command).start()
            status = "Opened modules folder"
        } catch (e: Throwable) {
            status = "Could not open folder: ${ModuleManager.modulesFolder.path}"
        }
    }

    private fun header(text: String) = Row("§6§l▌ $text")

    private fun clean(line: String, max: Int): String {
        val t = line.replace("\t", "  ").replace('§', '?')
        return if (t.length > max) t.take(max) + "…" else t
    }

    private fun buildEditor(x: Int, w: Int) {
        val file = editFile ?: return
        val quarter = (w - 6) / 4
        val dirtyMark = if (editDirty) "§e*" else ""

        fun save(): Boolean {
            flush()
            return try {
                file.writeText(editLines.joinToString("\n"))
                editDirty = false
                true
            } catch (e: Throwable) {
                status = "Save failed: $e"
                false
            }
        }

        // Back on the very top, tabs row is row 6
        addRenderableWidget(action("Save", x, 32, quarter) {
            if (save()) status = "Saved ${file.name}"
            rebuild()
        })
        addRenderableWidget(action("Save+Reload", x + quarter + 2, 32, quarter) {
            if (save()) {
                status = "Saved and reloading..."
                Reference.loadCT()
            }
            rebuild()
        })
        addRenderableWidget(action("Revert", x + (quarter + 2) * 2, 32, quarter) {
            openFile(file)
        })
        addRenderableWidget(action("Close", x + (quarter + 2) * 3, 32, quarter) {
            flush()
            editFile = null
            rebuild()
        })

        addRenderableWidget(label("$dirtyMark§f${file.name} §7line ${editLine + 1}/${editLines.size}", x, 56, w))

        val box = makeBox(x, 80, w, 20, if (editLine in editLines.indices) editLines[editLine] else "", "edit this line")
        if (box != null) {
            boxes["line"] = box
            addRenderableWidget(box)
        } else {
            addRenderableWidget(label("(text box unavailable)", x, 80, w))
        }

        val top = 106
        val perPage = maxOf(3, (height - top - 76) / 22)
        val start = (editLine - perPage / 2).coerceIn(0, maxOf(0, editLines.size - perPage))
        val maxChars = w / 6

        for (i in start until minOf(editLines.size, start + perPage)) {
            val current = i == editLine
            val text = (if (current) "§e" else "§7") + String.format("%3d ", i + 1) + "§f" + clean(editLines[i], maxChars)
            addRenderableWidget(action(text, x, top + (i - start) * 22, w) {
                flush()
                editLine = i
                rebuild()
            })
        }

        val y = height - 48 - 22
        val sixth = (w - 10) / 5
        addRenderableWidget(action("+ Below", x, y, sixth) {
            flush()
            editLines.add(editLine + 1, "")
            editLine++
            editDirty = true
            rebuild()
        })
        addRenderableWidget(action("- Delete", x + (sixth + 2), y, sixth) {
            flush()
            if (editLines.size > 1) editLines.removeAt(editLine) else editLines[0] = ""
            editLine = editLine.coerceAtMost(editLines.size - 1)
            editDirty = true
            rebuild()
        })
        addRenderableWidget(action("Up", x + (sixth + 2) * 2, y, sixth) {
            flush()
            editLine = maxOf(0, editLine - 1)
            rebuild()
        })
        addRenderableWidget(action("Down", x + (sixth + 2) * 3, y, sixth) {
            flush()
            editLine = minOf(editLines.size - 1, editLine + 1)
            rebuild()
        })
        addRenderableWidget(action("Dup", x + (sixth + 2) * 4, y, sixth) {
            flush()
            editLines.add(editLine + 1, editLines[editLine])
            editLine++
            editDirty = true
            rebuild()
        })

        addRenderableWidget(label(status, x, height - 48, w))
        addRenderableWidget(action("Close GUI", x + w - 80, height - 24, 80) { show(null) })
    }


    private fun makeMulti(x: Int, y: Int, w: Int, h: Int, value: String): Any? {
        return try {
            val cls = Class.forName("net.minecraft.client.gui.components.MultiLineEditBox")
            val font = Refl.get(Minecraft.getInstance(), "font")
            for (ctor in cls.constructors.sortedByDescending { it.parameterCount }) {
                try {
                    var ints = 0
                    val args = arrayOfNulls<Any>(ctor.parameterCount)
                    var ok = true
                    ctor.parameterTypes.forEachIndexed { i, t ->
                        args[i] = when {
                            t == Int::class.javaPrimitiveType -> {
                                ints++
                                when (ints) { 1 -> x; 2 -> y; 3 -> w; 4 -> h; else -> -1 }
                            }
                            t == Boolean::class.javaPrimitiveType -> true
                            t.name.endsWith("Component") -> Component.literal("")
                            t.name.endsWith("Font") -> font
                            else -> { ok = false; null }
                        }
                    }
                    if (!ok) continue
                    val box = ctor.newInstance(*args)
                    Refl.callArg(box, "setCharacterLimit", 1000000)
                    Refl.callArg(box, "setValue", value)
                    return box
                } catch (e: Throwable) {
                }
            }
            null
        } catch (e: Throwable) {
            null
        }
    }

    private fun filesOf(file: File): List<File> {
        val module = moduleOf(file) ?: return listOf(file)
        val folder = File(ModuleManager.modulesFolder, module)
        return folder.walk().filter { it.isFile && it.extension.lowercase() in editable }.sortedBy { it.path }.toList()
    }

    private fun buildEditorMulti(x: Int, w: Int): Boolean {
        val file = editFile ?: return false
        val sidebar = 120
        val boxX = x + sidebar + 4
        val boxW = w - sidebar - 4
        val boxY = 56
        val boxH = height - boxY - 54

        val box = makeMulti(boxX, boxY, boxW, boxH, editText) ?: return false
        boxes["multi"] = box
        addRenderableWidget(box as net.minecraft.client.gui.components.AbstractWidget)

        fun save(): Boolean {
            flush()
            return try {
                file.writeText(editText)
                editSaved = editText
                true
            } catch (e: Throwable) {
                status = "Save failed: $e"
                false
            }
        }

        val quarter = (w - 6) / 4
        addRenderableWidget(action("Save", x, 32, quarter) {
            if (save()) status = "Saved ${file.name}"
            rebuild()
        })
        addRenderableWidget(action("Save+Reload", x + quarter + 2, 32, quarter) {
            if (save()) {
                status = "Saved, reloading scripts..."
                Reference.loadCT()
            }
            rebuild()
        })
        addRenderableWidget(action("Revert", x + (quarter + 2) * 2, 32, quarter) { openFile(file) })
        addRenderableWidget(action("Close file", x + (quarter + 2) * 3, 32, quarter) {
            flush()
            editFile = null
            rebuild()
        })

        val dirty = editText != editSaved
        addRenderableWidget(label((if (dirty) "§e● " else "§a✔ ") + "§f${file.name}", x, boxY, sidebar))

        val perSide = maxOf(2, (height - boxY - 54 - 22 * 3) / 22)
        val files = filesOf(file)
        files.take(perSide).forEachIndexed { i, f ->
            val name = if (f == file) "§e▶ ${f.name}" else "§7  ${f.name}"
            addRenderableWidget(action(name, x, boxY + 22 + i * 22, sidebar) {
                flush()
                if (editText != editSaved) {
                    try { file.writeText(editText); editSaved = editText } catch (e: Throwable) {}
                }
                openFile(f)
            })
        }

        val inputY = height - 54 - 44
        val nb = makeBox(x, inputY, sidebar, 20, inputs["newFile"] ?: "", "new file")
        if (nb != null) {
            boxes["newFile"] = nb
            addRenderableWidget(nb)
        }
        addRenderableWidget(action("+ Create file", x, inputY + 22, sidebar) {
            flush()
            val raw = (inputs["newFile"] ?: "").trim().replace(Regex("[^A-Za-z0-9_.-]"), "")
            val module = moduleOf(file)
            if (raw.isEmpty() || module == null) {
                status = "Enter a file name"
            } else {
                val name = if (raw.contains('.')) raw else "$raw.js"
                val f = File(File(ModuleManager.modulesFolder, module), name)
                if (!f.exists()) f.writeText("")
                inputs["newFile"] = ""
                openFile(f)
            }
            rebuild()
        })

        addRenderableWidget(label(status, x, height - 48, w))
        addRenderableWidget(action("Close GUI", x + w - 80, height - 24, 80) { show(null) })
        return true
    }

    private fun moduleOf(file: File): String? {
        val rel = try {
            file.canonicalFile.relativeTo(ModuleManager.modulesFolder.canonicalFile).path
        } catch (e: Throwable) {
            return null
        }
        if (rel.startsWith("..")) return null
        return rel.substringBefore(File.separatorChar).takeIf { it.isNotBlank() }
    }

    private fun openFile(file: File) {
        editFile = file
        editLines = file.readText().split("\n").map { it.removeSuffix("\r") }.toMutableList()
        if (editLines.isEmpty()) editLines.add("")
        editLine = 0
        editDirty = false
        editText = file.readText().replace("\r\n", "\n")
        editSaved = editText
        status = "Opened ${file.name}"
        rebuild()
    }

    private fun createScript(raw: String) {
        val name = raw.trim().replace(Regex("[^A-Za-z0-9_-]"), "")
        if (name.isEmpty()) {
            status = "Enter a name (letters, digits, - _)"
            return
        }
        val folder = File(ModuleManager.modulesFolder, name)
        if (folder.exists()) {
            status = "$name already exists"
            return
        }
        folder.mkdirs()
        val js = File(folder, "$name.js")
        js.writeText("// $name\nregister(\"tick\", function () {\n    // runs every tick\n});\n")
        File(folder, "metadata.json").writeText(
            "{\n  \"name\": \"$name\",\n  \"version\": \"1.0.0\",\n  \"entry\": \"$name.js\",\n  \"creator\": \"\",\n  \"description\": \"\"\n}\n"
        )
        inputs["newScript"] = ""
        openFile(js)
        status = "Created $name"
    }

    private fun editorBrowserRows(): List<Row> {
        val rows = mutableListOf<Row>()
        rows += header("CODE EDITOR (local files only)")
        rows += Row("", input = Input("newScript", "new script name", { createScript(it) }, "Create"))
        rows += Row("§eOpen modules folder", { openModulesFolder(); rebuild() })
        rows += Row("§7Pick a file to edit. Changes are saved only on your computer.")

        val folders = ModuleManager.modulesFolder.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase() }
            ?: emptyList()
        folders.forEach { folder ->
            rows += Row("§b▸ ${folder.name}")
            folder.walk().filter { it.isFile && it.extension.lowercase() in editable }
                .sortedBy { it.path }.forEach { f ->
                    val rel = f.relativeTo(folder).path
                    rows += Row("§7   §f$rel §7(${f.length()} B)", { openFile(f) })
                }
        }
        return rows
    }

    private fun toggle(name: String, get: () -> Boolean, set: (Boolean) -> Unit): Row {
        return Row("$name: " + (if (get()) "§aON" else "§cOFF"), {
            set(!get())
            Config.save()
            rebuild()
        })
    }

    private fun scriptRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val names = ScriptRepo.cached

        rows += Row("§bRefresh list §7(${ScriptRepo.spec()})", { loadList() })
        rows += Row("§7Change repo with: /st repo owner/repo[@branch]")

        if (names.isEmpty()) rows += Row(if (loading) "Loading..." else "No scripts found in scripts/")

        names.forEach { name ->
            val installed = ScriptRepo.isInstalled(name)
            val text = if (installed) "§a✔ §f$name §7(click to re-import)" else "§f$name §7- click to import"
            rows += Row(text, {
                ScriptRepo.importAsync(name) {
                    status = it
                    refresh()
                }
            })
        }

        return rows
    }

    private fun moduleRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val modules = ModuleManager.modulesFolder.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.lowercase() }
            ?.map { ModuleManager.parseModule(it) }
            ?: emptyList()

        rows += Row("§bReload all scripts", {
            status = "Reloading scripts..."
            Reference.loadCT()
            rebuild()
        })
        rows += Row("§eOpen modules folder", { openModulesFolder(); rebuild() })
        rows += Row("§7${modules.size} modules in ${ModuleManager.modulesFolder.path}")

        modules.forEach { module ->
            val open = module.name in expanded
            val confirming = confirmDelete == module.name

            rows += Row(
                (if (open) "v " else "> ") + "§f${module.name} §7v${module.metadata.version ?: "?"}",
                {
                    if (open) expanded.remove(module.name) else expanded.add(module.name)
                    rebuild()
                },
                if (confirming) "§cSure?" else "Delete",
                {
                    if (confirming) {
                        module.folder.deleteRecursively()
                        confirmDelete = null
                        expanded.remove(module.name)
                        status = "Deleted ${module.name}"
                        Reference.loadCT()
                    } else {
                        confirmDelete = module.name
                    }
                    rebuild()
                },
            )

            if (open) {
                val files = module.folder.walk().filter { it.isFile }.toList()
                val meta = module.metadata
                rows += Row("§7   Entry: §f${meta.entry ?: "none"}")
                if (meta.creator != null) rows += Row("§7   Creator: §f${meta.creator}")
                if (meta.description != null) rows += Row("§7   Description: §f${meta.description}")
                if (!meta.tags.isNullOrEmpty()) rows += Row("§7   Tags: §f${meta.tags.joinToString(", ")}")
                if (!meta.requires.isNullOrEmpty()) rows += Row("§7   Requires: §f${meta.requires.joinToString(", ")}")
                rows += Row("§7   Files: §f${files.size} §7(${files.sumOf { it.length() } / 1024} KB)")
                rows += Row("§7   Path: §f${module.folder.path}")
            }
        }

        return rows
    }

    private fun consoleRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val filterNames = listOf("All", "Errors/Warnings", "Events", "Scripts (no events)")

        rows += Row("§bFilter: §f${filterNames[filter]} §7(click to change)", {
            filter = (filter + 1) % filterNames.size
            page = 0
            rebuild()
        })
        rows += Row("", input = Input("consoleSearch", "search text", { page = 0 }, "Search"))
        val rec = CTLog.recording
        rows += Row(if (rec) "§c■ Stop recording & save §7(${CTLog.recordedCount()} lines)" else "§a● Start recording log", {
            com.sushitriggers.ctjs.utils.console.Recorder.toggle()
            rebuild()
        })
        rows += Row("§bDump current log to file", {
            val f = CTLog.dump()
            status = if (f != null) "Dumped: ${f.path}" else "Dump failed"
            rebuild()
        })
        rows += Row("", input = Input("recordKey", "record key (e.g. F8, R)", {
            val code = com.sushitriggers.ctjs.utils.console.Recorder.parseKey(it)
            if (code != null) {
                Config.recordKey = code
                Config.save()
                status = "Record key: " + com.sushitriggers.ctjs.utils.console.Recorder.keyName(code)
            } else status = "Use a letter, digit or F1-F12"
        }, "Set key"))
        rows += Row("§7Record key: §f${com.sushitriggers.ctjs.utils.console.Recorder.keyName(Config.recordKey)} §7(works only with no GUI open)")
        rows += Row("§cClear console", {
            CTLog.clear()
            status = "Console cleared"
            rebuild()
        })

        val search = (inputs["consoleSearch"] ?: "").trim()
        val lines = CTLog.last(500).reversed().filter { search.isEmpty() || it.contains(search, ignoreCase = true) }.filter {
            when (filter) {
                1 -> "[ERROR]" in it || "[WARN]" in it
                2 -> "[EVENT]" in it
                3 -> "[EVENT]" !in it
                else -> true
            }
        }

        if (lines.isEmpty()) rows += Row("§7Nothing here yet")

        lines.forEach {
            val color = when {
                "[ERROR]" in it -> "§c"
                "[WARN]" in it -> "§e"
                "[EVENT]" in it -> "§3"
                else -> "§7"
            }
            rows += Row(color + it)
        }

        return rows
    }

    private fun eventRows(): List<Row> {
        val rows = mutableListOf<Row>()

        rows += toggle("Event log (file + console)", { Config.logEvents }, { Config.logEvents = it })
        rows += toggle("Show events live in chat", { Config.eventsInChat }, { Config.eventsInChat = it })

        rows += header("HIDE WORDS (comma separated)")
        if (inputs["hideWords"] == null) inputs["hideWords"] = Config.eventHideWords
        rows += Row("", input = Input("hideWords", "e.g. KEY, TARGET", {
            Config.eventHideWords = it
            Config.save()
            status = "Hide words saved"
        }, "Save"))

        rows += header("CATEGORIES (click to hide / show)")
        ActivityLog.categories.forEach { category ->
            val on = category !in Config.disabledEventCategories
            rows += Row((if (on) "§a● §f" else "§c○ §7") + category + " §8- " + (ActivityLog.categoryHelp[category] ?: ""), {
                if (on) Config.disabledEventCategories.add(category) else Config.disabledEventCategories.remove(category)
                Config.save()
                rebuild()
            })
        }

        rows += header("RECENT EVENTS (newest first)")

        val events = CTLog.last(500).reversed().filter { "[EVENT]" in it }.take(40)
        if (events.isEmpty()) rows += Row("§7No events yet")
        events.forEach { rows += Row("§3$it") }

        return rows
    }

    private fun settingRows(): List<Row> {
        val rows = mutableListOf<Row>()

        rows += toggle("Threaded module loading", { Config.threadedLoading }, { Config.threadedLoading = it })
        rows += toggle("Clear console on reload", { Config.clearConsoleOnLoad }, { Config.clearConsoleOnLoad = it })
        rows += toggle("Event log", { Config.logEvents }, { Config.logEvents = it })
        rows += toggle("Events in chat", { Config.eventsInChat }, { Config.eventsInChat = it })

        rows += Row("§bReload scripts", {
            status = "Reloading scripts..."
            Reference.loadCT()
            rebuild()
        })
        rows += Row("§cUnload scripts", {
            Reference.unloadCT()
            status = "Scripts unloaded"
            rebuild()
        })

        rows += header("GITHUB")
        if (inputs["repo"] == null) inputs["repo"] = ScriptRepo.spec()
        rows += Row("", input = Input("repo", "owner/repo[@branch]", { ScriptRepo.setSpec(it.trim()); status = "Repo set" }, "Set repo"))
        rows += Row("", input = Input("token", "GitHub token (for upload)", {
            if (it.isNotBlank()) { ScriptRepo.setToken(it.trim()); status = "Token saved" }
            inputs["token"] = ""
        }, "Set token"))
        rows += Row("§7Token: " + (if (ScriptRepo.hasToken()) "§aset" else "§cnot set"))
        rows += Row("§7Script repo: §f${ScriptRepo.spec()}")
        rows += Row("§7Modules folder: §f${ModuleManager.modulesFolder.path}")
        rows += Row("§7Log file: §f${CTLog.file.path}")
        rows += Row("§7Settings are saved in config/SushiTriggers/settings.json")

        return rows
    }

    private fun infoRows(): List<Row> {
        val rows = mutableListOf<Row>()
        val loader = FabricLoader.getInstance()
        val mc = Minecraft.getInstance()
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024

        fun info(name: String, value: Any?) {
            rows += Row("§7$name: §f$value")
        }

        rows += header("SUSHITRIGGERS")
        info("Version", Reference.MODVERSION)
        info("Scripts loaded", Reference.isLoaded)
        info("Modules installed", ModuleManager.modulesFolder.listFiles()?.count { it.isDirectory } ?: 0)

        val counts = JSLoader.triggerCounts()
        info("Registered triggers", counts.values.sum())
        counts.forEach { (type, count) -> rows += Row("§7   $type: §f$count") }

        val commands = Command.activeCommands.keys.sorted()
        info("Script commands", if (commands.isEmpty()) "none" else commands.joinToString(", ") { "/$it" })

        rows += header("GAME")
        info("Minecraft", loader.getModContainer("minecraft").map { it.metadata.version.friendlyString }.orElse("?"))
        info("Fabric Loader", loader.getModContainer("fabricloader").map { it.metadata.version.friendlyString }.orElse("?"))
        info("Fabric API", loader.getModContainer("fabric-api").map { it.metadata.version.friendlyString }.orElse("not found"))
        info("Mods loaded", loader.allMods.size)
        info("Java", System.getProperty("java.version"))
        info("Memory", "$usedMb / ${runtime.maxMemory() / 1024 / 1024} MB")

        val player = mc.player
        rows += header("PLAYER")
        if (player == null) {
            rows += Row("§7Not in a world")
        } else {
            fun fmt(d: Double?) = if (d == null) "?" else String.format("%.1f", d)

            info("Name", Refl.text(Refl.call(player, "getName")) ?: "?")
            info("Health", fmt(Refl.num(player, "health")))
            info("Position", "${fmt(Refl.num(player, "x"))}, ${fmt(Refl.num(player, "y"))}, ${fmt(Refl.num(player, "z"))}")
            info("Dimension", Refl.call(Refl.get(mc, "level"), "dimension")?.toString()?.substringAfterLast("/")?.trimEnd(']')?.trim() ?: "?")
        }

        return rows
    }

    companion object {
        @Volatile
        private var pending = false

        @Volatile
        private var loading = false

        @Volatile
        private var status = "SushiTriggers v2.2.1"

        private var tab = Tab.HOME
        private var page = 0
        private var filter = 0
        private var termFilter = 0
        private var termScroll = 0
        private var termSearch = ""
        private var confirmDelete: String? = null
        private val inputs = mutableMapOf<String, String>()
        private val editable = setOf("js", "json", "txt", "md", "html", "css", "yml", "yaml")
        private var editFile: File? = null
        private var editLines = mutableListOf("")
        private var editLine = 0
        private var editDirty = false
        private var editText = ""
        private var editSaved = ""
        private val expanded = mutableSetOf<String>()

        fun open() {
            pending = true
            loadList()
        }

        fun tick() {
            ScriptMenu.tick()
            if (pending) {
                pending = false
                show(ScriptGui())
            }
        }

        private fun loadList() {
            loading = true
            status = "Loading list from ${ScriptRepo.spec()}..."
            refresh()

            ScriptRepo.listAsync { list, error ->
                loading = false
                status = if (list != null) "${list.size} scripts available" else "Failed: $error"
                refresh()
            }
        }

        private fun refresh() {
            val mc = Minecraft.getInstance()
            mc.execute {
                val screen = com.sushitriggers.ctjs.minecraft.libs.GameUtil.screen()
                if (screen is ScriptGui) screen.rebuild()
            }
        }

        private fun show(screen: Screen?) {
            val mc = Minecraft.getInstance()
            mc.execute {
                val onMinecraft = mc.javaClass.methods.firstOrNull { it.name == "setScreen" && it.parameterCount == 1 }
                if (onMinecraft != null) {
                    onMinecraft.invoke(mc, screen)
                } else {
                    val gui = Refl.get(mc, "gui")
                    gui?.javaClass?.methods?.firstOrNull { it.name == "setScreen" && it.parameterCount == 1 }
                        ?.invoke(gui, screen)
                }
            }
        }
    }
}
