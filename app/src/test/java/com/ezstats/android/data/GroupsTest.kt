package com.ezstats.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Organizations, folders and homes: the tree the dashboard draws and the rules about what may go where. */
class GroupsTest {

    private fun org(id: String, name: String) = Group(id, name, "", GroupKind.ORG)
    private fun folder(id: String, name: String, parent: String = "") = Group(id, name, parent, GroupKind.FOLDER)
    private fun home(id: String, name: String, parent: String = "") = Home(id, name, parent)
    private fun device(key: String, home: String = "") =
        Device(key = key, world = "w", x = 0, y = 0, z = 0, kind = "thermostat", type = "room", label = key, nickname = null, home = home, system = null)

    private fun state(
        orgs: List<Group> = emptyList(),
        folders: List<Group> = emptyList(),
        homes: List<Home> = emptyList(),
        devices: List<Device> = emptyList(),
    ) = StateData("Steve", false, "F", true, null, homes, orgs, folders, devices, emptyList(), emptyMap())

    /**
     * Acme Co
     * ├── Kyleton ── House 1 (d1, d2)
     * └── Depot ──── Warehouse (d3)
     * Misc (a folder at the top, empty)
     * Cottage (a home at the top: d4)
     * and d5 in no home
     */
    private val sample = state(
        orgs = listOf(org("org1", "Acme Co")),
        folders = listOf(folder("fold1", "Kyleton", "org1"), folder("fold2", "Depot", "org1"), folder("fold3", "Misc")),
        homes = listOf(home("home1", "House 1", "fold1"), home("home2", "Warehouse", "fold2"), home("home3", "Cottage")),
        devices = listOf(device("d1", "home1"), device("d2", "home1"), device("d3", "home2"), device("d4", "home3"), device("d5")),
    )

    private fun describe(entries: List<DashEntry>) = entries.map {
        when (it) {
            is DashEntry.Header -> "${"  ".repeat(it.depth)}[${it.group?.name ?: "No home"}]"
            is DashEntry.Dev -> "${"  ".repeat(0)}${it.device.key}"
        }
    }

    @Test
    fun theDashboardIsATreeOfOrganizationsFoldersAndHomes() {
        assertEquals(
            listOf(
                "[Acme Co]", "  [Kyleton]", "    [House 1]", "d1", "d2", "  [Depot]", "    [Warehouse]", "d3",
                "[Misc]", "[Cottage]", "d4", "[No home]", "d5",
            ),
            describe(flattenGroups(sample, emptySet())),
        )
    }

    @Test
    fun aFoldedGroupHidesEverythingInsideItButStillCountsIt() {
        val entries = flattenGroups(sample, setOf("fold1"))
        assertEquals(
            listOf("[Acme Co]", "  [Kyleton]", "  [Depot]", "    [Warehouse]", "d3", "[Misc]", "[Cottage]", "d4", "[No home]", "d5"),
            describe(entries),
        )
        val kyleton = entries.filterIsInstance<DashEntry.Header>().first { it.group?.id == "fold1" }
        assertFalse(kyleton.expanded)
        assertEquals(listOf("d1", "d2"), kyleton.devices.map { it.key }) // what is inside, for the count and summary
        // Folding the organization hides all of it.
        assertEquals(
            listOf("[Acme Co]", "[Misc]", "[Cottage]", "d4", "[No home]", "d5"),
            describe(flattenGroups(sample, setOf("org1"))),
        )
        assertEquals(3, flattenGroups(sample, setOf("org1")).filterIsInstance<DashEntry.Header>().first().devices.size)
    }

    @Test
    fun withNoGroupsItIsJustTheDevicesAsArranged() {
        val plain = state(devices = listOf(device("b"), device("a"), device("c")))
        assertEquals(listOf("b", "a", "c"), describe(flattenGroups(plain, emptySet())))
        // A server before 0.8 has homes and no folders: homes at the top, devices as the server lists them.
        val old = state(homes = listOf(home("h1", "Cottage"), home("h2", "Shed")), devices = listOf(device("x", "h2"), device("y", "h1")))
        assertEquals(listOf("[Cottage]", "y", "[Shed]", "x"), describe(flattenGroups(old, emptySet())))
    }

    @Test
    fun theNoHomeGroupOnlyAppearsWhenSomethingIsInIt() {
        val allHomed = state(homes = listOf(home("h1", "Cottage")), devices = listOf(device("a", "h1")))
        assertEquals(listOf("[Cottage]", "a"), describe(flattenGroups(allHomed, emptySet())))
        // A device whose home no longer exists falls back to "No home" rather than vanishing.
        val stale = state(homes = listOf(home("h1", "Cottage")), devices = listOf(device("a", "h1"), device("b", "deleted")))
        assertEquals(listOf("[Cottage]", "a", "[No home]", "b"), describe(flattenGroups(stale, emptySet())))
    }

    @Test
    fun groupsKnowWhetherTheyCanMoveEarlierOrLater() {
        val headers = flattenGroups(sample, emptySet()).filterIsInstance<DashEntry.Header>().associateBy { it.group?.id }
        // Kyleton and Depot are the two folders in Acme Co; Misc is a folder at the top, so it is not beside them.
        assertFalse(headers.getValue("fold1").canMoveEarlier); assertTrue(headers.getValue("fold1").canMoveLater)
        assertTrue(headers.getValue("fold2").canMoveEarlier); assertFalse(headers.getValue("fold2").canMoveLater)
        assertFalse(headers.getValue("fold3").canMoveEarlier); assertFalse(headers.getValue("fold3").canMoveLater)
        assertFalse(headers.getValue("org1").canMoveEarlier); assertFalse(headers.getValue("org1").canMoveLater)
    }

    @Test
    fun aGroupWhoseParentIsMissingIsShownAtTheTop() {
        val s = state(folders = listOf(folder("f1", "Lost", "nowhere")), homes = listOf(home("h1", "Cottage", "f1")), devices = listOf(device("a", "h1")))
        assertEquals(listOf("[Lost]", "  [Cottage]", "a"), describe(flattenGroups(s, emptySet())))
    }

    @Test
    fun theIndexKnowsDepthHeightPathsAndAncestors() {
        val index = GroupIndex(sample.allGroups())
        val house = index.byId.getValue("home1")
        assertEquals(2, index.depth(house))
        assertEquals(0, index.depth(index.byId.getValue("org1")))
        assertEquals(1, index.height(index.byId.getValue("fold1"))) // Kyleton has a home below it
        assertEquals(2, index.height(index.byId.getValue("org1")))
        assertEquals(0, index.height(house))
        assertEquals("Acme Co › Kyleton › House 1", index.path(house))
        assertEquals("Misc", index.path(index.byId.getValue("fold3")))
        assertEquals(setOf("fold1", "fold2", "home1", "home2"), index.descendants("org1"))
        assertEquals(listOf("home1", "fold1", "org1"), index.selfAndAncestors("home1"))
    }

    @Test
    fun aFolderOrHomeMayGoToTheTopOrInsideAnyOrganizationOrFolderButNotInsideItself() {
        val index = GroupIndex(sample.allGroups())
        // A new group: anywhere that is a container.
        assertEquals(listOf(null, "org1", "fold1", "fold2", "fold3"), validParents(index, null).map { it?.id })
        // Moving Kyleton: not into itself (and its home can't hold anything anyway).
        assertEquals(listOf(null, "org1", "fold2", "fold3"), validParents(index, index.byId.getValue("fold1")).map { it?.id })
        // Depot can go into Kyleton, but not into itself.
        assertEquals(listOf(null, "org1", "fold1", "fold3"), validParents(index, index.byId.getValue("fold2")).map { it?.id })
        // A home can go in any container.
        assertEquals(listOf(null, "org1", "fold1", "fold2", "fold3"), validParents(index, index.byId.getValue("home1")).map { it?.id })
    }

    @Test
    fun nestingStopsAtFiveLevels() {
        // org (depth 0) > a (1) > b (2) > c (3) > d (4): d is as deep as it can be.
        val deep = state(
            orgs = listOf(org("o", "O")),
            folders = listOf(folder("a", "A", "o"), folder("b", "B", "a"), folder("c", "C", "b"), folder("d", "D", "c"), folder("e", "E")),
        )
        val index = GroupIndex(deep.allGroups())
        assertEquals(4, index.depth(index.byId.getValue("d")))
        // A new group can't go inside D (it would be depth 5), but can go in C.
        val newOk = validParents(index, null).map { it?.id }
        assertFalse("d" in newOk)
        assertTrue("c" in newOk)
        // E, a lone folder, can go anywhere up to C, but not inside D.
        assertEquals(listOf(null, "o", "a", "b", "c"), validParents(index, index.byId.getValue("e")).map { it?.id })
        // B holds C and D, two more levels. Where it is now (inside A) it just fits; deeper it would not; and it can't
        // go inside C or D, which it holds.
        assertEquals(listOf(null, "o", "a", "e"), validParents(index, index.byId.getValue("b")).map { it?.id })
        // C holds one more level (D): it fits inside B (depth 2 + 1 + 1 = 4), but not inside D, which it holds.
        assertEquals(listOf(null, "o", "a", "b", "e"), validParents(index, index.byId.getValue("c")).map { it?.id })
    }

    @Test
    fun movingOneStepEarlierOrLaterNamesTheItemItShouldSitBefore() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals("a", orderBefore(ids, 1, -1)) // b earlier: before a
        assertNull(orderBefore(ids, 0, -1)) // already first
        assertEquals("d", orderBefore(ids, 1, 1)) // b later: before d (it ends up after c)
        assertEquals("", orderBefore(ids, 2, 1)) // c later: to the end
        assertNull(orderBefore(ids, 3, 1)) // already last
        assertNull(orderBefore(ids, 9, 1)) // not in the list
        assertEquals("", orderBefore(listOf("a", "b"), 0, 1)) // a later in a pair: to the end
    }

    @Test
    fun everyHomeIsOfferedWithItsPath() {
        assertEquals(
            listOf("home1" to "Acme Co › Kyleton › House 1", "home2" to "Acme Co › Depot › Warehouse", "home3" to "Cottage"),
            homeChoices(sample).map { it.first.id to it.second },
        )
        // A home that can't be reached from the top is still offered.
        val odd = state(homes = listOf(home("h1", "Cottage", "gone")))
        assertEquals(listOf("h1" to "Cottage"), homeChoices(odd).map { it.first.id to it.second })
    }
}
