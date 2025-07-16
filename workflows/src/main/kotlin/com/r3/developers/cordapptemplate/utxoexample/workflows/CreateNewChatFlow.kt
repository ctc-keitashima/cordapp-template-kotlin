package com.r3.developers.cordapptemplate.utxoexample.workflows

import com.r3.developers.cordapptemplate.utxoexample.contracts.ChatContract
import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.application.flows.*
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant

// フローを開始するために必要な、デシリアライズされた引数を保持するクラス。
data class CreateNewChatFlowArgs(val chatName: String, val message: String, val otherMember: String)

// このフローの説明については、入門ドキュメントのChat CorDapp Designセクションを参照してください。
class CreateNewChatFlow: ClientStartableFlow {

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

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    // SubFlowを実行するにはFlowEngineサービスが必要です。
    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {

        log.info("CreateNewChatFlow.call() が呼び出されました")

        try {
            // requestBodyからフローへのデシリアライズされた入力引数を取得します。
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, CreateNewChatFlowArgs::class.java)

            // フローを実行しているVNodeとotherMemberのMemberInfoを取得します。
            // Kotlin CorDappsの良い習慣は、RuntimeExceptionのみをスローすることです。
            // 注意：Java CorDappsでは、メソッドのシグネチャを変更してオーバーライドを壊すため、宣言されたチェック済み例外ではなく、チェックされていないRuntimeExceptionのみをスローできます。
            val myInfo = memberLookup.myInfo()
            val otherMember = memberLookup.lookup(MemberX500Name.parse(flowArgs.otherMember)) ?:
                throw CordaRuntimeException("MemberLookupがフロー引数で指定されたotherMemberを見つけられません。")

            // 入力引数とメンバー情報からChatStateを作成します。
            val chatState = ChatState(
                chatName = flowArgs.chatName,
                messageFrom = myInfo.name,
                message = flowArgs.message,
                participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
            )

            // 公証人を取得します。
            val notary = notaryLookup.notaryServices.single()

            // UTXOTransactionBuilderを使用してドラフトトランザクションを作成します。
            val txBuilder= ledgerService.createTransactionBuilder()
                .setNotary(notary.name)
                .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                .addOutputState(chatState)
                .addCommand(ChatContract.Create())
                .addSignatories(chatState.participants)

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
    "clientRequestId": "create-1",
    "flowClassName": "com.r3.developers.cordapptemplate.utxoexample.workflows.CreateNewChatFlow",
    "requestBody": {
        "chatName":"Chat with Bob",
        "otherMember":"CN=Bob, OU=Test Dept, O=R3, L=London, C=GB",
        "message": "Hello Bob"
        }
}
 */