package com.sushitriggers.ctjs.minecraft.libs

import com.sushitriggers.ctjs.utils.Refl
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.phys.Vec3

class ItemData(
    @JvmField val slot: Int,
    @JvmField val name: String,
    @JvmField val count: Int,
    @JvmField val id: String,
    @JvmField val lore: Array<String>,
) {
    val empty: Boolean get() = count <= 0 || id == "empty"

    fun hasLore(text: String) = lore.any { it.contains(text, ignoreCase = true) }

    override fun toString() = "$name x$count"
}

class EntityData(
    @JvmField val name: String,
    @JvmField val type: String,
    @JvmField val x: Double,
    @JvmField val y: Double,
    @JvmField val z: Double,
    @JvmField val distance: Double,
)

object GameUtil {
    val mc: Minecraft get() = Minecraft.getInstance()

    /** In 26.x the current screen lives on mc.gui, not on Minecraft */
    fun screen(): Any? = Refl.call(Refl.get(mc, "gui"), "screen") ?: Refl.get(mc, "screen")

    fun item(stack: Any?, slot: Int): ItemData {
        if (stack == null || Refl.call(stack, "isEmpty") == true) {
            return ItemData(slot, "", 0, "empty", emptyArray())
        }

        val name = Refl.text(Refl.call(stack, "getHoverName")) ?: ""
        val count = (Refl.call(stack, "getCount") as? Number)?.toInt() ?: 1
        val id = Refl.call(stack, "getItem")?.toString() ?: "?"
        return ItemData(slot, name, count, id, lore(stack))
    }

    fun lore(stack: Any): Array<String> {
        return try {
            val type = Class.forName("net.minecraft.core.component.DataComponents").getField("LORE").get(null)
            val lore = Refl.callN(stack, "get", type) ?: return emptyArray()
            val lines = Refl.call(lore, "lines") as? List<*> ?: return emptyArray()
            lines.map { Refl.text(it) ?: "" }.toTypedArray()
        } catch (e: Throwable) {
            emptyArray()
        }
    }

    fun setScreen(screen: Any?) {
        val mc = mc
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

@Suppress("unused")
object PlayerLib {
    private val player get() = GameUtil.mc.player

    @JvmStatic fun exists() = player != null

    @JvmStatic fun getName(): String = Refl.text(Refl.call(player, "getName")) ?: ""

    @JvmStatic fun getX(): Double = Refl.num(player, "x") ?: 0.0
    @JvmStatic fun getY(): Double = Refl.num(player, "y") ?: 0.0
    @JvmStatic fun getZ(): Double = Refl.num(player, "z") ?: 0.0

    @JvmStatic fun getYaw(): Double = (Refl.call(player, "getYRot") as? Number)?.toDouble() ?: 0.0
    @JvmStatic fun getPitch(): Double = (Refl.call(player, "getXRot") as? Number)?.toDouble() ?: 0.0

    @JvmStatic fun getHealth(): Double = (Refl.call(player, "getHealth") as? Number)?.toDouble() ?: 0.0
    @JvmStatic fun getMaxHealth(): Double = (Refl.call(player, "getMaxHealth") as? Number)?.toDouble() ?: 20.0
    @JvmStatic fun getAbsorption(): Double = (Refl.call(player, "getAbsorptionAmount") as? Number)?.toDouble() ?: 0.0
    @JvmStatic fun getFood(): Double = Refl.num(Refl.call(player, "getFoodData"), "foodLevel") ?: 0.0
    @JvmStatic fun getXpLevel(): Int = (Refl.get(player, "experienceLevel") as? Number)?.toInt() ?: 0

    @JvmStatic fun isSneaking(): Boolean = Refl.call(player, "isCrouching", "isShiftKeyDown") == true
    @JvmStatic fun isSprinting(): Boolean = Refl.call(player, "isSprinting") == true
    @JvmStatic fun isOnGround(): Boolean = Refl.call(player, "onGround", "isOnGround") == true
    @JvmStatic fun isOnFire(): Boolean = Refl.call(player, "isOnFire") == true
    @JvmStatic fun isInWater(): Boolean = Refl.call(player, "isInWater") == true

    @JvmStatic fun getMotion(): DoubleArray {
        val v = Refl.call(player, "getDeltaMovement")
        return doubleArrayOf(Refl.num(v, "x") ?: 0.0, Refl.num(v, "y") ?: 0.0, Refl.num(v, "z") ?: 0.0)
    }

    @JvmStatic fun setMotion(x: Double, y: Double, z: Double) {
        Refl.callN(player, "setDeltaMovement", Vec3(x, y, z))
    }

    @JvmStatic fun getHeldItem(): ItemData = GameUtil.item(Refl.call(player, "getMainHandItem"), -1)

    @JvmStatic fun getOffhandItem(): ItemData = GameUtil.item(Refl.call(player, "getOffhandItem"), 40)

    @JvmStatic fun getSelectedSlot(): Int {
        val inv = Refl.call(player, "getInventory")
        return ((Refl.call(inv, "getSelectedSlot") ?: Refl.get(inv, "selectedSlot") ?: Refl.field(inv, "selected")) as? Number)?.toInt() ?: 0
    }

    @JvmStatic fun setSelectedSlot(slot: Int) {
        val inv = Refl.call(player, "getInventory") ?: return
        if (Refl.callN(inv, "setSelectedSlot", slot) == null) {
            try {
                inv.javaClass.getField("selected").setInt(inv, slot)
            } catch (e: Throwable) {
            }
        }
    }

    /** 0-8 hotbar, 9-35 main, 36-39 armor (boots..helmet), 40 offhand */
    @JvmStatic fun getInventory(): Array<ItemData> {
        val inv = Refl.call(player, "getInventory") ?: return emptyArray()
        val size = (Refl.call(inv, "getContainerSize") as? Number)?.toInt() ?: 0
        return Array(size) { GameUtil.item(Refl.callN(inv, "getItem", it), it) }
    }

    @JvmStatic fun getItem(slot: Int): ItemData = GameUtil.item(Refl.callN(Refl.call(player, "getInventory"), "getItem", slot), slot)

    @JvmStatic fun findItem(text: String): Int {
        return getInventory().firstOrNull { !it.empty && it.name.contains(text, ignoreCase = true) }?.slot ?: -1
    }

    @JvmStatic fun getDimension(): String =
        Refl.call(Refl.get(GameUtil.mc, "level"), "dimension")?.toString()?.substringAfterLast("/")?.trimEnd(']')?.trim() ?: ""

    @JvmStatic fun getEffects(): Array<String> {
        val effects = Refl.call(player, "getActiveEffects") as? Collection<*> ?: return emptyArray()
        return effects.map { (Refl.call(it, "getDescriptionId")?.toString() ?: it.toString()) }.toTypedArray()
    }
}

@Suppress("unused")
object WorldLib {
    private val level get() = Refl.get(GameUtil.mc, "level")

    @JvmStatic fun exists() = level != null

    @JvmStatic fun getBlockAt(x: Int, y: Int, z: Int): String {
        val l = level as? net.minecraft.world.level.BlockGetter ?: return "air"
        return try {
            val state = l.getBlockState(net.minecraft.core.BlockPos(x, y, z))
            Refl.call(Refl.call(state, "getBlock"), "getDescriptionId")?.toString()?.removePrefix("block.")?.replace("minecraft.", "") ?: "?"
        } catch (e: Throwable) {
            "?"
        }
    }

    @JvmStatic fun getDayTime(): Long = (Refl.call(level, "getDayTime") as? Number)?.toLong() ?: 0L
    @JvmStatic fun getGameTime(): Long = (Refl.call(level, "getGameTime") as? Number)?.toLong() ?: 0L
    @JvmStatic fun isRaining(): Boolean = Refl.call(level, "isRaining") == true

    @JvmStatic fun getEntities(radius: Double): Array<EntityData> {
        val player = GameUtil.mc.player ?: return emptyArray()
        val px = Refl.num(player, "x") ?: 0.0
        val py = Refl.num(player, "y") ?: 0.0
        val pz = Refl.num(player, "z") ?: 0.0

        val entities = (Refl.call(level, "entitiesForRendering", "getAllEntities") as? Iterable<*>) ?: return emptyArray()
        val out = ArrayList<EntityData>()
        for (e in entities) {
            if (e == null || e === player) continue
            val x = Refl.num(e, "x") ?: continue
            val y = Refl.num(e, "y") ?: continue
            val z = Refl.num(e, "z") ?: continue
            val d = Math.sqrt((x - px) * (x - px) + (y - py) * (y - py) + (z - pz) * (z - pz))
            if (d > radius) continue
            out += EntityData(Refl.text(Refl.call(e, "getName")) ?: "", e.javaClass.simpleName, x, y, z, d)
        }
        return out.sortedBy { it.distance }.toTypedArray()
    }

    @JvmStatic fun getPlayerNames(): Array<String> {
        val conn = Refl.call(GameUtil.mc, "getConnection")
        val infos = Refl.call(conn, "getOnlinePlayers") as? Collection<*> ?: return emptyArray()
        return infos.mapNotNull { info ->
            val profile = Refl.call(info, "getProfile", "profile")
            (Refl.call(profile, "name", "getName") as? String)
        }.toTypedArray()
    }

    @JvmStatic fun getScoreboard(): Array<String> {
        return try {
            val scoreboard = Refl.call(level, "getScoreboard") ?: return emptyArray()
            val slotClass = Class.forName("net.minecraft.world.scores.DisplaySlot")
            val sidebar = slotClass.enumConstants!!.first { (it as Enum<*>).name == "SIDEBAR" }
            val objective = Refl.callN(scoreboard, "getDisplayObjective", sidebar) ?: return emptyArray()
            val scores = Refl.callN(scoreboard, "listPlayerScores", objective) as? Collection<*> ?: return emptyArray()

            val title = Refl.text(Refl.call(objective, "getDisplayName"))
            val lines = ArrayList<Pair<Int, String>>()
            for (entry in scores) {
                val owner = Refl.call(entry, "owner") as? String ?: continue
                if (owner.startsWith("#")) continue
                val value = (Refl.call(entry, "value") as? Number)?.toInt() ?: 0
                val team = Refl.callN(scoreboard, "getPlayersTeam", owner)
                val component = Component.literal(owner)
                val formatted = try {
                    val m = team?.javaClass?.methods?.firstOrNull { it.name == "formatNameForTeam" && it.parameterCount == 2 }
                    if (m != null) Refl.text(m.invoke(null, team, component)) else owner
                } catch (e: Throwable) {
                    owner
                }
                lines += value to (formatted ?: owner)
            }
            val sorted = lines.sortedByDescending { it.first }.map { it.second }
            (listOfNotNull(title) + sorted).toTypedArray()
        } catch (e: Throwable) {
            emptyArray()
        }
    }
}

@Suppress("unused")
object ClientLib {
    @JvmStatic fun chat(message: String) = ChatLib.chat(message)

    @JvmStatic fun say(message: String) {
        Refl.callN(Refl.call(GameUtil.mc, "getConnection"), "sendChat", message)
    }

    @JvmStatic fun command(command: String) {
        Refl.callN(Refl.call(GameUtil.mc, "getConnection"), "sendCommand", command.removePrefix("/"))
    }

    @JvmStatic fun getFps(): Int = (Refl.call(GameUtil.mc, "getFps") as? Number)?.toInt() ?: 0

    @JvmStatic fun getServerAddress(): String =
        Refl.get(Refl.call(GameUtil.mc, "getCurrentServer"), "ip")?.toString() ?: ""

    @JvmStatic fun isSingleplayer(): Boolean = Refl.call(GameUtil.mc, "hasSingleplayerServer") == true

    @JvmStatic fun getScreenName(): String = GameUtil.screen()?.javaClass?.simpleName ?: ""

    @JvmStatic fun getScreenTitle(): String = Refl.text(Refl.call(GameUtil.screen(), "getTitle")) ?: ""

    @JvmStatic fun closeScreen() = GameUtil.setScreen(null)

    @JvmStatic fun copy(text: String) {
        GameUtil.mc.execute {
            val handler = Refl.get(GameUtil.mc, "keyboardHandler")
            Refl.callN(handler, "setClipboard", text)
        }
    }

    /** mode: PICKUP, QUICK_MOVE, SWAP, CLONE, THROW, QUICK_CRAFT, PICKUP_ALL */
    @JvmStatic fun clickSlot(slot: Int, button: Int, mode: String) {
        GameUtil.mc.execute {
            try {
                val mc = GameUtil.mc
                val player = mc.player ?: return@execute
                val menu = Refl.get(player, "containerMenu") ?: return@execute
                val containerId = (Refl.get(menu, "containerId") as? Number)?.toInt() ?: return@execute
                val gameMode = Refl.get(mc, "gameMode") ?: return@execute

                val method = gameMode.javaClass.methods.firstOrNull {
                    it.parameterCount == 5 && (it.name.contains("ContainerClick") || it.name.contains("InventoryMouseClick"))
                } ?: return@execute
                val clickType = method.parameterTypes[3].enumConstants!!.first { (it as Enum<*>).name == mode.uppercase() }
                method.invoke(gameMode, containerId, slot, button, clickType, player)
            } catch (e: Throwable) {
            }
        }
    }
}

@Suppress("unused")
object ContainerLib {
    private val screen get() = GameUtil.screen()
    private val menu get() = Refl.get(GameUtil.mc.player, "containerMenu")

    @JvmStatic fun isOpen(): Boolean {
        val m = menu ?: return false
        return screen != null && m.javaClass.simpleName != "InventoryMenu"
    }

    @JvmStatic fun getTitle(): String = Refl.text(Refl.call(screen, "getTitle")) ?: ""

    @JvmStatic fun getSize(): Int = (Refl.get(menu, "slots") as? List<*>)?.size ?: 0

    @JvmStatic fun getItem(slot: Int): ItemData {
        val slots = Refl.get(menu, "slots") as? List<*> ?: return GameUtil.item(null, slot)
        val s = slots.getOrNull(slot) ?: return GameUtil.item(null, slot)
        return GameUtil.item(Refl.call(s, "getItem"), slot)
    }

    @JvmStatic fun getItems(): Array<ItemData> {
        val slots = Refl.get(menu, "slots") as? List<*> ?: return emptyArray()
        return slots.mapIndexedNotNull { i, s ->
            val item = GameUtil.item(Refl.call(s, "getItem"), i)
            if (item.empty) null else item
        }.toTypedArray()
    }

    @JvmStatic fun getCursorItem(): ItemData = GameUtil.item(Refl.call(menu, "getCarried"), -1)

    @JvmStatic fun click(slot: Int, button: Int, mode: String) = ClientLib.clickSlot(slot, button, mode)

    @JvmStatic fun close() = ClientLib.closeScreen()
}
