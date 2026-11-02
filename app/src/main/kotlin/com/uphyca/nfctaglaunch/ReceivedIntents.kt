package com.uphyca.nfctaglaunch

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 受け取ったインテント 1 件分。 */
data class ReceivedIntent(
    val receivedAt: String,
    val action: String?,
    val data: String?,
    val component: String?,
    val techs: List<String>,
    val tagId: String?,
    val ndef: String?,
)

/** Activity が作り直されても残るように、プロセス内で 1 つだけ持つ。 */
object ReceivedIntents {
    private val _items = MutableStateFlow<List<ReceivedIntent>>(emptyList())
    val items: StateFlow<List<ReceivedIntent>> = _items

    fun add(intent: Intent) {
        _items.update { listOf(intent.toReceived()) + it }
    }

    fun clear() {
        _items.value = emptyList()
    }
}

private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun Intent.toReceived(): ReceivedIntent {
    val tag: Tag? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(NfcAdapter.EXTRA_TAG)
    }
    val messages: Array<NdefMessage>? = if (Build.VERSION.SDK_INT >= 33) {
        getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)?.filterIsInstance<NdefMessage>()?.toTypedArray()
    }
    val record = messages?.firstOrNull()?.records?.firstOrNull()
    val id = getByteArrayExtra(NfcAdapter.EXTRA_ID) ?: tag?.id
    return ReceivedIntent(
        receivedAt = TIME.format(Instant.now().atZone(ZoneId.systemDefault())),
        action = action,
        data = dataString,
        component = component?.shortClassName,
        techs = tag?.techList?.map { it.removePrefix("android.nfc.tech.") } ?: emptyList(),
        tagId = id?.joinToString("") { "%02X".format(it) },
        ndef = record?.let { r -> r.toUri()?.toString() ?: r.toMimeType() ?: "TNF=${r.tnf}" },
    )
}
