var Minecraft = Packages.net.minecraft.client.Minecraft;
var SETTINGS_FILE = "./config/SushiTriggers/saveyourass.json";
var SWORD = "florid zombie sword";

var defaults = { enabled: false, threshold: 40, click: "left", clicks: 3, cost: 25 };
var settings = {};

function loadSettings() {
    var saved = {};
    try {
        var f = new java.io.File(SETTINGS_FILE);
        if (f.exists()) saved = JSON.parse(String(new java.lang.String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8")));
    } catch (e) {}
    for (var k in defaults) settings[k] = saved[k] !== undefined ? saved[k] : defaults[k];
}

function saveSettings() {
    try {
        var w = new java.io.FileWriter(SETTINGS_FILE);
        w.write(JSON.stringify(settings));
        w.close();
    } catch (e) {}
}

function stripCodes(t) {
    return String(t).replace(/§./g, "").trim();
}

// ---------- Vitality reading ----------
var lastVit = null;
var lastVitSrc = "";
var lastVitTime = 0;

function numFrom(text) {
    var t = stripCodes(text).replace(/,/g, "");
    var m = /vitality\D{0,12}(\d+(?:\.\d+)?)/i.exec(t) || /(\d+(?:\.\d+)?)\s*\S{0,2}\s*vitality/i.exec(t);
    return m ? parseFloat(m[1]) : null;
}

function actionBarText(mc) {
    try {
        var gui = mc.gui;
        var c = gui.getClass();
        while (c != null) {
            var fs = c.getDeclaredFields();
            for (var i = 0; i < fs.length; i++) {
                var n = String(fs[i].getName()).toLowerCase();
                if (n.indexOf("overlaymessage") >= 0 && n.indexOf("time") < 0 && n.indexOf("animate") < 0) {
                    fs[i].setAccessible(true);
                    var v = fs[i].get(gui);
                    if (v != null) return String(v.getString ? v.getString() : v);
                }
            }
            c = c.getSuperclass();
        }
    } catch (e) {}
    return "";
}

function tabLines(mc) {
    var out = [];
    try {
        var conn = mc.getConnection();
        var infos = conn.getOnlinePlayers().toArray();
        for (var i = 0; i < infos.length; i++) {
            try {
                var d = infos[i].getTabListDisplayName();
                if (d != null) out.push(String(d.getString()));
            } catch (e) {}
        }
    } catch (e) {}
    return out;
}

function bossLines(mc) {
    var out = [];
    try {
        var hud = mc.gui;
        var c = hud.getClass();
        while (c != null) {
            var fs = c.getDeclaredFields();
            for (var i = 0; i < fs.length; i++) {
                fs[i].setAccessible(true);
                var v = fs[i].get(hud);
                if (v != null && String(v.getClass().getName()).toLowerCase().indexOf("hud") >= 0 && String(v.getClass().getName()).indexOf("Gui") < 0) {
                    var f2 = v.getClass().getDeclaredFields();
                    for (var j = 0; j < f2.length; j++) {
                        f2[j].setAccessible(true);
                        var b = f2[j].get(v);
                        if (b != null && String(b.getClass().getName()).toLowerCase().indexOf("bossoverlay") >= 0) {
                            var f3 = b.getClass().getDeclaredFields();
                            for (var k = 0; k < f3.length; k++) {
                                f3[k].setAccessible(true);
                                var ev = f3[k].get(b);
                                if (ev != null && ev.values) {
                                    var arr = ev.values().toArray();
                                    for (var q = 0; q < arr.length; q++) out.push(String(arr[q].getName().getString()));
                                }
                            }
                        }
                    }
                }
            }
            c = c.getSuperclass();
        }
    } catch (e) {}
    return out;
}

function candidates(mc) {
    var out = [];
    var ab = actionBarText(mc);
    if (ab) out.push(["actionbar", ab]);
    var tl = tabLines(mc);
    for (var t = 0; t < tl.length; t++) out.push(["tab", tl[t]]);
    var bl = bossLines(mc);
    for (var u = 0; u < bl.length; u++) out.push(["bossbar", bl[u]]);
    try {
        var held = Player.getHeldItem();
        if (held && held.lore) for (var h = 0; h < held.lore.length; h++) out.push(["heldlore", String(held.lore[h])]);
    } catch (e) {}
    try {
        var sb = World.getScoreboard();
        for (var i = 0; i < sb.length; i++) out.push(["scoreboard", String(sb[i])]);
    } catch (e) {}
    return out;
}

function readVitality(mc) {
    var c = candidates(mc);
    for (var i = 0; i < c.length; i++) {
        var v = numFrom(c[i][1]);
        if (v !== null) {
            lastVit = v;
            lastVitSrc = c[i][0];
            lastVitTime = Date.now();
            return v;
        }
    }
    if (lastVit !== null && Date.now() - lastVitTime < 4000) return lastVit;
    return null;
}

register("chat", function (msg) {
    var v = numFrom(msg);
    if (v !== null) { lastVit = v; lastVitSrc = "chat"; lastVitTime = Date.now(); }
});

// ---------- Logic ----------
// state: 0 idle, 1 switch, 2 clicking, 3 switch back
var state = 0;
var wait = 0;
var clicksDone = 0;
var clicksMax = 0;
var vitStart = 0;
var prevSlot = 0;
var swordSlot = 0;
var coolTicks = 0;
var errShown = false;
var warnedVit = false;

function doClick(mc) {
    var hand = Packages.net.minecraft.world.InteractionHand.MAIN_HAND;
    if (settings.click === "right") mc.gameMode.useItem(mc.player, hand);
    else mc.player.swing(hand);
}

function hpPercent() {
    var max = Player.getMaxHealth();
    return max > 0 ? Player.getHealth() / max * 100 : 100;
}

function findSword() {
    for (var i = 0; i < 9; i++) {
        var it = Player.getItem(i);
        if (it && it.name && stripCodes(it.name).toLowerCase().indexOf(SWORD) >= 0) return i;
    }
    return -1;
}

function start(mc, force) {
    var slot = findSword();
    if (slot < 0) {
        if (force) ChatLib.chat("&c[SaveYourAss] Florid Zombie Sword not found in hotbar");
        return false;
    }
    var vit = readVitality(mc);
    if (vit === null) {
        if (!warnedVit || force) {
            warnedVit = true;
            ChatLib.chat("&c[SaveYourAss] cannot read Vitality. Run /saveyourass vit");
        }
        return false;
    }
    var affordable = Math.floor(vit / settings.cost);
    var n = Math.min(settings.clicks, affordable);
    if (n < 1) {
        if (force) ChatLib.chat("&e[SaveYourAss] not enough Vitality (" + vit + ", need " + settings.cost + ")");
        return false;
    }
    swordSlot = slot;
    prevSlot = Player.getSelectedSlot();
    clicksMax = n;
    clicksDone = 0;
    vitStart = vit;
    state = (prevSlot === slot) ? 2 : 1;
    wait = 0;
    return true;
}

register("tick", function () {
    var mc = Minecraft.getInstance();
    if (mc.player == null) return;
    try {
        if (state === 0) {
            if (coolTicks > 0) { coolTicks--; return; }
            if (!settings.enabled) return;
            if (hpPercent() >= settings.threshold) return;
            if (mc.gui.screen() != null) return;
            start(mc, false);
            return;
        }
        if (state === 1) {
            Player.setSelectedSlot(swordSlot);
            state = 2;
            wait = 1;
            return;
        }
        if (state === 2) {
            if (wait > 0) { wait--; return; }
            var expected = vitStart - clicksDone * settings.cost;
            var more = clicksDone < clicksMax && expected >= settings.cost && hpPercent() < settings.threshold;
            if (!more) { state = 3; wait = 1; return; }
            doClick(mc);
            clicksDone++;
            wait = 1;
            return;
        }
        if (state === 3) {
            if (wait > 0) { wait--; return; }
            if (Player.getSelectedSlot() !== prevSlot) Player.setSelectedSlot(prevSlot);
            state = 0;
            coolTicks = 8;
        }
    } catch (e) {
        state = 0;
        if (!errShown) {
            errShown = true;
            ChatLib.chat("&c[SaveYourAss] " + e);
        }
    }
});

var IDS = ["enabled", "threshold-", "threshold+", "clicks-", "clicks+", "click", "test"];

function label(id) {
    if (id === "enabled") return (settings.enabled ? "[ON]  " : "[OFF] ") + "Save your ass";
    if (id === "threshold-") return "HP threshold: " + settings.threshold + "%   (click: -5)";
    if (id === "threshold+") return "HP threshold: " + settings.threshold + "%   (click: +5)";
    if (id === "clicks-") return "Max clicks: " + settings.clicks + "   (click: -1)";
    if (id === "clicks+") return "Max clicks: " + settings.clicks + "   (click: +1)";
    if (id === "click") return "Click type: " + (settings.click === "left" ? "LEFT" : "RIGHT");
    if (id === "test") return "Test now";
    return id;
}

function onClick(id) {
    if (id === "enabled") settings.enabled = !settings.enabled;
    else if (id === "threshold-") settings.threshold = Math.max(5, settings.threshold - 5);
    else if (id === "threshold+") settings.threshold = Math.min(95, settings.threshold + 5);
    else if (id === "click") settings.click = settings.click === "left" ? "right" : "left";
    else if (id === "clicks+") settings.clicks = Math.min(10, settings.clicks + 1);
    else if (id === "clicks-") settings.clicks = Math.max(1, settings.clicks - 1);
    else if (id === "test") { saveSettings(); start(Minecraft.getInstance(), true); return; }
    saveSettings();
    openMenu();
}

function openMenu() {
    Menu.open("Save your ass", IDS, onClick, label);
}

register("command", function (a, b) {
    if (a === "threshold" && !isNaN(parseInt(b, 10))) {
        settings.threshold = Math.max(1, Math.min(99, parseInt(b, 10)));
        saveSettings();
        ChatLib.chat("&d[SaveYourAss] &fthreshold " + settings.threshold + "%");
        return;
    }
    if (a === "cost" && !isNaN(parseInt(b, 10))) {
        settings.cost = Math.max(1, parseInt(b, 10));
        saveSettings();
        ChatLib.chat("&d[SaveYourAss] &fvitality cost per click " + settings.cost);
        return;
    }
    if (a === "vit") {
        var mc = Minecraft.getInstance();
        var v = readVitality(mc);
        ChatLib.chat("&d[SaveYourAss] &fvitality: " + v + " (source: " + lastVitSrc + ")");
        var c = candidates(mc);
        var lines = [];
        for (var i = 0; i < c.length; i++) lines.push(c[i][0] + ": " + stripCodes(c[i][1]));
        try {
            var w = new java.io.FileWriter("./config/SushiTriggers/saveyourass-dump.txt");
            w.write(lines.join("\n"));
            w.close();
            ChatLib.chat("&d[SaveYourAss] &fsaved config/SushiTriggers/saveyourass-dump.txt");
        } catch (e) {}
        return;
    }
    openMenu();
}).setName("saveyourass");

loadSettings();
ChatLib.chat("&d[SaveYourAss] &7loaded. /saveyourass opens the menu");
