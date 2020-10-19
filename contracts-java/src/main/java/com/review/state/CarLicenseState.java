package com.review.state;


import com.review.contract.CarLicenseContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import java.util.Arrays;
import java.util.List;


/**
 * The state object recording CarLicense agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 */

@BelongsToContract(CarLicenseContract.class)
public class CarLicenseState implements ContractState {

    public CarLicenseState() {

    }

    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList();
    }

}
