package com.r3.developers.cordapptemplate.utxoexample.workflows

import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.*

// フローを開始するために必要な、デシリアライズされた引数を保持するクラス。
data class GetChatFlowArgs(val id: UUID, val numberOfRecords: Int)

// messageFromとmessageをペアにするためのクラス。
data class MessageAndSender(val messageFrom: String, val message: String)

// このフローの説明については、入門ドキュメントのChat CorDapp Designセクションを参照してください。
class GetChatFlow: ClientStartableFlow {

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

        log.info("GetChatFlow.call() が呼び出されました")

        // requestBodyからフローへのデシリアライズされた入力引数を取得します。
        val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, GetChatFlowArgs::class.java)

        // 指定されたIDを持つ最新の未消費のChatStateを検索します。
        // 注意：このコードはすべての未消費の状態を取得してからフィルタリングします。
        // これは、多数のチャットがある場合には非効率な操作です。
        // 注意：対応するChatStateがないIDを入力すると、このエラーが発生します（よくあるエラー）。
        val states = ledgerService.findUnconsumedStatesByExactType(ChatState::class.java, 100, Instant.now()).results
        val state = states.singleOrNull {it.state.contractState.id == flowArgs.id}
            ?: throw CordaRuntimeException("${flowArgs.id} というIDを持つ一意の未消費のChatStateが見つかりませんでした")

        // バックチェーンからチャット履歴を取得するresolveMessagesFromBackchain()を呼び出します。
        return jsonMarshallingService.format(resolveMessagesFromBackchain(state, flowArgs.numberOfRecords ))
    }

    // resoveMessageFromBackchain()は、提供されたstateAndRefから開始します。これは、この特定のチャットのバックチェーンの
    // 未消費のヘッドを表し、numberOfRecords引数で指定されたトランザクションの数だけチェーンを後方にたどります。
    // 各トランザクションについて、メッセージと送信者を表すMessageAndSenderをリストに追加し、そのリストを返します。
    @Suspendable
    private fun resolveMessagesFromBackchain(stateAndRef: StateAndRef<ChatState>, numberOfRecords: Int): List<MessageAndSender>{

        // MessageAndSenderを収集するためのMutableListを設定します。
        val messages = mutableListOf<MessageAndSender>()

        // バックチェーンをたどるための初期条件を設定します。
        var currentStateAndRef = stateAndRef
        var recordsToFetch = numberOfRecords
        var moreBackchain = true

        // バックチェーンの開始まで、または十分なレコードが取得されるまでループを続けます。
        while (moreBackchain) {

            // 現在のStateAndRefからトランザクションIDを取得し、保管庫からトランザクションを取得します。
            val transactionId = currentStateAndRef.ref.transactionId
            val transaction = ledgerService.findLedgerTransaction(transactionId)
                ?: throw CordaRuntimeException("トランザクション $transactionId が見つかりません。")

            // トランザクションから出力状態を取得し、それを使用してMessageAndSenderオブジェクトを作成し、
            // 可変リストに追加します。
            val output = transaction.getOutputStates(ChatState::class.java).singleOrNull()
                ?: throw CordaRuntimeException("トランザクション $transactionId にはChatState出力が1つだけ必要です。")
            messages.add(MessageAndSender(output.messageFrom.toString(), output.message))
            // 取得するレコードの数をデクリメントします。
            recordsToFetch--

            // 入力状態への参照を取得します。
            val inputStateAndRefs = transaction.inputStateAndRefs

            // 入力状態がこれ以上ないか（チェーンの開始）、または十分なレコードを取得したかを確認します。
            // トランザクションに入力状態が多すぎて不正な形式になっていないかを確認します。
            // currentStateAndRefを入力StateAndRefに設定し、ループを繰り返します。
            if (inputStateAndRefs.isEmpty() || recordsToFetch == 0) {
                moreBackchain = false
            } else if (inputStateAndRefs.size > 1) {
                throw CordaRuntimeException("トランザクション $transactionId に複数の入力状態が見つかりました。")
            } else {
                @Suppress("UNCHECKED_CAST")
                currentStateAndRef = inputStateAndRefs.single() as StateAndRef<ChatState>
            }
        }
     // 不変リストに変換します。
     return messages.toList()
    }
}

/*
REST経由でフローをトリガーするためのRequestBody：
{
    "clientRequestId": "get-1",
    "flowClassName": "com.r3.developers.cordapptemplate.utxoexample.workflows.GetChatFlow",
    "requestBody": {
        "id":"** IDを入力してください **",
        "numberOfRecords":"4"
    }
}
 */