package com.review.contract

import com.review.state.CarLicenseState
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [CarLicenseState].
 *
 * All contracts must sub-class the [Contract] interface.
 */

class CarLicenseContract: Contract {

    companion object {
        @JvmStatic
        val ID = "com.review.contract.CarLicenseContract"
    }

    override fun verify(tx: LedgerTransaction) {

    }

    interface Commands: CommandData{
        //Add your new commands here
    }

}