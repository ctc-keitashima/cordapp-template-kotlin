package com.r3.developers.cordapptemplate.utxoexample.states

import com.r3.developers.cordapptemplate.utxoexample.contracts.ChatContract
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.BelongsToContract
import net.corda.v5.ledger.utxo.ContractState
import java.security.PublicKey
import java.util.*


// ChatStateは台帳に保存されるデータを表します。チャットは2人の参加者間の一連のメッセージで構成され、
// UUIDで表されます。任意の2人の参加者のペアは、複数のチャットを持つことができます。
// 各ChatStateは、チャット内の2人の参加者間の1つのメッセージを保存します。ChatStateのバックチェーンは、
// チャットの履歴を表します。

@BelongsToContract(ChatContract::class)
data class ChatState(
    // チャットの一意の識別子。
    val id : UUID = UUID.randomUUID(),
    // チャットの一意でない名前。
    val chatName: String,
    // メッセージを送信した参加者のMemberX500Name。
    val messageFrom: MemberX500Name,
    // メッセージ
    val message: String,
    // チャットの参加者。公開鍵で表されます。
    private val participants: List<PublicKey>) : ContractState {

    override fun getParticipants(): List<PublicKey> {
        return participants
    }

    // 前の（入力）ChatStateから新しいChatStateを作成するためのヘルパー関数。
    fun updateMessage(messageFrom: MemberX500Name, message: String) =
        copy(messageFrom = messageFrom, message = message)
}
