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
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.*


data class TestContractFlowArgs(val otherMember: String)

class TestContractFlow: ClientStartableFlow  {

    private companion object {
        val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    // フローが台帳APIを利用できるようにするためにUtxoLedgerServiceをインジェクトします
    @CordaInject
    lateinit var ledgerService: UtxoLedgerService

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {


        val results = mutableMapOf<String, String>()

        log.info("TestContractFlow.call() が呼び出されました")

        class FakeCommand : Command

        try {
            val flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, TestContractFlowArgs::class.java)

            val myInfo = memberLookup.myInfo()

            val otherMember = memberLookup.lookup(MemberX500Name.parse(flowArgs.otherMember)) ?:
            throw CordaRuntimeException("MemberLookupがフロー引数で指定されたotherMemberを見つけられません。")

            // 公証人の名前と公開鍵を取得します。
            val notary = notaryLookup.notaryServices.first()

            // テストで入力StateRefとして参照できる出力Stateを持つ整形式のトランザクションを作成します
            lateinit var inputStateRef: StateRef
            lateinit var chatId: UUID

            try {
                val chatState = ChatState(
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                chatId = chatState.id

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Create())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                inputStateRef = StateRef(signedTransaction.id, 0)
                flowEngine.subFlow(FinalizeChatSubFlow(signedTransaction, otherMember.name))

            } catch (e:Exception) {
                throw CordaRuntimeException("セットアップトランザクションは例外のために作成できませんでした： ${e.message}")
            }




            // *************   テスト開始 ****************

            // 複数のコマンドは許可されていません
            results["複数のコマンドは許可されていません"] = try {
                val chatState = ChatState(
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Create())
                    .addCommand(FakeCommand())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"

            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("単一のコマンドが必要です。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 3人の参加者を持つChatStateは許可されていません
            results["3人の参加者を持つChatStateは許可されていません"]  = try {

                val chatState = ChatState(
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Create())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"

            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("出力状態には2人の参加者のみが必要です。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 作成時の入力状態は許可されていません
            results["作成時の入力状態は許可されていません"] = try {
                val chatState = ChatState(
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputStateRef)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Create())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"

            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがCreateの場合、入力状態は存在しないはずです。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }

            // 作成時に出力状態がゼロであることは許可されていません

                // 「出力状態には2人の参加者のみが必要です。」で最初に失敗するため、テストは省略されました


            // 作成時に2つの出力状態を持つことは許可されていません
            results["作成時に2つの出力状態を持つことは許可されていません"] = try {
                val chatState = ChatState(
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(chatState)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Create())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"
            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがCreateの場合、出力状態は1つだけである必要があります。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 更新時にゼロの入力状態は許可されていません
            results["更新時にゼロの入力状態は許可されていません"] = try {

                val chatState = ChatState(
                    id = chatId,
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Update())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"

            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがUpdateの場合、入力状態は1つだけである必要があります。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 更新時に2つの入力状態は許可されていません
            results["更新時に2つの入力状態は許可されていません"] = try {

                log.info("MB: テスト変更")
                val chatState = ChatState(
                    id = chatId,
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputStateRef)
                    .addInputState(inputStateRef)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Update())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"

            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがUpdateの場合、入力状態は1つだけである必要があります。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 更新時にゼロの出力状態は許可されていません

                // 「出力状態には2人の参加者のみが必要です。」で最初に失敗するため、テストは省略されました


            // 更新時に2つの出力状態は許可されていません
            results["更新時に2つの出力状態は許可されていません"] = try {
                val chatState = ChatState(
                    id = chatId,
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputStateRef)
                    .addOutputState(chatState)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Update())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"
            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがUpdateの場合、出力状態は1つだけである必要があります。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 更新時にIDは変更してはなりません
            results["更新時にIDは変更してはなりません"] = try {
                val chatState = ChatState(
                    id = UUID.randomUUID(),
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputStateRef)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Update())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"
            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがUpdateの場合、IDは変更してはなりません")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 更新時にchatNameは変更してはなりません
            results["更新時にchatNameは変更してはなりません"] = try {
                val chatState = ChatState(
                    id = chatId,
                    chatName = "DummyChat Name has changed",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputStateRef)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Update())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"
            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがUpdateの場合、chatNameは変更してはなりません。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // 更新時に参加者は変更してはなりません
            results["更新時に参加者は変更してはなりません"] = try {
                val chatState = ChatState(
                    id = chatId,
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), myInfo.ledgerKeys.first())
                )

                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addInputState(inputStateRef)
                    .addOutputState(chatState)
                    .addCommand(ChatContract.Update())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"
            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("コマンドがUpdateの場合、参加者は変更してはなりません。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }


            // FakeCommandは許可されていません
            results["FakeCommandは許可されていません"] = try {
                val chatState = ChatState(
                    chatName = "DummyChat",
                    messageFrom = myInfo.name,
                    message = "Dummy Message",
                    participants = listOf(myInfo.ledgerKeys.first(), otherMember.ledgerKeys.first())
                )


                // UTXOTransactionBuilderを使用してドラフトトランザクションを作成します。
                val txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.name)
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(chatState)
                    .addCommand(FakeCommand())
                    .addSignatories(chatState.participants)

                @Suppress("DEPRECATION", "UNUSED_VARIABLE")
                val signedTransaction = txBuilder.toSignedTransaction()

                "失敗"

            } catch (e:Exception) {
                val exceptionMessage =  e.message ?: "例外メッセージなし"
                if (exceptionMessage.contains("許可されていないコマンドです。")) {
                    "成功" }
                else {
                    "契約は失敗しましたが、別の例外が発生しました： ${e.message}"
                }
            }



            return results.toString()

            // 例外をキャッチし、ログに記録して例外を再スローします。
        } catch (e: Exception) {
            log.warn("リクエストボディ '$requestBody' のutxoフローの処理に失敗しました。理由：'${e.message}'")
            throw e
        }
    }

}
/*
{
    "clientRequestId": "dummy-1",
    "flowClassName": "com.r3.developers.cordapptemplate.utxoexample.workflows.TestContractFlow",
    "requestBody": {
        "otherMember":"CN=Bob, OU=Test Dept, O=R3, L=London, C=GB"
    }
}

 */