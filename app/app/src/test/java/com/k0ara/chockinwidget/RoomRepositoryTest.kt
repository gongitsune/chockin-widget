package com.k0ara.chockinwidget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.format.DateTimeParseException

class RoomRepositoryTest {
    @Test fun parsesMembersAndNormalizesOptionalFields() {
        val room = parseRoom("""{
            "serverTime":"2026-11-19T10:00:00+09:00",
            "members":[
                {"displayName":"あおい","avatarUrl":null,"statusMessage":null},
                {"displayName":"そら","avatarUrl":"","statusMessage":"  "},
                {"displayName":"Haru"},
                {"displayName":"Linus","avatarUrl":"https://example.com/avatar.png","statusMessage":"作業中"}
            ]}""")
        assertEquals(listOf(
            Member("あおい", null, null),
            Member("そら", null, null),
            Member("Haru", null, null),
            Member("Linus", "https://example.com/avatar.png", "作業中")
        ), room.members)
        assertEquals(Instant.parse("2026-11-19T01:00:00Z"), room.timestamp)
    }

    @Test fun parsesEmptyMembers() {
        val room = parseRoom("""{"serverTime":"2026-11-19T10:00:00Z","members":[]}""")
        assertTrue(room.members.isEmpty())
        assertEquals(Instant.parse("2026-11-19T10:00:00Z"), room.timestamp)
    }

    @Test(expected = DateTimeParseException::class)
    fun rejectsInvalidServerTime() {
        parseRoom("""{"serverTime":"invalid","members":[]}""")
    }
}
