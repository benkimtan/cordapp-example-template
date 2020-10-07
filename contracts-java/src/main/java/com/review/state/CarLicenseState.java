package com.review.state;


import com.review.contract.CarLicenseContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.util.Arrays;
import java.util.List;


/**
 * The state object recording CarLicense agreements between two parties.
 *
 * A state must implement [ContractState] or one of its descendants.
 *
 */

@BelongsToContract(CarLicenseContract.class)
public class CarLicenseState implements LinearState {
    private final String licensePlate;
    private final Party issuer;
    private final Party licensee;
    private final UniqueIdentifier linearId;

    /**
     * @param licensePlate the value of the Car license.
     * @param issuer the authority issuing the car license.
     * @param licensee the party applying and receiving the car license.
     */

    public CarLicenseState(String licensePlate,
                           Party issuer,
                           Party licensee,
                           UniqueIdentifier linearId) {
        this.licensePlate = licensePlate;
        this.issuer = issuer;
        this.licensee = licensee;
        this.linearId = linearId;
    }

    public String getLicensePlate() {return licensePlate; }
    public Party getIssuer() {return  issuer;}
    public Party getLicensee() {return  licensee;}

    @Override public UniqueIdentifier getLinearId() {return linearId;}
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(issuer, licensee);
    }

    //Function to replicate object with a change in the licensee x500 only
    public CarLicenseState copy(String licensePlate, Party issuer, Party licensee) {
        return new CarLicenseState(licensePlate, issuer, licensee, this.getLinearId());
    }

    public CarLicenseState changeLicensee(Party newParty) {
        return new CarLicenseState(licensePlate, issuer, newParty, linearId);
    }
}
