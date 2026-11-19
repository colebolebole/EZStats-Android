package com.ezstats.android.data

// Organizations, folders and homes (EZStats 0.8+): the tree the dashboard draws, and the rules about what may go where.
// The server enforces every rule below too; they are here so the app only offers what will work.

/** The id the dashboard uses for the devices that are in no home. */
const val NO_HOME = "none"

/** A group's depth is 0 at the top, and nesting stops at depth 4 (five levels). */
const val MAX_DEPTH = 4

/** Every organization, folder and home as a [Group]: organizations first, then folders, then homes, each as arranged. */
fun StateData.allGroups(): List<Group> =
    orgs + folders + homes.map { Group(it.id, it.name, it.parent, GroupKind.HOME) }

/** Lookups over the tree of groups. */
class GroupIndex(val groups: List<Group>) {
    val byId: Map<String, Group> = groups.associateBy { it.id }

    /** What a group is in; a parent that doesn't exist counts as the top, so nothing is ever lost from view. */
    fun parentOf(g: Group): String = if (g.parent in byId) g.parent else ""

    private val byParent: Map<String, List<Group>> = groups.groupBy { parentOf(it) }

    /** What is directly inside [parent] ("" is the top): organizations, then folders, then homes, each as arranged. */
    fun children(parent: String): List<Group> = byParent[parent].orEmpty()

    /** 0 at the top. */
    fun depth(g: Group): Int {
        var d = 0
        var cur = g
        val seen = hashSetOf(g.id)
        while (true) {
            val p = byId[parentOf(cur)] ?: break
            if (!seen.add(p.id)) break
            d++
            cur = p
        }
        return d
    }

    /** How many levels there are below [g] (0 for a home, or an empty folder). */
    fun height(g: Group, seen: MutableSet<String> = hashSetOf()): Int {
        if (!seen.add(g.id)) return 0
        return children(g.id).maxOfOrNull { 1 + height(it, seen) } ?: 0
    }

    /** Everything inside [id], at any depth. */
    fun descendants(id: String): Set<String> {
        val out = linkedSetOf<String>()
        fun walk(p: String) {
            children(p).forEach { if (out.add(it.id)) walk(it.id) }
        }
        walk(id)
        return out
    }

    /** "Acme Co › Kyleton › House 1". */
    fun path(g: Group): String {
        val names = ArrayDeque<String>()
        var cur: Group? = g
        val seen = hashSetOf<String>()
        while (cur != null && seen.add(cur.id)) {
            names.addFirst(cur.name)
            cur = byId[parentOf(cur)]
        }
        return names.joinToString(" › ")
    }

    /** The group [id] and every group above it, nearest first (to unfold them all when a device is shown). */
    fun selfAndAncestors(id: String): List<String> {
        val out = mutableListOf<String>()
        var cur = byId[id]
        while (cur != null && cur.id !in out) {
            out += cur.id
            cur = byId[parentOf(cur)]
        }
        return out
    }
}

/**
 * Where a folder or home may be put: the top (null), or an organization or folder. A group can't go inside itself or
 * what it holds, and [moving] with everything below it must still fit within [MAX_DEPTH] levels. Pass null for [moving]
 * for a group that doesn't exist yet. Organizations never move: they stay at the top.
 */
fun validParents(index: GroupIndex, moving: Group?): List<Group?> {
    val barred = if (moving == null) emptySet() else index.descendants(moving.id) + moving.id
    val below = if (moving == null) 0 else index.height(moving)
    val containers = index.groups.filter { it.kind != GroupKind.HOME && it.id !in barred && index.depth(it) + 1 + below <= MAX_DEPTH }
    return listOf<Group?>(null) + containers
}

/** The groups next to [g]: the same kind, in the same place, in the order they are arranged. */
fun siblingsOf(index: GroupIndex, g: Group): List<Group> =
    index.children(index.parentOf(g)).filter { it.kind == g.kind }

/**
 * The `before` to send to move item [index] of [siblings] one place ([delta] -1 earlier, +1 later): the id of the item
 * it should now sit just before, or "" for the end. Null when it is already as far as it goes that way.
 */
fun orderBefore(siblings: List<String>, index: Int, delta: Int): String? = when {
    index !in siblings.indices -> null
    delta < 0 -> if (index > 0) siblings[index - 1] else null
    else -> if (index < siblings.lastIndex) siblings.getOrElse(index + 2) { "" } else null
}

/** What the dashboard draws, top to bottom: a heading per group (indented by depth) and the devices of the open ones. */
sealed interface DashEntry {
    /** [group] is null for the devices that are in no home. [devices] is everything inside, for the counts and summary. */
    data class Header(
        val group: Group?,
        val depth: Int,
        val devices: List<Device>,
        val expanded: Boolean,
        val canMoveEarlier: Boolean,
        val canMoveLater: Boolean,
    ) : DashEntry

    data class Dev(val device: Device) : DashEntry
}

/**
 * The dashboard as a flat list: organizations at the top, then the folders and homes in each (folders first), then the
 * devices in the order they were arranged, and finally the devices in no home. A group in [collapsed] hides everything
 * in it. With no groups at all it is just the devices.
 */
fun flattenGroups(state: StateData, collapsed: Set<String>): List<DashEntry> {
    val groups = state.allGroups()
    if (groups.isEmpty()) return state.devices.map { DashEntry.Dev(it) }
    val index = GroupIndex(groups)
    val byHome = state.devices.groupBy { it.home }
    val homeIds = state.homes.mapTo(HashSet()) { it.id }
    val loose = state.devices.filter { it.home !in homeIds }

    fun devicesIn(g: Group): List<Device> =
        if (g.kind == GroupKind.HOME) byHome[g.id].orEmpty() else index.children(g.id).flatMap { devicesIn(it) }

    val out = mutableListOf<DashEntry>()
    fun visitAll(list: List<Group>, depth: Int) {
        list.forEach { g ->
            val same = list.filter { it.kind == g.kind }
            val i = same.indexOf(g)
            val open = g.id !in collapsed
            out += DashEntry.Header(g, depth, devicesIn(g), open, i > 0, i < same.lastIndex)
            if (open) {
                if (g.kind == GroupKind.HOME) byHome[g.id].orEmpty().forEach { out += DashEntry.Dev(it) }
                else visitAll(index.children(g.id), depth + 1)
            }
        }
    }
    visitAll(index.children(""), 0)
    if (loose.isNotEmpty()) {
        val open = NO_HOME !in collapsed
        out += DashEntry.Header(null, 0, loose, open, false, false)
        if (open) loose.forEach { out += DashEntry.Dev(it) }
    }
    return out
}

/** Every home with the path to it, in the order the tree shows them (for choosing where a device goes). */
fun homeChoices(state: StateData): List<Pair<Group, String>> {
    val index = GroupIndex(state.allGroups())
    val out = mutableListOf<Pair<Group, String>>()
    fun walk(parent: String) {
        index.children(parent).forEach { g ->
            if (g.kind == GroupKind.HOME) out += g to index.path(g) else walk(g.id)
        }
    }
    walk("")
    // A home that can't be reached from the top (it shouldn't happen) is still offered, by its own name.
    state.homes.filter { h -> out.none { it.first.id == h.id } }.forEach { out += Group(it.id, it.name, it.parent, GroupKind.HOME) to it.name }
    return out
}
