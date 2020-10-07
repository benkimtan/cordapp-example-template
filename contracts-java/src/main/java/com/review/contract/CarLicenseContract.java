package com.review.contract;

import com.review.state.CarLicenseState;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.CommandWithParties;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.TypeOnlyCommandData;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;


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
public class CarLicenseContract implements Contract{
    public static final String ID = "com.review.contract.CarLicenseContract";


    public interface Commands extends CommandData {
        class Create extends TypeOnlyCommandData implements Commands{}
        class Transfer extends TypeOnlyCommandData implements Commands{}
        class Scrap extends TypeOnlyCommandData implements Commands{}
    }

    /**
     * The verify() function of all the states' contracts must not throw an exception for a transaction to be
     * considered valid.
     */

    @Override
    public void verify(LedgerTransaction tx) {

        // We can use the requireSingleCommand function to extract command data from transaction.
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final Commands commandData = command.getValue();

        if (commandData.equals(new Commands.Create())) {
            requireThat(require-> {

                //Shape constraints
                require.using("Input for license issuance is zero", tx.getInputStates().size()==0);
                require.using("There should only be one output for issuance", tx.getOutputStates().size()==1);

                //Content constraints
                final CarLicenseState output = tx.outputsOfType(CarLicenseState.class).get(0);
                require.using("The issuer and licensee cannot be the same person", output.getIssuer()!=output.getLicensee());

                //Signature constraints
                Set<PublicKey> listOfParticipantPublicKeys = output.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toSet());

                List<PublicKey> arrayOfSigners = command.getSigners();
                Set<PublicKey> setOfSigners = new HashSet<PublicKey>(arrayOfSigners);

                require.using("Only the issuer and licensee needs to sign the contract",
                        setOfSigners.equals(listOfParticipantPublicKeys) && setOfSigners.size() == 2);

                return null;
            });
        }

        if (commandData.equals(new Commands.Transfer())) {
            requireThat(require->{

                //Shape constraints
                require.using("Input for license transfer is one", tx.getInputStates().size()==1);
                require.using("There should only be one output for transfer", tx.getOutputStates().size()==1);

                //Content constraints
                final CarLicenseState input = tx.inputsOfType(CarLicenseState.class).get(0);
                final CarLicenseState output = tx.outputsOfType(CarLicenseState.class).get(0);
                final CarLicenseState checkOutput = output.changeLicensee(input.getLicensee());
                require.using("Only the licensee may change in a transfer",
                        checkOutput.getIssuer().equals(input.getIssuer()) && checkOutput.getLicensee().equals(input.getLicensee()) && checkOutput.getLicensePlate().equals(input.getLicensePlate()) && checkOutput.getLinearId().equals(input.getLinearId())
                        );
                require.using("The output licensee cannot be the issuer", output.getIssuer() != output.getLicensee());

                //Signature constraints
                Set<PublicKey> listOfParticipantPublicKeys = input.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toSet());
                listOfParticipantPublicKeys.add(output.getLicensee().getOwningKey());

                List<PublicKey> arrayOfSigners = command.getSigners();
                Set<PublicKey> setOfSigners = new HashSet<PublicKey>(arrayOfSigners);


                require.using("Only the issuer, old licensee and new licensee needs to sign the contract",
                        setOfSigners.equals(listOfParticipantPublicKeys) && setOfSigners.size() == 3);

                return null;
            });

        }

        if (commandData.equals(new Commands.Scrap())) {
            requireThat(require->{
                //Shape constraints
                require.using("Input for license scraping is one", tx.getInputStates().size()==1);
                require.using("Output for license scraping is zero", tx.getOutputStates().size()==0);

                //Content constraints
                final CarLicenseState input = tx.inputsOfType(CarLicenseState.class).get(0);
                //No requirements in content to scrap the license

                //Signature constraints
                Set<PublicKey> listOfParticipantPublicKeys = input.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toSet());

                List<PublicKey> arrayOfSigners = command.getSigners();
                Set<PublicKey> setOfSigners = new HashSet<PublicKey>(arrayOfSigners);

                require.using("Only the issuer and licensee needs to sign the contract",
                        setOfSigners.equals(listOfParticipantPublicKeys) && setOfSigners.size() == 2);

                return null;
            });
        }
    }
}
