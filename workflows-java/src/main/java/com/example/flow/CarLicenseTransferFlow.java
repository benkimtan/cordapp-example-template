package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.review.contract.CarLicenseContract;
import com.review.state.CarLicenseState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static sun.misc.Version.println;

public class CarLicenseTransferFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final UniqueIdentifier LinearId;
        private final Party newLicensee;

        /**
         * The progress tracker throws a string for each stage of the progress
         * See the 'progressTracker.currentStep' expressions within the call() function
         */
        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new Car License.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(UniqueIdentifier LinearId, Party newLicensee) {
            this.LinearId = LinearId;
            this.newLicensee = newLicensee;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            //Preparation creating the state object
            Party myIdentity = getOurIdentity();
            List<UUID> listOfLinearIds = new ArrayList<>();
            listOfLinearIds.add(LinearId.getId());
            QueryCriteria queryCriteria = new QueryCriteria.LinearStateQueryCriteria(null, listOfLinearIds);
            Vault.Page results = getServiceHub().getVaultService().queryBy(CarLicenseState.class, queryCriteria);
            StateAndRef inputStateAndRef = (StateAndRef) results.getStates().get(0);
            CarLicenseState inputState = (CarLicenseState) inputStateAndRef.getState().getData();
            if (!inputState.getIssuer().equals(myIdentity)) {
                throw new IllegalArgumentException("Only Issuer may transfer the Car License ");
            }

            CarLicenseState outputState = inputState.changeLicensee(newLicensee);

            //------BEGIN TEST-----------------
            Integer test = outputState.getParticipants().size();
            System.out.println("What is the size of participants" + test);
            //------END TEST-------------------

            //Selecting the notary and using the lazy way of selecting the first entry on the list
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            final Command<CarLicenseContract.Commands.Transfer> transferCommand = new Command<>(
                    new CarLicenseContract.Commands.Transfer(),
                    ImmutableList.of(inputState.getIssuer().getOwningKey(), inputState.getLicensee().getOwningKey(), newLicensee.getOwningKey()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addInputState(inputStateAndRef)
                    .addOutputState(outputState, CarLicenseContract.ID)
                    .addCommand(transferCommand);

            //Verify that the transaction is valid
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            //Sign the transaction before sending to the issuer where ptx = partially signed Tx
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(txBuilder);

            //Send the transaction to the counterparty where stx = signed Tx
            progressTracker.setCurrentStep(GATHERING_SIGS);
            FlowSession oldLicenseeParty = initiateFlow(inputState.getLicensee());
            FlowSession newLicenseeParty = initiateFlow(newLicensee);
            final SignedTransaction stx = subFlow(
                    new CollectSignaturesFlow(ptx, ImmutableSet.of(oldLicenseeParty,newLicenseeParty), CollectSignaturesFlow.Companion.tracker())
            );

            //After you have received the stx from the counterparty, it is time to send to the notary where ftx = fully signed Tx
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            final SignedTransaction ftx = subFlow(
                    new FinalityFlow(stx, ImmutableSet.of(oldLicenseeParty,newLicenseeParty), FinalityFlow.Companion.tracker())
            );

            return ftx;
        }

        @InitiatedBy(CarLicenseTransferFlow.Initiator.class)
        public static class Acceptor extends FlowLogic<SignedTransaction> {
            private final FlowSession otherPartySession;

            public Acceptor(FlowSession otherPartySession) {
                this.otherPartySession = otherPartySession;
            }

            @Suspendable
            @Override
            public SignedTransaction call() throws FlowException {
                class SignTxFlow extends SignTransactionFlow {
                    private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                        super(otherPartyFlow, progressTracker);
                    }

                    @Override
                    protected void checkTransaction(SignedTransaction stx) {
                        // Do nothing and no checks
                    }
                }
                final SignTxFlow signTxFlow = new SignTxFlow(otherPartySession, SignTransactionFlow.Companion.tracker());
                final SecureHash txId = subFlow(signTxFlow).getId();

                return subFlow(new ReceiveFinalityFlow(otherPartySession, txId));
            }
        }
    }
}
