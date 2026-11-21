package com.sushitriggers.ctjs.utils.console

import com.sushitriggers.ctjs.minecraft.libs.ChatLib
import com.sushitriggers.ctjs.minecraft.listeners.ChatWatcher
import com.sushitriggers.ctjs.triggers.TriggerType
import com.sushitriggers.ctjs.utils.Config
import com.sushitriggers.ctjs.utils.Refl
import net.minecraft.client.Minecraft
import kotlin.math.hypot
import kotlin.math.sqrt

object ActivityLog {
    private var hadPlayer = false
    private var lastLevel: Any? = null
    private var lastHealth: Double? = null
    private var lastAbsorption: Double? = null
    private var lastFood: Double? = null
    private var lastHurt = 0.0
    private var lastVel: DoubleArray? = null
    private var lastPos: DoubleArray? = null
    private var lastOnGround: Boolean? = null
    private var lastSprint = false
    private var lastSneak = false
    private var lastFire = false
    private var lastItem: String? = null
    private var lastScreen: String? = null
    private var watchKnockback = 0
    private var dead = false

    private var joinGrace = 0
    private var lastInventory: Array<String>? = null
    private var lastSelectedSlot: Int? = null
    private var lastMenuName: String? = null
    private var menuGrace = 0
    private var lastMenuSlots: List<String>? = null
    private var lastCarried: String? = null
    private val keyState = HashMap<String, Boolean>()
    private val mouseState = BooleanArray(3)
    private var lastTarget: String? = null
    private var lastXp: Double? = null
    private var lastEffects: Set<String> = emptySet()

    val categories = listOf(
        "health", "knockback", "movement", "items", "inventory", "container",
        "clicks", "keys", "screens", "target", "effects", "blocks", "chat", "world"
    )

    val categoryHelp = mapOf(
        "health" to "damage, healing, hunger, fire, death",
        "knockback" to "knockback and sudden velocity changes",
        "movement" to "jump, sprint, sneak, teleport",
        "items" to "item held in main hand",
        "inventory" to "every inventory slot change, hotbar slot",
        "container" to "chests/menus opened, slot changes, cursor item",
        "clicks" to "mouse button presses (also inside GUIs)",
        "keys" to "every bound key press (movement, hotbar, inventory...)",
        "screens" to "screens opened and closed",
        "target" to "entity under the crosshair",
        "effects" to "potion effects gained or lost",
        "blocks" to "blocks broken, placed or changed near you, block you look at",
        "chat" to "every chat / system message you receive",
        "world" to "join, leave, dimension change, xp level"
    )

    fun categoryOf(text: String): String = when {
        text.startsWith("DAMAGE") || text.startsWith("HEAL") || text.startsWith("HURT") ||
                text.startsWith("ABSORPTION") || text.startsWith("FOOD") || text.startsWith("DEAD") ||
                text.startsWith("ON FIRE") || text.startsWith("FIRE") -> "health"
        text.startsWith("KNOCKBACK") || text.startsWith("VELOCITY") -> "knockback"
        text.startsWith("JUMP") || text.startsWith("SPRINT") || text.startsWith("SNEAK") ||
                text.startsWith("TELEPORT") -> "movement"
        text.startsWith("HELD") -> "items"
        text.startsWith("INVENTORY") || text.startsWith("HOTBAR") -> "inventory"
        text.startsWith("CONTAINER") || text.startsWith("CURSOR") -> "container"
        text.startsWith("CLICK") -> "clicks"
        text.startsWith("KEY") -> "keys"
        text.startsWith("SCREEN") -> "screens"
        text.startsWith("TARGET") -> "target"
        text.startsWith("EFFECT") -> "effects"
        text.startsWith("BLOCK") -> "blocks"
        text.startsWith("CHAT") -> "chat"
        else -> "world"
    }

    fun event(text: String) {
        try {
            TriggerType.Event.triggerAll(categoryOf(text), text)
        } catch (e: Throwable) {
        }

        if (categoryOf(text) in Config.disabledEventCategories) return

        val hideWords = Config.eventHideWords.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (hideWords.any { text.contains(it, ignoreCase = true) }) return

        if (Config.logEvents) CTLog.add(LogType.INFO, "[EVENT] $text")
        if (Config.eventsInChat) ChatLib.chat("&8[EVENT] &7$text")
    }

    private fun fmt(d: Double) = String.format("%.2f", d)

    private fun describe(stack: Any?): String {
        if (stack == null || Refl.call(stack, "isEmpty") == true) return "empty"

        val name = Refl.text(Refl.call(stack, "getHoverName")) ?: stack.toString()
        val count = (Refl.call(stack, "getCount") as? Number)?.toInt() ?: 1
        return "$name x$count"
    }

    private fun slotName(index: Int) = when (index) {
        in 0..8 -> "hotbar ${index + 1}"
        in 9..35 -> "inventory ${index - 8}"
        36 -> "boots"
        37 -> "leggings"
        38 -> "chestplate"
        39 -> "helmet"
        40 -> "offhand"
        else -> "slot $index"
    }

    private fun reset() {
        lastLevel = null
        lastHealth = null
        lastAbsorption = null
        lastFood = null
        lastHurt = 0.0
        lastVel = null
        lastPos = null
        lastOnGround = null
        lastSprint = false
        lastSneak = false
        lastFire = false
        lastItem = null
        watchKnockback = 0
        dead = false
        lastInventory = null
        lastSelectedSlot = null
        lastMenuName = null
        lastMenuSlots = null
        lastCarried = null
        lastTarget = null
        lastXp = null
        lastEffects = emptySet()
    }

    private inline fun safe(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
        }
    }

    fun tick() {
        Recorder.tick()

        val mc = Minecraft.getInstance()
        safe { ChatWatcher.tick(mc) }
        val player = mc.player

        val screenName = com.sushitriggers.ctjs.minecraft.libs.GameUtil.screen()?.javaClass?.simpleName
        if (screenName != lastScreen) {
            if (screenName != null) event("SCREEN opened: $screenName")
            else if (lastScreen != null) event("SCREEN closed: $lastScreen")
            lastScreen = screenName
        }

        safe { trackMouse(mc, screenName) }
        safe { trackKeys(mc) }

        if (player == null) {
            if (hadPlayer) {
                event("LEFT world")
                hadPlayer = false
                reset()
            }
            return
        }

        val level = Refl.get(mc, "level")
        val dimension = Refl.call(level, "dimension")?.toString()?.substringAfterLast("/")?.trimEnd(']')?.trim()

        if (!hadPlayer) {
            event("JOINED world, dimension=$dimension")
            hadPlayer = true
            joinGrace = 40
        } else if (level !== lastLevel && lastLevel != null) {
            event("WORLD changed: dimension=$dimension")
            lastPos = null
            lastVel = null
            joinGrace = 40
        }
        lastLevel = level

        if (joinGrace > 0) joinGrace--

        safe { trackBody(player) }
        safe { trackInventory(player) }
        safe { trackContainer(player) }
        safe { trackTarget(mc) }
        safe { trackXpAndEffects(player) }
        if ("blocks" !in Config.disabledEventCategories) safe { trackBlocks(mc, player, level) }
    }

    private var blockMap = HashMap<Long, Any?>()
    private var blockTick = 0
    private var lastLookedBlock: String? = null

    private fun blockName(state: Any?): String {
        val id = Refl.call(Refl.call(state, "getBlock"), "getDescriptionId")?.toString() ?: return "?"
        return id.removePrefix("block.").replace("minecraft.", "")
    }

    private fun isAir(state: Any?) = Refl.call(state, "isAir") == true

    private fun trackBlocks(mc: Minecraft, player: Any, level: Any?) {
        if (level !is net.minecraft.world.level.BlockGetter) return

        val hit = Refl.get(mc, "hitResult")
        var looked: String? = null
        if (hit != null && hit.javaClass.simpleName == "BlockHitResult") {
            val pos = Refl.call(hit, "getBlockPos") as? net.minecraft.core.BlockPos
            if (pos != null) {
                looked = blockName(level.getBlockState(pos)) + " at ${pos.x}, ${pos.y}, ${pos.z}"
            }
        }
        if (looked != lastLookedBlock) {
            if (looked != null) event("BLOCK looking at: $looked")
            lastLookedBlock = looked
        }

        if (++blockTick % 2 != 0) return

        val px = Math.floor(Refl.num(player, "x") ?: return).toInt()
        val py = Math.floor(Refl.num(player, "y") ?: return).toInt()
        val pz = Math.floor(Refl.num(player, "z") ?: return).toInt()
        val radius = 4

        val fresh = HashMap<Long, Any?>()
        val changes = ArrayList<String>()
        val pos = net.minecraft.core.BlockPos.MutableBlockPos()

        for (x in px - radius..px + radius) for (y in py - radius..py + radius) for (z in pz - radius..pz + radius) {
            pos.set(x, y, z)
            val state = level.getBlockState(pos)
            val key = net.minecraft.core.BlockPos.asLong(x, y, z)
            fresh[key] = state

            if (joinGrace > 0 || !blockMap.containsKey(key)) continue
            val old = blockMap[key]
            if (old !== state) {
                val where = "($x, $y, $z)"
                changes += when {
                    isAir(state) -> "BLOCK removed ${blockName(old)} at $where"
                    isAir(old) -> "BLOCK placed ${blockName(state)} at $where"
                    else -> "BLOCK changed ${blockName(old)} -> ${blockName(state)} at $where"
                }
            }
        }
        blockMap = fresh

        if (changes.size > 40) event("BLOCK bulk change: ${changes.size} blocks (chunk load / explosion)")
        else changes.forEach { event(it) }
    }

    private fun trackMouse(mc: Minecraft, screenName: String?) {
        val handler = Refl.get(mc, "mouseHandler") ?: return
        val names = listOf("left", "right", "middle")
        val methods = listOf("isLeftPressed", "isRightPressed", "isMiddlePressed")

        for (i in 0..2) {
            val down = Refl.call(handler, methods[i]) as? Boolean ?: continue
            if (down && !mouseState[i]) {
                event("CLICK ${names[i]} button pressed" + (screenName?.let { " in screen $it" } ?: " in world"))
            }
            mouseState[i] = down
        }
    }

    private fun trackKeys(mc: Minecraft) {
        val options = Refl.get(mc, "options") ?: return
        val mappings = Refl.get(options, "keyMappings") as? Array<*> ?: return

        for (mapping in mappings) {
            val name = Refl.call(mapping, "getName") as? String ?: continue
            val down = Refl.call(mapping, "isDown") as? Boolean ?: continue
            val previous = keyState[name] ?: false

            if (down && !previous) {
                val key = Refl.text(Refl.call(mapping, "getTranslatedKeyMessage"))
                event("KEY $name" + (key?.let { " ($it)" } ?: "") + " pressed")
            }
            keyState[name] = down
        }
    }

    private fun trackBody(player: Any) {
        val health = Refl.num(player, "health")
        val absorption = Refl.num(player, "absorptionAmount")
        val food = Refl.num(Refl.call(player, "getFoodData"), "foodLevel")
        val hurt = Refl.num(player, "hurtTime") ?: 0.0

        val velObj = Refl.call(player, "getDeltaMovement")
        val vel = if (velObj != null) doubleArrayOf(
            Refl.num(velObj, "x") ?: 0.0, Refl.num(velObj, "y") ?: 0.0, Refl.num(velObj, "z") ?: 0.0
        ) else null
        val pos = doubleArrayOf(Refl.num(player, "x") ?: 0.0, Refl.num(player, "y") ?: 0.0, Refl.num(player, "z") ?: 0.0)

        var hurtEvent = false

        if (health != null && lastHealth != null && health != lastHealth) {
            val diff = health - lastHealth!!
            if (diff < 0) {
                val source = Refl.call(player, "getLastDamageSource")
                val type = Refl.call(source, "getMsgId")?.toString()
                    ?: Refl.call(Refl.call(source, "type"), "msgId")?.toString()
                    ?: source?.toString()
                val attacker = Refl.call(source, "getEntity", "getCausingEntity")
                val attackerName = Refl.text(Refl.call(attacker, "getName")) ?: attacker?.javaClass?.simpleName
                val direct = Refl.call(source, "getDirectEntity")
                val directName = if (direct != null && direct !== attacker) direct.javaClass.simpleName else null

                event(
                    "DAMAGE ${fmt(diff)} (health ${fmt(lastHealth!!)} -> ${fmt(health)})" +
                            (type?.let { " source=$it" } ?: "") +
                            (attackerName?.let { " attacker=$it" } ?: "") +
                            (directName?.let { " via=$it" } ?: "")
                )
                hurtEvent = true
            } else {
                event("HEAL +${fmt(diff)} (health ${fmt(lastHealth!!)} -> ${fmt(health)})")
            }
        }

        if (!hurtEvent && hurt > lastHurt && lastHurt == 0.0) {
            event("HURT animation without health loss (blocked / 0 damage hit)")
            hurtEvent = true
        }

        if (hurtEvent) watchKnockback = 3

        if (absorption != null && lastAbsorption != null && absorption != lastAbsorption) {
            event("ABSORPTION ${fmt(lastAbsorption!!)} -> ${fmt(absorption)}")
        }

        if (food != null && lastFood != null && food != lastFood) {
            event("FOOD ${fmt(lastFood!!)} -> ${fmt(food)}")
        }

        if (vel != null && lastVel != null) {
            val dx = vel[0] - lastVel!![0]
            val dy = vel[1] - lastVel!![1]
            val dz = vel[2] - lastVel!![2]
            val horizontal = hypot(dx, dz)

            if (watchKnockback > 0 && (horizontal > 0.15 || dy > 0.2)) {
                event(
                    "KNOCKBACK delta=(${fmt(dx)}, ${fmt(dy)}, ${fmt(dz)}) " +
                            "velocity=(${fmt(vel[0])}, ${fmt(vel[1])}, ${fmt(vel[2])}) " +
                            "horizontal=${fmt(horizontal)}"
                )
                watchKnockback = 0
            } else if (horizontal > 0.6 || dy > 0.8) {
                event(
                    "VELOCITY JUMP delta=(${fmt(dx)}, ${fmt(dy)}, ${fmt(dz)}) " +
                            "(explosion / launch / knockback without hurt?)"
                )
            }
        }
        if (watchKnockback > 0) watchKnockback--

        if (lastPos != null) {
            val moved = sqrt(
                (pos[0] - lastPos!![0]) * (pos[0] - lastPos!![0]) + (pos[2] - lastPos!![2]) * (pos[2] - lastPos!![2])
            )
            if (moved > 8.0) {
                event(
                    "TELEPORT (${fmt(lastPos!![0])}, ${fmt(lastPos!![1])}, ${fmt(lastPos!![2])}) -> " +
                            "(${fmt(pos[0])}, ${fmt(pos[1])}, ${fmt(pos[2])})"
                )
            }
        }

        val onGround = (Refl.call(player, "onGround", "isOnGround") as? Boolean)
        if (onGround != null) {
            if (lastOnGround == true && !onGround && vel != null && vel[1] > 0.3) event("JUMP")
            lastOnGround = onGround
        }

        val sprint = Refl.call(player, "isSprinting") as? Boolean ?: false
        if (sprint != lastSprint) event(if (sprint) "SPRINT started" else "SPRINT stopped")
        lastSprint = sprint

        val sneak = Refl.call(player, "isCrouching", "isShiftKeyDown") as? Boolean ?: false
        if (sneak != lastSneak) event(if (sneak) "SNEAK started" else "SNEAK stopped")
        lastSneak = sneak

        val fire = Refl.call(player, "isOnFire") as? Boolean ?: false
        if (fire != lastFire) event(if (fire) "ON FIRE" else "FIRE stopped")
        lastFire = fire

        val item = describe(Refl.call(player, "getMainHandItem"))
        if (lastItem != null && item != lastItem) event("HELD ITEM $lastItem -> $item")
        lastItem = item

        if (health != null && health <= 0.0 && !dead) {
            dead = true
            event("DEAD at (${fmt(pos[0])}, ${fmt(pos[1])}, ${fmt(pos[2])})")
        } else if (health != null && health > 0.0) {
            dead = false
        }

        lastHealth = health
        lastAbsorption = absorption
        lastFood = food
        lastHurt = hurt
        lastVel = vel
        lastPos = pos
    }

    private fun trackInventory(player: Any) {
        val inventory = Refl.call(player, "getInventory") ?: return
        val size = (Refl.call(inventory, "getContainerSize") as? Number)?.toInt() ?: return
        val snapshot = Array(size) { describe(Refl.callArg(inventory, "getItem", it)) }
        val previous = lastInventory

        if (previous != null && previous.size == snapshot.size && joinGrace == 0) {
            for (i in snapshot.indices) {
                if (snapshot[i] != previous[i]) {
                    event("INVENTORY ${slotName(i)}: ${previous[i]} -> ${snapshot[i]}")
                }
            }
        }
        lastInventory = snapshot

        val selected = (Refl.get(inventory, "selectedSlot") as? Number)?.toInt()
            ?: (Refl.field(inventory, "selected") as? Number)?.toInt()
        if (selected != null) {
            if (lastSelectedSlot != null && selected != lastSelectedSlot) {
                event("HOTBAR slot ${lastSelectedSlot!! + 1} -> ${selected + 1}")
            }
            lastSelectedSlot = selected
        }
    }

    private fun trackContainer(player: Any) {
        val menu = Refl.get(player, "containerMenu") ?: return
        val name = menu.javaClass.simpleName
        val isPlayerMenu = name == "InventoryMenu"
        val slots = Refl.get(menu, "slots") as? List<*>

        if (name != lastMenuName) {
            if (lastMenuName != null) {
                if (isPlayerMenu) {
                    event("CONTAINER closed: $lastMenuName")
                    safe { TriggerType.ContainerClose.triggerAll(lastMenuName) }
                } else {
                    event("CONTAINER opened: $name (${slots?.size ?: "?"} slots)")
                    safe { TriggerType.ContainerOpen.triggerAll(name, slots?.size ?: 0) }
                }
            }
            lastMenuName = name
            lastMenuSlots = null
            menuGrace = 4
        }

        val snapshot = slots?.map { describe(Refl.call(it, "getItem")) }
        val previous = lastMenuSlots

        if (menuGrace > 0) {
            menuGrace--
        } else if (!isPlayerMenu && snapshot != null && previous != null && previous.size == snapshot.size) {
            for (i in snapshot.indices) {
                if (snapshot[i] != previous[i]) {
                    event("CONTAINER $name slot $i: ${previous[i]} -> ${snapshot[i]}")
                }
            }
        }
        lastMenuSlots = snapshot

        val carried = describe(Refl.call(menu, "getCarried"))
        if (lastCarried != null && carried != lastCarried && joinGrace == 0) {
            event("CURSOR item: $lastCarried -> $carried")
        }
        lastCarried = carried
    }

    private fun trackTarget(mc: Minecraft) {
        val entity = Refl.get(mc, "crosshairPickEntity")
        val target = if (entity == null) null else
            (Refl.text(Refl.call(entity, "getName")) ?: entity.javaClass.simpleName) + " (" + entity.javaClass.simpleName + ")"

        if (target != lastTarget) {
            if (target != null) event("TARGET entity: $target")
            else if (lastTarget != null) event("TARGET lost: $lastTarget")
            lastTarget = target
        }
    }

    private fun trackXpAndEffects(player: Any) {
        val xp = Refl.num(player, "experienceLevel")
        if (xp != null) {
            if (lastXp != null && xp != lastXp) event("XP level ${lastXp!!.toInt()} -> ${xp.toInt()}")
            lastXp = xp
        }

        val effects = Refl.call(player, "getActiveEffects") as? Collection<*> ?: return
        val current = effects.map {
            val id = Refl.call(it, "getDescriptionId")?.toString() ?: it.toString()
            val level = ((Refl.call(it, "getAmplifier") as? Number)?.toInt() ?: 0) + 1
            "$id $level"
        }.toSet()

        if (joinGrace == 0) {
            (current - lastEffects).forEach { event("EFFECT gained: $it") }
            (lastEffects - current).forEach { event("EFFECT lost: $it") }
        }
        lastEffects = current
    }
}
