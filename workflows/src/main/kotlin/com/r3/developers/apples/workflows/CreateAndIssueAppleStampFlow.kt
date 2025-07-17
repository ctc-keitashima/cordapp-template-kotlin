package com.r3.developers.apples.workflows

import net.corda.v5.application.flows.*
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.base.types.MemberX500Name

import com.r3.developers.apples.states.AppleStamp
import com.r3.developers.apples.contracts.AppleCommands

import java.util.UUID
import java.time.Instant
import java.time.temporal.ChronoUnit

data class CreateAndIssueAppleStampRequest(
    val stampDescription: String,   // 説明文
    val holder: MemberX500Name,     // リンゴ引換券の所有者（つまり、引換券を渡す相手）
)

/* 
    AppleStamp（リンゴ引換券） を作成する
    作成したリンゴ引換券は、即座に相手に渡す
*/
@InitiatingFlow(protocol = "create-and-issue-apple-stamp")
class CreateAndIssueAppleStampFlow : ClientStartableFlow{
    @CordaInject
    lateinit var flowMessaging: FlowMessaging

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @CordaInject
    lateinit var memberLookup: MemberLookup

    @CordaInject
    lateinit var notaryLookup: NotaryLookup

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(requestBody: ClientRequestBody): String {
        val request = requestBody.getRequestBodyAs(
            jsonMarshallingService,
            CreateAndIssueAppleStampRequest::class.java)
        val stampDescription = request.stampDescription
        val holderName = request.holder

        // Retrieve the notaries public key (this will change)
        val notaryInfo = notaryLookup.notaryServices.single()

        // 発行者は私
        val issuer = memberLookup.myInfo().ledgerKeys.first()

        // リンゴ引換券の渡し先はAPI引数のものを利用
        val holder = memberLookup.lookup(holderName)
            ?.let { it.ledgerKeys.first() }
            ?: throw IllegalArgumentException("The holder $holderName does not exist within the network")
    
        // リンゴ引換券の状態を作成
        val newStamp = AppleStamp(
            id = UUID.randomUUID(),
            stampDesc = stampDescription,
            issuer = issuer,
            holder = holder,
            participants = listOf(issuer, holder)   // 引換券の関係者は、発行者と渡す人の２名
        )

        // トランザクションを作成
        val transaction = utxoLedgerService.createTransactionBuilder()
            .setNotary(notaryInfo.name)
            .addOutputState(newStamp)
            .addCommand(AppleCommands.Issue())
            .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
            .addSignatories(listOf(issuer, holder))
            .toSignedTransaction()

        // トランザクションを決定（ファイナライズ）するために、取引相手とのセッションを開始
        val session = flowMessaging.initiateFlow(holderName)

        return try {
            // トランザクションを決定（ファイナライズ）する
            // この処理の中で、フレームワークがレスポンダーに送信して応答を待つ処理をする
            utxoLedgerService.finalize(transaction, listOf(session))
            newStamp.id.toString()
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }
}