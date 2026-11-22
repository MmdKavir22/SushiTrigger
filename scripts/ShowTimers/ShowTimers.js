var Minecraft = Packages.net.minecraft.client.Minecraft;

var SETTINGS_FILE = "./config/SushiTriggers/showtimers-settings.json";
var FORGE_FILE = "./config/SushiTriggers/showtimers-forge.json";

var defaults = { enabled: true, darkAuction: true, forge: true, pickCooldown: true, abilityKey: 75, x: 6, y: 70 };
var settings = {};
var forgeEntries = [];
var tickCount = 0;
var failed = false;
var lastSignature = "";

function readFile(path) {
    try {
        var f = new java.io.File(path);
        if (!f.exists()) return null;
        return String(new java.lang.String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"));
    } catch (e) {
        return null;
    }
}

function writeFile(path, text) {
    try {
        var w = new java.io.FileWriter(path);
        w.write(text);
        w.close();
    } catch (e) {}
}

function loadSettings() {
    var saved = {};
    try {
        var text = readFile(SETTINGS_FILE);
        if (text) saved = JSON.parse(text);
    } catch (e) {}
    for (var k in defaults) {
        settings[k] = saved[k] !== undefined ? saved[k] : defaults[k];
    }
    settings.whitelist = (saved.whitelist && saved.whitelist.length !== undefined) ? saved.whitelist : [];
    settings.hidden = (saved.hidden && typeof saved.hidden === "object") ? saved.hidden : {};
}

function saveSettings() {
    writeFile(SETTINGS_FILE, JSON.stringify(settings));
}

function loadForge() {
    try {
        var text = readFile(FORGE_FILE);
        if (text) {
            var data = JSON.parse(text);
            if (data && data.length !== undefined) forgeEntries = data;
        }
    } catch (e) {}
}

function saveForge() {
    writeFile(FORGE_FILE, JSON.stringify(forgeEntries));
}

function pad(n) {
    return n < 10 ? "0" + n : "" + n;
}

function fmt(sec) {
    if (sec < 0) sec = 0;
    var h = Math.floor(sec / 3600);
    var m = Math.floor((sec % 3600) / 60);
    var s = Math.floor(sec % 60);
    return (h > 0 ? h + ":" + pad(m) : "" + m) + ":" + pad(s);
}

function parseDuration(text) {
    var re = /(\d+)\s*([dhms])/gi;
    var total = 0;
    var found = false;
    var m;
    while ((m = re.exec(text)) !== null) {
        found = true;
        var n = parseInt(m[1], 10);
        var u = m[2].toLowerCase();
        total += u === "d" ? n * 86400 : u === "h" ? n * 3600 : u === "m" ? n * 60 : n;
    }
    return found ? total : -1;
}

// ---------- Dark Auction (every hour at :55 UTC, lasts 5 minutes) ----------
function darkAuctionLine() {
    var now = new Date();
    var secondsOfHour = now.getUTCMinutes() * 60 + now.getUTCSeconds();
    var start = 55 * 60;
    if (secondsOfHour >= start) {
        var left = 3600 - secondsOfHour;
        return { text: "Dark Auction: ACTIVE " + fmt(left), color: 0x55FF55 };
    }
    var wait = start - secondsOfHour;
    return { text: "Dark Auction: " + fmt(wait), color: wait <= 60 ? 0xFFFF55 : 0xFFFFFF };
}

// ---------- Forge ----------
function currentScreen(mc) {
    try {
        return mc.gui.screen();
    } catch (e) {
        return null;
    }
}

var loreError = "";

function tooltipLore(stack) {
    var out = [];
    try {
        var ctxClass = java.lang.Class.forName("net.minecraft.world.item.Item$TooltipContext");
        var flagClass = java.lang.Class.forName("net.minecraft.world.item.TooltipFlag");
        var ctx = ctxClass.getField("EMPTY").get(null);
        var flag = flagClass.getField("NORMAL").get(null);
        var lines = stack.getTooltipLines(ctx, Minecraft.getInstance().player, flag);
        for (var i = 1; i < slotCount(lines); i++) out.push(String(slotAt(lines,i).getString()));
    } catch (e) {
        loreError += " tooltip:" + e;
    }
    return out;
}

function loreOf(stack) {
    var out = [];
    try {
        var DC = Packages.net.minecraft.core.component.DataComponents;
        var lore = stack.get(DC.LORE);
        if (lore != null) {
            var lines = lore.lines();
            for (var i = 0; i < slotCount(lines); i++) out.push(String(slotAt(lines,i).getString()));
        }
    } catch (e) {
        loreError += " lore:" + e;
    }
    if (out.length === 0) out = tooltipLore(stack);
    return out;
}

function loreOfOld(stack) {
    var out = [];
    try {
        var DC = Packages.net.minecraft.core.component.DataComponents;
        var lore = stack.get(DC.LORE);
        if (lore == null) return out;
        var lines = lore.lines();
        for (var i = 0; i < slotCount(lines); i++) out.push(String(slotAt(lines,i).getString()));
    } catch (e) {}
    return out;
}

function slotCount(slots) {
    return slots.length !== undefined ? slots.length : slots.size();
}

function slotAt(slots, i) {
    return slots.length !== undefined ? slots[i] : slots.get(i);
}

function scanForge(mc, dumpToFile) {
    var title = null;
    try {
        title = currentScreen(mc) != null ? String(currentScreen(mc).getTitle().getString()) : null;
    } catch (e) {}
    if (title == null) return false;
    var isForge = title.indexOf("ShowTimers") < 0 && title.toLowerCase().indexOf("forge") >= 0;
    if (!isForge && !dumpToFile) return false;

    var slots = mc.player.containerMenu.slots;
    var found = [];
    var dump = ["title: " + title];
    loreError = "";

    var total = slotCount(slots);
    var populated = false;
    for (var pi = 0; pi < total && pi < 54; pi++) {
        var ps = slotAt(slots, pi).getItem();
        if (!ps.isEmpty() && /^(close|claim all|forge recipes)$/i.test(String(ps.getHoverName().getString()))) { populated = true; break; }
    }
    if (isForge && !populated && !dumpToFile) return true;
    var limit = total > 36 ? total - 36 : total;
    for (var i = 0; i < limit; i++) {
        var stack = slotAt(slots, i).getItem();
        if (stack.isEmpty()) continue;
        var name = String(stack.getHoverName().getString());
        var lore = loreOf(stack);
        dump.push("slot " + i + ": " + name + " (lore lines: " + lore.length + ")");
        for (var k = 0; k < lore.length; k++) dump.push("    " + lore[k]);

        var seconds = -1;
        var ready = false;
        for (var j = 0; j < lore.length; j++) {
            if (/remaining|left|ends in|time/i.test(lore[j]) && parseDuration(lore[j]) >= 0) seconds = parseDuration(lore[j]);
            if (/collect/i.test(lore[j])) ready = true;
        }
        if (seconds >= 0 || ready) {
            var itemName = name;
            for (var n = 0; n < lore.length; n++) {
                var mm = /^Item:?\s*(.+)$/i.exec(lore[n]);
                if (mm) itemName = mm[1];
            }
            var newEnd = seconds >= 0 ? Date.now() + seconds * 1000 : Date.now();
            for (var q = 0; q < forgeEntries.length; q++) {
                if (forgeEntries[q].slot === i && forgeEntries[q].name === itemName && Math.abs(forgeEntries[q].end - newEnd) < 2500) newEnd = forgeEntries[q].end;
            }
            found.push({
                slot: i,
                name: itemName,
                end: newEnd
            });
        }
    }

    if (loreError) dump.push("errors:" + loreError);

    if (dumpToFile) {
        writeFile("./config/SushiTriggers/showtimers-forge-dump.txt", dump.join("\n"));
        return true;
    }

    if (isForge && total > 0) {
        writeFile("./config/SushiTriggers/showtimers-forge-dump.txt", dump.join("\n"));
        var signature = found.length + ":" + (found.length > 0 ? found[0].name : "");
        if (signature !== lastSignature) {
            lastSignature = signature;
            ChatLib.chat("&d[ShowTimers] &fforge: " + found.length + " timer(s) found" + (found.length === 0 ? " (dump saved: showtimers-forge-dump.txt)" : ""));
        }
        if (found.length > 0 || forgeEntries.length > 0) {
            forgeEntries = found;
            saveForge();
        }
        return true;
    }
    return false;
}

function forgeKey(e) {
    return "slot" + e.slot;
}

// ---------- Panel ----------
function updatePanel() {
    if (!settings.enabled) {
        Hud.remove("showtimers");
        return;
    }

    var lines = [];
    var colors = [];

    if (settings.darkAuction) {
        var da = darkAuctionLine();
        lines.push(da.text);
        colors.push(da.color);
    }

    if (settings.forge) {
        for (var i = 0; i < forgeEntries.length; i++) {
            if (settings.hidden[forgeKey(forgeEntries[i])]) continue;
            var left = Math.ceil((forgeEntries[i].end - Date.now()) / 1000);
            lines.push(forgeEntries[i].name + ": " + (left <= 0 ? "READY" : fmt(left)));
            colors.push(left <= 0 ? 0x55FF55 : (left <= 60 ? 0xFFFF55 : 0xFFFFFF));
        }
        if (forgeEntries.length === 0) {
            lines.push("Forge: open the NPC menu once");
            colors.push(0xAAAAAA);
        }
    }

    if (lines.length === 0) {
        Hud.remove("showtimers");
        return;
    }
    Hud.panel("showtimers", "Timers", 0xFF55FF, lines, colors, settings.x, settings.y);
}

// ---------- Menu ----------
var MENU_IDS = ["enabled", "darkAuction", "abilityTimer", "forgeItems", "move", "clearForge"];

function onoff(v) {
    return v ? "[ON]  " : "[OFF] ";
}

function menuLabel(id) {
    if (id === "enabled") return onoff(settings.enabled) + "Show timers on screen";
    if (id === "darkAuction") return onoff(settings.darkAuction) + "Dark Auction";
    if (id === "abilityTimer") return onoff(settings.pickCooldown) + "Ability Timer  >";
    if (id === "abOn") return onoff(settings.pickCooldown) + "Ability Timer";
    if (id === "abKey") return waitingKey ? "Press a key now..." : "Whitelist key: " + keyName(settings.abilityKey) + "  (click to change)";
    if (id.indexOf("wl:") === 0) return "[X] " + (settings.whitelist[parseInt(id.substring(3), 10)] || id) + "  (click to remove)";
    if (id === "forge") return onoff(settings.forge) + "Forge";
    if (id === "forgeItems") return onoff(settings.forge) + "Forge  >  (items)";
    if (id === "forge") return onoff(settings.forge) + "Forge timers";
    if (id.indexOf("fi:") === 0) {
        var e = forgeEntries[parseInt(id.substring(3), 10)];
        return e ? onoff(!settings.hidden[forgeKey(e)]) + "Slot " + (e.slot - 9) + ": " + e.name : id;
    }
    if (id === "back") return "< Back";
    if (id === "move") return "Move panel with mouse";
    if (id === "clearForge") return "Clear saved forge timers";
    return id;
}

function openForgeItems() {
    var ids = [];
    for (var i = 0; i < forgeEntries.length; i++) ids.push("fi:" + i);
    ids.unshift("forge");
    ids.push("back");
    if (forgeEntries.length === 0) ChatLib.chat("&d[ShowTimers] &fopen the NPC Forge once to list items");
    Menu.open("ShowTimers Settings", ids, forgeItemClick, menuLabel);
}

function forgeItemClick(id) {
    if (id === "back") { openMenu(); return; }
    if (id === "forge") { settings.forge = !settings.forge; saveSettings(); updatePanel(); openForgeItems(); return; }
    var e = forgeEntries[parseInt(id.substring(3), 10)];
    if (e) {
        var k = forgeKey(e);
        settings.hidden[k] = !settings.hidden[k];
        saveSettings();
        updatePanel();
    }
    openForgeItems();
}

function openAbilityMenu() {
    var ids = ["abOn", "abKey"];
    for (var i = 0; i < settings.whitelist.length; i++) ids.push("wl:" + i);
    ids.push("back");
    if (settings.whitelist.length === 0) ChatLib.chat("&d[ShowTimers] &fwhitelist empty: all items count. Open inventory, hover an item and press the key to add it");
    Menu.open("ShowTimers Settings", ids, abilityClick, menuLabel);
}

function abilityClick(id) {
    if (id === "back") { openMenu(); return; }
    if (id === "abOn") { settings.pickCooldown = !settings.pickCooldown; saveSettings(); }
    else if (id === "abKey") { waitingKey = true; }
    else if (id.indexOf("wl:") === 0) {
        settings.whitelist.splice(parseInt(id.substring(3), 10), 1);
        saveSettings();
    }
    openAbilityMenu();
}

function menuClick(id) {
    if (id === "abilityTimer") { openAbilityMenu(); return; }
    if (id === "forgeItems") { openForgeItems(); return; }
    if (id === "move") {
        updatePanel();
        Hud.edit("showtimers", function (x, y) {
            settings.x = x;
            settings.y = y;
            saveSettings();
        });
        return;
    }
    if (id === "clearForge") {
        forgeEntries = [];
        saveForge();
    } else {
        settings[id] = !settings[id];
        saveSettings();
    }
    updatePanel();
}

function openMenu() {
    Menu.open("ShowTimers", MENU_IDS, menuClick, menuLabel);
}

// ---------- Triggers ----------
loadSettings();
if (!settings.hidden) settings.hidden = {};
if (!settings.whitelist) settings.whitelist = [];
loadForge();

register("tick", function () {
    tickCount++;
    var mc = Minecraft.getInstance();
    if (mc.player == null) return;
    try {
        keyTick();
        if (tickCount % 10 === 0 && currentScreen(mc) != null) scanForge(mc, false);
        updatePanel();
    } catch (e) {
        if (!failed) {
            failed = true;
            ChatLib.chat("&c[ShowTimers] " + e);
            print("[ShowTimers] " + e);
        }
    }
});

register("gameUnload", function () {
    Hud.remove("showtimers");
});

register("command", function (a, b, c) {
    if (a === "pos") {
        var nx = parseInt(b, 10);
        var ny = parseInt(c, 10);
        if (!isNaN(nx)) settings.x = nx;
        if (!isNaN(ny)) settings.y = ny;
        saveSettings();
        ChatLib.chat("&d[ShowTimers] &fposition " + settings.x + ", " + settings.y);
        return;
    }
    if (a === "dump") {
        var mc = Minecraft.getInstance();
        var ok = false;
        try { ok = currentScreen(mc) != null && scanForge(mc, true); } catch (e) { ok = false; }
        ChatLib.chat(ok ? "&a[ShowTimers] saved config/SushiTriggers/showtimers-forge-dump.txt" : "&c[ShowTimers] open the Forge menu first");
        return;
    }
    if (a === "list") {
        ChatLib.chat("&d[ShowTimers] &f" + darkAuctionLine().text);
        for (var i = 0; i < forgeEntries.length; i++) {
            var left = Math.round((forgeEntries[i].end - Date.now()) / 1000);
            ChatLib.chat("&d[ShowTimers] &f" + forgeEntries[i].name + ": " + (left <= 0 ? "READY" : fmt(left)));
        }
        return;
    }
    openMenu();
}).setName("showtimer");

// ---------- Pickaxe ability cooldown on hotbar ----------
var PICK_COOLDOWNS = {
    "mining speed boost": 120,
    "pickobulus": 110,
    "maniac miner": 59,
    "vein seeker": 60,
    "anomalous desire": 110,
    "gemstone infusion": 120,
    "sheer force": 120
};
var pickTimers = {};
var waitingKey = false;
var keyWasDown = false;

function stripCodes(t) {
    return String(t).replace(/\u00a7./g, "").trim();
}

function isWhitelisted(name) {
    if (settings.whitelist.length === 0) return true;
    return settings.whitelist.indexOf(stripCodes(name)) >= 0;
}

function keyName(code) {
    try {
        var n = Packages.org.lwjgl.glfw.GLFW.glfwGetKeyName(code, 0);
        if (n != null) return String(n).toUpperCase();
    } catch (e) {}
    return "key " + code;
}

function windowHandle(mc) {
    var w = mc.getWindow();
    try { return w.handle(); } catch (e) {}
    return w.getWindow();
}

function isDown(mc, code) {
    return Packages.org.lwjgl.glfw.GLFW.glfwGetKey(windowHandle(mc), code) === 1;
}

function hoveredItemName(mc) {
    var scr = currentScreen(mc);
    if (scr == null) return null;
    var c = scr.getClass();
    while (c != null) {
        try {
            var f = c.getDeclaredField("hoveredSlot");
            f.setAccessible(true);
            var slot = f.get(scr);
            if (slot == null) return null;
            var st = slot.getItem();
            if (st.isEmpty()) return null;
            return stripCodes(st.getHoverName().getString());
        } catch (e) {}
        c = c.getSuperclass();
    }
    return null;
}

function keyTick() {
    var mc = Minecraft.getInstance();
    if (waitingKey) {
        for (var code = 32; code <= 348; code++) {
            if (code === 256) continue;
            if (isDown(mc, code)) {
                settings.abilityKey = code;
                saveSettings();
                waitingKey = false;
                keyWasDown = true;
                ChatLib.chat("&d[ShowTimers] &fwhitelist key: " + keyName(code));
                openAbilityMenu();
                return;
            }
        }
        return;
    }
    var down = isDown(mc, settings.abilityKey);
    if (down && !keyWasDown && currentScreen(mc) != null) {
        var name = hoveredItemName(mc);
        if (name) {
            var idx = settings.whitelist.indexOf(name);
            if (idx >= 0) {
                settings.whitelist.splice(idx, 1);
                ChatLib.chat("&d[ShowTimers] &cremoved &f" + name + " &cfrom ability whitelist");
            } else {
                settings.whitelist.push(name);
                ChatLib.chat("&d[ShowTimers] &aadded &f" + name + " &ato ability whitelist");
            }
            saveSettings();
        }
    }
    keyWasDown = down;
}

function pickLeft(slot) {
    var t = pickTimers[slot];
    return t === undefined ? 0 : t - Date.now();
}

var pickBound = {}; // item name -> ability name

register("chat", function (msg) {
    msg = String(msg);
    var item = stripCodes(Player.getHeldItem().name);
    if (!item || !isWhitelisted(item)) return;

    var used = /You used your (.+?) Pickaxe Ability!/i.exec(msg);
    if (used) {
        var ability = used[1].toLowerCase();
        if (pickBound[item] !== undefined && pickBound[item] !== ability) return;
        pickBound[item] = ability;
        var cd = PICK_COOLDOWNS[ability];
        pickTimers[item] = Date.now() + (cd === undefined ? 120 : cd) * 1000;
        return;
    }
    var cool = /on cooldown for (\d+)\s*s/i.exec(msg);
    if (cool) {
        if (pickBound[item] === undefined) return;
        pickTimers[item] = Date.now() + parseInt(cool[1], 10) * 1000;
        return;
    }
    if (/is now available!/i.test(msg) && pickBound[item] !== undefined) {
        var l = pickLeft(item);
        if (l > 0 && l < 3000) delete pickTimers[item];
    }
});

register("renderOverlay", function () {
    if (!settings.enabled || !settings.pickCooldown) return;
    var w = Hud.width();
    var h = Hud.height();
    for (var i = 0; i < 9; i++) {
        var name = stripCodes(Player.getItem(i).name);
        if (!name) continue;
        var left = pickLeft(name);
        if (left <= 0) {
            if (pickTimers[name] !== undefined) delete pickTimers[name];
            continue;
        }
        var secs = Math.ceil(left / 1000);
        var label = String(secs);
        var x = Math.floor(w / 2) - 90 + i * 20 + 2;
        var y = h - 19;
        Hud.rect(x, y, 16, 16, 0xAA000000 | 0);
        var color = secs <= 5 ? 0xFF55FF55 : (secs <= 15 ? 0xFFFFFF55 : 0xFFFFFFFF);
        Hud.text(label, x + 8 - Math.floor(Hud.textWidth(label) / 2), y + 4, color | 0);
    }
});

ChatLib.chat("&d[ShowTimers] &7loaded. /showtimer opens the settings menu");
