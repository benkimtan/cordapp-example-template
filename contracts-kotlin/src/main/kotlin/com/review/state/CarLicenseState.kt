package com.review.state


import com.review.contract.CarLicenseContract
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party

/**
 * The state object recording CarLicense agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 * @param licensePlate the value of the Car license.
 * @param issuer the authority issuing the car license.
 * @param licensee the party applying and receiving the car license.
 */

@BelongsToContract(CarLicenseContract::class)
data class CarLicenseState(val licensePlate: String,
                           val issuer: Party,
                           val licensee: Party,
                           override val linearId: UniqueIdentifier = UniqueIdentifier()): LinearState {

    override val participants: List<AbstractParty> = listOf(issuer, licensee)

    //Function to replicate object with a change in the licensee x500 only
    fun changeLicensee(newParty: Party) = copy(licensee = newParty)

}