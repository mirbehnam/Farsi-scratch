package ir.behnamapps.fascratch.inbox

import org.junit.Assert.*
import org.junit.Test

class InboxPolicyTest {
    private fun message(id: String, scope: String = "public", expires: Long? = null, pinned: Boolean = false, published: Long = 1) =
        InboxMessage(id,1,1,"خبر","متن","info",pinned,published,expires,"",emptyList(),null,scope)

    @Test fun unreadOnlyChangesWhenNotificationVersionChanges() {
        assertTrue(InboxPolicy.unread(1,null))
        assertFalse(InboxPolicy.unread(1,1))
        assertFalse(InboxPolicy.unread(1,2))
        assertTrue(InboxPolicy.unread(2,1))
        assertEquals("۲",InboxPolicy.badge(2))
        assertEquals("۹۹+",InboxPolicy.badge(100))
        assertEquals("",InboxPolicy.badge(0))
    }
    @Test fun privateSnapshotNeverSurvivesAccountSwitchOrLogout() {
        val public = listOf(message("news"))
        val private = listOf(message("secret","personal"))
        assertEquals(2,InboxPolicy.visibleMessages(public,private,"a","a",10).size)
        assertEquals(public,InboxPolicy.visibleMessages(public,private,"b","a",10))
        assertEquals(public,InboxPolicy.visibleMessages(public,private,null,null,10))
    }
    @Test fun completeSnapshotsAndExpiryRemoveOldRowsAndPinnedItemsComeFirst() {
        val rows = listOf(message("old",expires = 10),message("recent",published = 9),message("pinned",pinned = true))
        assertEquals(listOf("pinned","recent"),InboxPolicy.visibleMessages(rows,emptyList(),null,null,10).map { it.id })
        assertTrue(InboxPolicy.visibleMessages(emptyList(),emptyList(),null,null,10).isEmpty())
    }
    @Test fun repeatedActionsRespectDebounceAndRetryAfter() {
        assertTrue(InboxPolicy.refreshDue(1000,0,0))
        assertFalse(InboxPolicy.refreshDue(29_999,1000,0))
        assertTrue(InboxPolicy.refreshDue(31_000,1000,0))
        assertFalse(InboxPolicy.refreshDue(31_000,1000,60_000))
        assertTrue(InboxPolicy.refreshDue(60_000,1000,60_000))
    }
    @Test fun externalButtonsAndPrivateImageCredentialsAreStrictlyScoped() {
        val base = "https://api.behnamapp.ir/scratch/v1"
        val hash = "a".repeat(64)
        val path = "/inbox/personal/id/image/$hash"
        assertTrue(InboxPolicy.safeLink("https://behnamapp.ir/news?q=hi"))
        listOf("javascript:alert(1)","http://behnamapp.ir","https://user:pass@example.com","https://127.0.0.1/a","https://foo.local/a","https://example.com/%0a").forEach { assertFalse(it,InboxPolicy.safeLink(it)) }
        assertTrue(InboxPolicy.mediaUrl(base+path,"personal",base,"id",hash))
        assertFalse(InboxPolicy.mediaUrl("https://cdn.example.com/scratch/v1"+path,"personal",base,"id",hash))
        assertFalse(InboxPolicy.mediaUrl(base+path+"/extra","personal",base,"id",hash))
        assertTrue(InboxPolicy.mediaUrl("https://cdn.example.com/photo.webp","public",base,"id",hash))
    }
}
