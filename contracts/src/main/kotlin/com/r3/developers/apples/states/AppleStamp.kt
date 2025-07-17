package com.r3.developers.apples.states

import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.BelongsToContract
import com.r3.developers.apples.contracts.AppleStampContract
import java.util.UUID
import java.security.PublicKey

@BelongsToContract(AppleStampContract::class)
public class AppleStamp(
    val id: UUID,
    val stampDesc: String,
    val issuer: PublicKey,
    val holder: PublicKey,
    private val participants: List<PublicKey>
) : ContractState {

    override fun getParticipants(): List<PublicKey> = participants
}
