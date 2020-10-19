package com.review.state


import com.review.contract.CarLicenseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty

/**
 * The state object recording CarLicense agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 */

@BelongsToContract(CarLicenseContract::class)
data class CarLicenseState(val data: String = "data"): ContractState {

    override val participants: List<AbstractParty> = listOf()

}