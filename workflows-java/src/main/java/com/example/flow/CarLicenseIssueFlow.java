package com.example.flow;


import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.review.contract.CarLicenseContract;
import com.review.state.CarLicenseState;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;


/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the Car License encapsulated
 * within an [CarLicenseState].
 *
 * In our simple example, the [Acceptor] who is the issuer always accepts a valid Car License.
 *
 * These flows have deliberately been implemented by using only the call() method for ease of understanding.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */
public class CarLicenseIssueFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final Party issuer;
        private final String licensePlate;

        /**
         * The progress tracker throws a string for each stage of the progress
         * See the 'progressTracker.currentStep' expressions within the call() function
         */
        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new Car License.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
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

        public Initiator(Party issuer, String licensePlate) {
            this.issuer = issuer;
            this.licensePlate = licensePlate;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            //Preparation creating the state object
            Party myIdentity = getOurIdentity();
            CarLicenseState newState = new CarLicenseState(licensePlate, issuer, myIdentity, new UniqueIdentifier());

            //Selecting the notary and using the lazy way of selecting the first entry on the list
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            final Command<CarLicenseContract.Commands.Create> issueCommand = new Command<>(
                    new CarLicenseContract.Commands.Create(),
                    ImmutableList.of(newState.getIssuer().getOwningKey(), newState.getLicensee().getOwningKey()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(newState, CarLicenseContract.ID)
                    .addCommand(issueCommand);

            //Verify that the transaction is valid
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            txBuilder.verify(getServiceHub());

            //Sign the transaction before sending to the issuer where ptx = partially signed Tx
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(txBuilder);

            //Send the transaction to the counterparty where stx = signed Tx
            progressTracker.setCurrentStep(GATHERING_SIGS);
            FlowSession otherPartySession = initiateFlow(issuer);
            final SignedTransaction stx = subFlow(
                    new CollectSignaturesFlow(ptx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker())
            );

            //After you have received the stx from the counterparty, it is time to send to the notary where ftx = fully signed Tx
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            final SignedTransaction ftx = subFlow(
                    new FinalityFlow(stx, ImmutableSet.of(otherPartySession), FinalityFlow.Companion.tracker())
            );

            return ftx;
        }

        @InitiatedBy(CarLicenseIssueFlow.Initiator.class)
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
