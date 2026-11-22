var Minecraft = Packages.net.minecraft.client.Minecraft;
var SETTINGS_FILE = "./config/SushiTriggers/saveyourass.json";
var SWORD = "florid zombie sword";

var defaults = { enabled: false, threshold: 40, click: "left", clicks: 3 };
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

function findSword() {
    for (var i = 0; i < 9; i++) {
        var it = Player.getItem(i);
        if (it && it.name && stripCodes(it.name).toLowerCase().indexOf(SWORD) >= 0) return i;
    }
    return -1;
}

// state machine: 0 idle, 1 switch, 2 clicking, 3 switch back
var state = 0;
var wait = 0;
var clicksLeft = 0;
var prevSlot = 0;
var swordSlot = 0;
var cooldownUntil = 0;
var armed = true;
var errShown = false;

function doClick(mc) {
    var hand = Packages.net.minecraft.world.InteractionHand.MAIN_HAND;
    if (settings.click === "right") mc.gameMode.useItem(mc.player, hand);
    else mc.player.swing(hand);
}

function hpPercent() {
    var max = Player.getMaxHealth();
    return max > 0 ? Player.getHealth() / max * 100 : 100;
}

function start(force) {
    var slot = findSword();
    if (slot < 0) {
        if (force) ChatLib.chat("&c[SaveYourAss] Florid Zombie Sword not found in hotbar");
        return false;
    }
    swordSlot = slot;
    prevSlot = Player.getSelectedSlot();
    clicksLeft = settings.clicks;
    state = 1;
    wait = 0;
    cooldownUntil = Date.now() + 5000;
    return true;
}

register("tick", function () {
    var mc = Minecraft.getInstance();
    if (mc.player == null) return;
    try {
        if (state === 0) {
            if (!settings.enabled) return;
            var pct = hpPercent();
            if (pct >= settings.threshold) { armed = true; return; }
            if (armed && Date.now() > cooldownUntil && mc.gui.screen() == null) {
                armed = false;
                if (start(false)) ChatLib.chat("&d[SaveYourAss] &fHP " + Math.round(pct) + "% - using Florid Zombie Sword");
            }
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
            doClick(mc);
            clicksLeft--;
            wait = 1;
            if (clicksLeft <= 0) state = 3;
            return;
        }
        if (state === 3) {
            if (wait > 0) { wait--; return; }
            Player.setSelectedSlot(prevSlot);
            state = 0;
        }
    } catch (e) {
        state = 0;
        if (!errShown) {
            errShown = true;
            ChatLib.chat("&c[SaveYourAss] " + e);
        }
    }
});

var IDS = ["enabled", "threshold-", "threshold+", "click", "test"];

function label(id) {
    if (id === "enabled") return (settings.enabled ? "[ON]  " : "[OFF] ") + "Save your ass";
    if (id === "threshold-") return "HP threshold: " + settings.threshold + "%   (click: -5)";
    if (id === "threshold+") return "HP threshold: " + settings.threshold + "%   (click: +5)";
    if (id === "click") return "Click type: " + (settings.click === "left" ? "LEFT x" : "RIGHT x") + settings.clicks;
    if (id === "test") return "Test now";
    return id;
}

function onClick(id) {
    if (id === "enabled") settings.enabled = !settings.enabled;
    else if (id === "threshold-") settings.threshold = Math.max(5, settings.threshold - 5);
    else if (id === "threshold+") settings.threshold = Math.min(95, settings.threshold + 5);
    else if (id === "click") settings.click = settings.click === "left" ? "right" : "left";
    else if (id === "test") { saveSettings(); start(true); return; }
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
    openMenu();
}).setName("saveyourass");

loadSettings();
ChatLib.chat("&d[SaveYourAss] &7loaded. /saveyourass opens the menu");
