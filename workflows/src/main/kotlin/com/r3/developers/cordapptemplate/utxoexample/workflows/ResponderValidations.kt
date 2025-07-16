package com.r3.developers.cordapptemplate.utxoexample.workflows

import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name

// 注意：これらの例外は、Cordaのロギングがデバッグに設定されている場合にのみログに表示されます。

// メッセージに禁止用語が含まれていないか確認し、含まれている場合は例外をスローします。
@Suspendable
fun checkForBannedWords(str: String) {
    val bannedWords = listOf("banana", "apple", "pear")
    if (bannedWords.any { str.contains(it) }) {
        throw CordaRuntimeException("検証に失敗しました - メッセージに禁止用語が含まれています")
    }
}

// ChatStateのmessageFromフィールドがイニシエーター（otherMember）の
// memberX500Nameと一致するかどうかを確認し、一致しない場合は例外をスローします。
@Suspendable
fun checkMessageFromMatchesCounterparty(
    state: ChatState,
    otherMember: MemberX500Name,
) {
    if (state.messageFrom != otherMember) {
        throw CordaRuntimeException("検証に失敗しました - messageFromがフローイニシエーターのmemberX500Nameと一致しません")
    }
}
