package com.flightchat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.flightchat.model.ChatMessage
import com.flightchat.ui.ChatScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ChatLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shrinkingViewportKeepsLatestMessageAndQuoteCanBeSent() {
        val height = mutableStateOf(650.dp)
        val messages = (1..120).map { ChatMessage(id = it.toLong(), messageId = "quote-$it", content = "Message $it", nickname = "Alice") }
        var sentReply: ChatMessage? = null
        var sentText = ""
        compose.setContent {
            MaterialTheme {
                Box(Modifier.height(height.value)) {
                    ChatScreen(messages, emptyList(), onSendMessage = { text, reply, _ -> sentText = text; sentReply = reply },
                        onHistoryLimit = {}, onRetry = {}, onLeaveRoom = {}, currentUserId = "me", isConnected = true)
                }
            }
        }
        compose.onNodeWithText("Message 120").assertIsDisplayed()
        compose.runOnIdle { height.value = 380.dp }
        compose.onNodeWithText("Message 120").assertIsDisplayed()
        compose.onNodeWithText("Message 120").performTouchInput { longClick() }
        compose.onNodeWithText("引用 Alice：Message 120").assertIsDisplayed()
        // Restore the full window before the real IME opens; the artificial resize above models the IME already.
        compose.runOnIdle { height.value = 650.dp }
        compose.onNode(hasSetTextAction()).performTextInput("Reply text")
        compose.onNodeWithText("发送").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals("Reply text", sentText)
            assertEquals("quote-120", sentReply?.messageId)
        }
        compose.onNodeWithText("引用 Alice：Message 120").assertDoesNotExist()
    }
}
