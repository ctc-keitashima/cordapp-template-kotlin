package com.r3.developers.apples.workflows

import net.corda.v5.application.flows.*
import com.r3.developers.apples.contracts.AppleCommands
import com.r3.developers.apples.states.AppleStamp
import com.r3.developers.apples.states.BasketOfApples
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowMessaging
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.types.MemberX500Name
import net.corda.v5.ledger.common.NotaryLookup
import net.corda.v5.ledger.utxo.UtxoLedgerService
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class RedeemApplesFlowRequest(
    val buyer: MemberX500Name, // 購入者（受取人）
    val stampId: UUID, // リンゴ引換券のID
)

/*
    AppleStamp（リンゴ引換券） を受け取り、BasketOfAppes（リンゴ）を差し出す
    ⇒ リンゴ引換券とリンゴの交換
*/
@InitiatingFlow(protocol = "redeem-apples")
class RedeemApplesFlow : ClientStartableFlow {
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
        val request =
            requestBody.getRequestBodyAs(
                jsonMarshallingService,
                RedeemApplesFlowRequest::class.java,
            )
        val buyerName = request.buyer
        val stampId = request.stampId

        // Retrieve the notaries public key (this will change)
        val notaryInfo = notaryLookup.notaryServices.single()

        val myKey = memberLookup.myInfo().let { it.ledgerKeys.first() }

        val buyer =
            memberLookup
                .lookup(buyerName)
                ?.let { it.ledgerKeys.first() }
                ?: throw IllegalArgumentException("The buyer does not exist within the network")

        val appleStampStateAndRef =
            utxoLedgerService
                .findUnconsumedStatesByExactType(
                    AppleStamp::class.java,
                    100, // 制限数
                    Instant.now(),
                ).results
                .firstOrNull { it.state.contractState.id == stampId }
                ?: throw IllegalArgumentException("引換券ID $stampId に一致するリンゴ引換券が見つかりません")

        val basketOfApplesStampStateAndRef =
            utxoLedgerService
                .findUnconsumedStatesByExactType(
                    BasketOfApples::class.java,
                    100, // 制限数
                    Instant.now(),
                ).results
                .firstOrNull { basketStateAndRef ->
                    basketStateAndRef.state.contractState.owner == appleStampStateAndRef.state.contractState.issuer
                }
                ?: throw IllegalArgumentException("対象となるリンゴの籠が見つかりません")

        val originalBasketOfApples = basketOfApplesStampStateAndRef.state.contractState

        val updatedBasket = originalBasketOfApples.changeOwner(buyer)

        // Create the transaction
        val transaction =
            utxoLedgerService
                .createTransactionBuilder()
                .setNotary(notaryInfo.name)
                .addInputStates(appleStampStateAndRef.ref, basketOfApplesStampStateAndRef.ref)
                .addOutputState(updatedBasket)
                .addCommand(AppleCommands.Redeem())
                .setTimeWindowUntil(Instant.now().plus(1, ChronoUnit.DAYS))
                .addSignatories(listOf(myKey, buyer))
                .toSignedTransaction()

        val session = flowMessaging.initiateFlow(buyerName)

        return try {
            // Send the transaction and state to the counterparty and let them sign it
            // Then notarise and record the transaction in both parties' vaults.
            utxoLedgerService.finalize(transaction, listOf(session)).toString()
        } catch (e: Exception) {
            "Flow failed, message: ${e.message}"
        }
    }
}
