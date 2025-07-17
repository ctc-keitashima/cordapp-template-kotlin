package com.r3.developers.apples.contracts

import com.r3.developers.apples.states.AppleStamp
import com.r3.developers.apples.contracts.AppleCommands
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class AppleStampContract : Contract {
    override fun verify(transaction: UtxoLedgerTransaction) {
        // Extract the command from the transaction
        // Verify the transaction according to the intention of the transaction
        when (val command = transaction.commands.first()) {
            is AppleCommands.Issue -> {
                val outputs = transaction.getOutputStates(AppleStamp::class.java)
                require(outputs.size == 1) {
                    "This transaction should only have one AppleStamp state as output"
                }
                val output = outputs.single()
                require(output.stampDesc.isNotBlank()) {
                    "The output AppleStamp state should have clear description of the type of redeemable goods"
                }
            }
            is AppleCommands.Redeem -> {
                val inputs = transaction.getInputStates(AppleStamp::class.java)
                require(inputs.size == 1) {
                    "This transaction should only have one AppleStamp state as input"
                }
                val input = inputs.single()
                require(input.holder in transaction.signatories) {
                    "The holder of the input AppleStamp state must be a signatory to the transaction"
                }
            }
            else -> {
                // Unrecognised Command type
                throw IllegalArgumentException("Incorrect type of AppleStamp commands: ${command::class.java.name}")
            }
        }
    }
}
