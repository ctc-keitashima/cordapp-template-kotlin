package com.r3.developers.cordapptemplate.utxoexample.contracts

import com.r3.developers.cordapptemplate.utxoexample.states.ChatState
import net.corda.v5.base.exceptions.CordaRuntimeException
import net.corda.v5.ledger.utxo.Command
import net.corda.v5.ledger.utxo.Contract
import net.corda.v5.ledger.utxo.transaction.UtxoLedgerTransaction

class ChatContract: Contract {

    // エラーメッセージを保持するための内部スコープ定数
    // これにより、テストでそれらを使用できるようになり、文言が更新されたという理由だけでテストを修正する必要がなくなります
    internal companion object {

        const val REQUIRE_SINGLE_COMMAND = "Requires a single command."
        const val UNKNOWN_COMMAND = "Command not allowed."
        const val OUTPUT_STATE_SHOULD_ONLY_HAVE_TWO_PARTICIPANTS = "The output state should have two and only two participants."
        const val TRANSACTION_SHOULD_BE_SIGNED_BY_ALL_PARTICIPANTS = "The transaction should have been signed by both participants."

        const val CREATE_COMMAND_SHOULD_HAVE_NO_INPUT_STATES = "When command is Create there should be no input states."
        const val CREATE_COMMAND_SHOULD_HAVE_ONLY_ONE_OUTPUT_STATE =  "When command is Create there should be one and only one output state."

        const val UPDATE_COMMAND_SHOULD_HAVE_ONLY_ONE_INPUT_STATE = "When command is Update there should be one and only one input state."
        const val UPDATE_COMMAND_SHOULD_HAVE_ONLY_ONE_OUTPUT_STATE = "When command is Update there should be one and only one output state."
        const val UPDATE_COMMAND_ID_SHOULD_NOT_CHANGE = "When command is Update id must not change."
        const val UPDATE_COMMAND_CHATNAME_SHOULD_NOT_CHANGE = "When command is Update chatName must not change."
        const val UPDATE_COMMAND_PARTICIPANTS_SHOULD_NOT_CHANGE = "When command is Update participants must not change."
    }

    // トランザクションが新しいチャットを開始することを示すために使用されるコマンドクラス
    class Create: Command
    // トランザクションが既存のチャットに新しいChatStateを追加することを示すために使用されるコマンドクラス
    class Update: Command

    // verify()関数は、契約ルールをトランザクションに適用するために使用されます
    override fun verify(transaction: UtxoLedgerTransaction) {

        // トランザクションにコマンドが1つしかないことを保証します
        val command = transaction.commands.singleOrNull() ?: throw CordaRuntimeException(REQUIRE_SINGLE_COMMAND)

        // ユニバーサル制約を適用します（コマンドに関係なくすべてのトランザクションに適用されます）
        OUTPUT_STATE_SHOULD_ONLY_HAVE_TWO_PARTICIPANTS using {
            val output = transaction.outputContractStates.first() as ChatState
            output.participants.size== 2
        }

        TRANSACTION_SHOULD_BE_SIGNED_BY_ALL_PARTICIPANTS using {
            val output = transaction.outputContractStates.first() as ChatState
            transaction.signatories.containsAll(output.participants)
        }

        // コマンドに基づいてケースを切り替えます
        when(command) {
            // Createコマンドを持つトランザクションにのみ適用されるルール
            is Create -> {
                CREATE_COMMAND_SHOULD_HAVE_NO_INPUT_STATES using (transaction.inputContractStates.isEmpty())
                CREATE_COMMAND_SHOULD_HAVE_ONLY_ONE_OUTPUT_STATE using (transaction.outputContractStates.size == 1)
            }
            // Updateコマンドを持つトランザクションにのみ適用されるルール
            is Update -> {
                UPDATE_COMMAND_SHOULD_HAVE_ONLY_ONE_INPUT_STATE using (transaction.inputContractStates.size == 1)
                UPDATE_COMMAND_SHOULD_HAVE_ONLY_ONE_OUTPUT_STATE using (transaction.outputContractStates.size == 1)

                val input = transaction.inputContractStates.single() as ChatState
                val output = transaction.outputContractStates.single() as ChatState
                UPDATE_COMMAND_ID_SHOULD_NOT_CHANGE using (input.id == output.id)
                UPDATE_COMMAND_CHATNAME_SHOULD_NOT_CHANGE using (input.chatName == output.chatName)
                UPDATE_COMMAND_PARTICIPANTS_SHOULD_NOT_CHANGE using (
                        input.participants.toSet().intersect(output.participants.toSet()).size == 2)
            }
            else -> {
                throw CordaRuntimeException(UNKNOWN_COMMAND)
            }
        }
    }

    // Corda 4の「「text」using（boolean）」スタイルで制約を記述できるようにするヘルパー関数
    private infix fun String.using(expr: Boolean) {
        if (!expr) throw CordaRuntimeException("Failed requirement: $this")
    }

    // ラムダの最後の式がブール値である「「text」using {lambda}」スタイルで制約を記述できるようにするヘルパー関数
    private infix fun String.using(expr: () -> Boolean) {
        if (!expr.invoke()) throw CordaRuntimeException("Failed requirement: $this")
    }
}