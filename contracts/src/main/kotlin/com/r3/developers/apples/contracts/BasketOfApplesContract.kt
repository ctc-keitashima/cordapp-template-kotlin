package com.r3.developers.apples.contracts

import com.r3.developers.apples.states.AppleStamp
import com.r3.developers.apples.states.BasketOfApples
import com.r3.developers.apples.contracts.AppleCommands
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

import net.corda.v5.ledger.utxo.Command

class BasketOfApplesContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
        when (val command = transaction.commands.first()) {
            is AppleCommands.PackBasket -> {
                val outputs = transaction.getOutputStates(BasketOfApples::class.java)
                require(outputs.size == 1) {
                    "This transaction should only have one BasketOfApples state as output"
                }
                val output = transaction.getOutputStates(BasketOfApples::class.java).first()
                require(output.description.isNotBlank()) {
                    "The output AppleStamp state should have clear description of the type of redeemable goods"
                }
                require(output.weight > 0) {
                    "The output AppleStamp state should have non zero weight"
                }
            }
            is AppleCommands.Redeem -> {
                require(transaction.inputContractStates.size == 2) {
                    "This transaction should consume two states"
                }
                
                // Retrieve the inputs to this transaction, which should be exactly one AppleStamp
                // and one BasketOfApples
                val stampInputs = transaction.getInputStates(AppleStamp::class.java)
                val basketInputs = transaction.getInputStates(BasketOfApples::class.java)
                require(stampInputs.isNotEmpty() && basketInputs.isNotEmpty()) {
                    "This transaction should have exactly one AppleStamp and one BasketOfApples input state"
                }

                val stampInput = stampInputs.single()
                val basketInput = basketInputs.single()
                require(stampInput.issuer == basketInput.farm) {
                    "The issuer of the Apple stamp should be the producing farm of this basket of apple"
                }
                require(basketInput.weight > 0) {
                    "The basket of apple has to weigh more than 0"
                }
            }
            else -> throw IllegalArgumentException("Unknown command: $command")

        }
    }
}