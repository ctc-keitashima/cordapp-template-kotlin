package com.r3.developers.apples.workflows

import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionValidator
import net.corda.v5.base.annotations.Suspendable

@InitiatedBy(protocol = "create-and-issue-apple-stamp")
class CreateAndIssueAppleStampResponderFlow : ResponderFlow {

    @CordaInject
    lateinit var utxoLedgerService: UtxoLedgerService

    @Suspendable
    override fun call(session: FlowSession) {
        // イニシエーターから送信されたトランザクションを受信し、検証、バリデーション、署名、記録を行う
        utxoLedgerService.receiveFinality(session) { _ ->
            /*
             * [receiveFinality] はトランザクションとその署名を自動的に検証し、署名します。
             * しかし、トランザクションが契約上有効であっても、必ずしも署名したいとは限りません。
             * 例えば、相手先と取引したくない場合や、金額が大きすぎる場合、
             * トランザクションの構造に納得できない場合などがあります。
             * [UtxoTransactionValidator]（ここで作成されるラムダ）は、追加のチェックを定義できます。
             * これらの条件が満たされない場合、トランザクションや署名が契約上有効であっても署名しません。
             */
        }
    }
}
