package com.review.contract

import com.review.state.CarLicenseState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.requireThat
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [CarLicenseState].
 *
 * For a new [CarLicenseState] to be issued onto the ledger, a transaction is required which takes:
 * - Zero input states.
 * - One output state: the new [CarLicenseState].
 * - An Create() command with the public keys of both the issuer and the licensee.
 *
 * For a car license to be transferred, a transaction is required which takes:
 * - One input CarlicenseState
 * - One output CarLicenseState
 * - A Transfer() command with the public keys of all three parties including the issuer of the license
 *
 * For a car license to be scraped, a transaction is required which takes:
 * - One input CarLicenseState
 * - Zero output state
 * - A Scrap() command with the public keys of both the issuer and the licensee
 *
 * All contracts must sub-class the [Contract] interface.
 */

class CarLicenseContract: Contract {

    companion object {
        @JvmStatic
        val ID = "com.review.contract.CarLicenseContract"
    }

    override fun verify(tx: LedgerTransaction) {
        val txCommand = tx.commands.requireSingleCommand<Commands>()
        when(txCommand.value){
            is Commands.Create-> requireThat{
                //Shape constraints
                "Input for license issuance is zero" using (tx.inputStates.isEmpty())
                "There should only be one output for issuance" using (tx.outputStates.size == 1)

                //Content constraints
                val output = tx.outputStates.single() as CarLicenseState
                "The issuer and licensee cannot be the same person" using (output.issuer != output.licensee)

                //Signature constraints
                "Only the issuer and licensee needs to sign the contract" using (
                        output.participants.map { it.owningKey }.toSet() == txCommand.signers.toSet()
                        )
            }
            is Commands.Transfer-> requireThat {
                //Shape constraint
                "Input for license transfer is one" using (tx.inputStates.size == 1)
                "Output for license transfer is one" using (tx.outputStates.size == 1)

                //Content constraints
                val input = tx.inputStates.single() as CarLicenseState
                val output = tx.outputStates.single() as CarLicenseState
                "Only the licensee may change in a transfer" using (input == output.changeLicensee(input.licensee))
                "The output licensee cannot be the issuer" using (output.issuer != output.licensee)

                //Signature constraints
                "Only the issuer, old licensee and new licensee needs to sign the contract" using (
                        input.participants.union(output.participants).map { it.owningKey }.toSet() == txCommand.signers.toSet()
                        )
            }
            is Commands.Scrap-> requireThat {
                //Shape constraint
                "Input for license scraping is one" using (tx.inputStates.size == 1)
                "Output for license scraping is zero" using (tx.outputStates.isEmpty())

                //Content constraints
                val input = tx.inputStates.single() as CarLicenseState
                //No requirements in content to scrap the license

                //Signature constraints
                "Only the issuer and licensee needs to sign the contract" using (
                        input.participants.map { it.owningKey }.toSet() == txCommand.signers.toSet()
                        )
            }

        }
    }

    interface Commands: CommandData{
        class Create(): Commands
        class Transfer(): Commands
        class Scrap(): Commands
    }

}