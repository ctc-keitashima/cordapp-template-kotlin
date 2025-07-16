package com.r3.developers.cordapptemplate.utxoexample.workflows

import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.application.flows.*
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import org.slf4j.LoggerFactory

// このフローの説明については、入門ドキュメントのChat CorDapp Designセクションを参照してください。

// @InitiatingFlowは、イニシエーターをレスポンダーにリンクするために使用されるプロトコルを宣言します。
@InitiatingFlow(protocol = "finalize-chat-protocol")
class FinalizeChatSubFlow(private val signedTransaction: UtxoSignedTransaction, private val otherMember: MemberX500Name): SubFlow<String> {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // フローが台帳APIを利用できるようにするためにUtxoLedgerServiceをインジェクトします。
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @Suspendable
    override fun call(): String {

        log.info("FinalizeChatFlow.call() が呼び出されました")

        // 他のメンバーとのセッションを開始します。
        val session = flowMessaging.initiateFlow(otherMember)

        return try {
            // Cordaが提供するfinalise()関数を呼び出します。これは、カウンターパーティから署名を集め、
            // トランザクションを公証し、各パーティの保管庫にトランザクションを永続化します。
            // 成功すると、作成されたトランザクションのIDが返されます。（これはChatState IDとは異なります）
            val finalizedSignedTransaction = ledgerService.finalize(
                signedTransaction,
                listOf(session)
            )
            // トランザクションIDを文字列に変換して返します。
            finalizedSignedTransaction.transaction.id.toString().also {
                log.info("成功！応答： $it")
            }
        }
        // フローをソフトに失敗させ、フロー例外をスローせずにエラーメッセージを返します。
        catch (e: Exception) {
            log.warn("ファイナリティに失敗しました", e)
            "ファイナリティに失敗しました, ${e.message}"
        }
    }
}

// このフローの説明については、入門ドキュメントのChat CorDapp Designセクションを参照してください。

//@InitiatingByは、イニシエーターをレスポンダーにリンクするために使用されるプロトコルを宣言します。
@InitiatedBy(protocol = "finalize-chat-protocol")
class FinalizeChatResponderFlow: ResponderFlow {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    // フローが台帳APIを利用できるようにするためにUtxoLedgerServiceをインジェクトします。
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {

        log.info("FinalizeChatResponderFlow.call() が呼び出されました")

        try {
            // Initiating Flowのfinalise()関数に応答側を提供するためにreceiveFinality()関数を呼び出します。
            // 応答側がトランザクションに署名すべきかどうかを決定するためのビジネスロジックを含むラムダバリデーターを受け入れます。
            val finalizedSignedTransaction = ledgerService.receiveFinality(session) { ledgerTransaction ->

                // 注意：この例外は、Cordaのロギングがデバッグに設定されている場合にのみログに表示されます。
                val state = ledgerTransaction.getOutputStates(ChatState::class.java).singleOrNull() ?:
                    throw CordaRuntimeException("検証に失敗しました - トランザクションにはChatStateの出力が1つだけではありませんでした。")

                // checkForBannedWords()とcheckMessageFromMatchesCounterparty()関数を使用して、
                // トランザクションに署名するかどうかを確認します。
                checkForBannedWords(state.message)
                checkMessageFromMatchesCounterparty(state, session.counterparty)

                log.info("トランザクションを検証しました- ${ledgerTransaction.id}")
            }
            log.info("レスポンダーフローを終了しました - ${finalizedSignedTransaction.transaction.id}")
        }
        // フローをソフトに失敗させ、例外をログに記録します。
        catch (e: Exception) {
            log.warn("レスポンダーフローが例外的に終了しました", e)
        }
    }
}