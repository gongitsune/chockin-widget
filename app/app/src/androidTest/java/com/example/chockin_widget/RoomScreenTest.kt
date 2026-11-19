package com.example.chockin_widget

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.chockin_widget.ui.theme.ChockinWidgetTheme
import org.junit.Rule
import org.junit.Test

class RoomScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsMembersAndOptionalMessagesFromRoomResponse() {
        val room = parseRoom("""{
            "serverTime":"2026-11-19T10:00:00+09:00","occupied":true,"presentCount":2,
            "members":[
                {"displayName":"あおい","checkedInAt":"2026-11-19T09:30:00+09:00","avatarUrl":null,"statusMessage":"作業中"},
                {"displayName":"そら","checkedInAt":"2026-11-19T09:00:00+09:00","avatarUrl":null,"statusMessage":null}
            ]}""")
        compose.setContent { ChockinWidgetTheme { RoomScreen(RoomState(room), false) {} } }
        listOf("あおい", "作業中", "そら", "2").forEach {
            compose.onNodeWithText(it).assertIsDisplayed()
        }
    }

    @Test fun showsEmptyRoomAfterSuccessfulRefresh() {
        val room = parseRoom("""{"serverTime":"2026-11-19T10:00:00Z","occupied":false,"presentCount":0,"members":[]}""")
        compose.setContent { ChockinWidgetTheme { RoomScreen(RoomState(room), false) {} } }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.onNodeWithText(context.getString(R.string.room_empty)).assertIsDisplayed()
    }
}
