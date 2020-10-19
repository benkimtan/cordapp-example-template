package com.review.contract;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;


/**
 * A implementation of a basic smart contract in Corda.
 *
 * This contract enforces rules regarding the creation of a valid [CarLicenseState].
 *
 * All contracts must sub-class the [Contract] interface.
 */
public class CarLicenseContract implements Contract{
    public static final String ID = "com.review.contract.CarLicenseContract";


    public interface Commands extends CommandData {

    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */

    @Override
    public void verify(LedgerTransaction tx) {

    }
}
