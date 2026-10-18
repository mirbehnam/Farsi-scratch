package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.AvatarCatalog
import ir.behnamapps.fascratch.inappbilling.learning.AvatarItem
import org.junit.Assert.*
import org.junit.Test

class AvatarMarketplaceItemsTest {
    @Test fun defaultIsFreeOwnedAndSelectableWithoutCoins() {
        val items = AvatarCatalog("account", true, emptyList()).displayItems("junior")
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("default", item.id)
        assertEquals(0, item.price)
        assertTrue(item.owned) // Owned items use equip, not buy/confirmation.
        assertFalse(item.forSale)
        assertFalse(item.equipped)
    }

    @Test fun currentSelectionOverridesStaleCatalogAndKeepsServerOrdering() {
        val catalog = AvatarCatalog("account", true, listOf(
            AvatarItem("explorer", "کاوشگر", 200, "", false, false),
            AvatarItem("junior", "جونیور", 100, "", true, true)))
        val items = catalog.displayItems("default")
        assertEquals(listOf("default", "explorer", "junior"), items.map { it.id })
        assertEquals(listOf("default"), items.filter { it.equipped }.map { it.id })
        assertTrue(catalog.items.last().equipped) // Presentation never mutates the cached catalog.
        assertEquals(listOf("junior"), catalog.displayItems("junior").filter { it.equipped }.map { it.id })
    }

    @Test fun serverDefaultCannotCreateDuplicateGridKeysOrTurnItIntoAPurchase() {
        val catalog = AvatarCatalog("account", true, listOf(AvatarItem("default", "duplicate", 100, "", false, false)))
        val item = catalog.displayItems("default").single()
        assertEquals(0, item.price)
        assertTrue(item.owned)
        assertTrue(item.equipped)
    }
}
