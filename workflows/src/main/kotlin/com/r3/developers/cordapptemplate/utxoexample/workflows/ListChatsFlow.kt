package com.r3.developers.cordapptemplate.utxoexample.workflows

import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*


// フローの結果を保持するデータクラス。
// ChatStateは直接返すことができません。なぜなら、JsonMarshallingServiceは、
// 基盤となるJacksonシリアライザーが認識する単純なクラスしかシリアライズできないためです。
// したがって、文字列とUUIDのみで構成されるDTOスタイルのオブジェクトを作成します。
// JsonMarshallingService用にカスタムシリアライザーを作成することも可能ですが、
// この単純な例の範囲を超えています。
data class ChatStateResults(val id: UUID, val chatName: String,val messageFromName: String, val message: String)

// このフローの説明については、入門ドキュメントのChat CorDapp Designセクションを参照してください。
class ListChatsFlow : ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    // フローが台帳APIを利用できるようにするためにUtxoLedgerServiceをインジェクトします。
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        log.info("ListChatsFlow.call() が呼び出されました")

        // VNodeの保管庫で未消費の状態を照会し、結果をシリアライズ可能なDTOに変換します。
        val states = ledgerService.findUnconsumedStatesByExactType(ChatState::class.java, 100, Instant.now()).results
        val results = states.map {
            ChatStateResults(
                it.state.contractState.id,
                it.state.contractState.chatName,
                it.state.contractState.messageFrom.toString(),
                it.state.contractState.message) }

        // JsonMarshallingServiceのformat()関数を使用してDTOをJsonにシリアライズします。
        return jsonMarshallingService.format(results)
    }
}

/*
REST経由でフローをトリガーするためのRequestBody：
{
    "clientRequestId": "list-1",
    "flowClassName": "com.r3.developers.cordapptemplate.utxoexample.workflows.ListChatsFlow",
    "requestBody": {}
}
*/
