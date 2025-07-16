package com.r3.developers.cordapptemplate.utxoexample.workflows

import com.r3.developers.cordapptemplate.utxoexample.contracts.ChatContract
import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*

// フローを開始するために必要な、デシリアライズされた引数を保持するクラス。
data class UpdateChatFlowArgs(val id: UUID, val message: String)


// このフローの説明については、入門ドキュメントのChat CorDapp Designセクションを参照してください。
class UpdateChatFlow: ClientStartableFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    // フローが台帳APIを利用できるようにするためにUtxoLedgerServiceをインジェクトします。
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    // SubFlowを実行するにはFlowEngineサービスが必要です。
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        log.info("UpdateNewChatFlow.call() が呼び出されました")

        try {
            // requestBodyからフローへのデシリアライズされた入力引数を取得します。
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, UpdateChatFlowArgs::class.java)

            // 指定されたIDを持つ最新の未消費のChatStateを検索します。
            // 注意：このコードはすべての未消費の状態を取得してからフィルタリングします。
            // これは、多数のチャットがある場合には非効率な操作です。
            // 注意：対応するChatStateがないIDを入力すると、このエラーが発生します（よくあるエラー）。
            val stateAndRef = ledgerService.findUnconsumedStatesByExactType(ChatState::class.java, 100, Instant.now()).results.singleOrNull {
                it.state.contractState.id == flowArgs.id
            } ?: throw CordaRuntimeException("${flowArgs.id} というIDを持つ複数のまたはゼロのチャット状態が見つかりました。")

            // フローを実行しているVnodeとotherMemberのMemberInfoを取得します。
            val myInfo = memberLookup.myInfo()
            val state = stateAndRef.state.contractState

            val members = state.participants.map {
                memberLookup.lookup(it) ?: throw CordaRuntimeException("公開鍵 $it からメンバーが見つかりませんでした。")}
            val otherMember = (members - myInfo).singleOrNull()
                ?: throw CordaRuntimeException("イニシエーター以外の参加者は1人だけである必要があります。")

            // updateMessageヘルパー関数を使用して新しいChatStateを作成します。
            val newChatState = state.updateMessage(myInfo.name, flowArgs.message)

            // UTXOTransactionBuilderを使用してドラフトトランザクションを作成します。
            val txBuilder= ledgerService.createTransactionBuilder()
                .setNotary(stateAndRef.state.notaryName)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(newChatState)
                .addInputState(stateAndRef.ref)
                .addCommand(ChatContract.Update())
                .addSignatories(newChatState.participants)

            // トランザクションビルダーをUTXOSignedTransactionに変換します。UtxoTransactionBuilderの
            // 内容を検証し、現在のノードに属する必要な署名者でトランザクションに署名します。
            val signedTransaction = txBuilder.toSignedTransaction()

            // トランザクションをファイナライズするFinalizeChatSubFlowを呼び出します。
            // 成功した場合、フローは作成されたトランザクションIDの文字列を返します。
            // 成功しなかった場合は、エラーメッセージを返します。
            return flowEngine.subFlow(FinalizeChatSubFlow(signedTransaction, otherMember.name))


        }
        // 例外をキャッチし、ログに記録して例外を再スローします。
        catch (e: Exception) {
            log.warn("リクエストボディ '$requestBody' のutxoフローの処理に失敗しました。理由：'${e.message}'")
            throw e
        }
    }
}

/*
REST経由でフローをトリガーするためのRequestBody：
{
    "clientRequestId": "update-2",
    "flowClassName": "com.r3.developers.cordapptemplate.utxoexample.workflows.UpdateChatFlow",
    "requestBody": {
        "id":"** IDを入力してください **",
        "message": "How are you today?"
        }
}
 */