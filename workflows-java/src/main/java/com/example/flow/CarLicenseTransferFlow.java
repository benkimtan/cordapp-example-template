package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import java.util.Arrays;
import java.util.List;

/**
 * This flow allows two parties (the [Initiator] and the [Acceptor]) to come to an agreement about the Car License encapsulated
 * within an [CarLicenseState].
 *
 * In our simple example, the [Acceptor] who is the licensee will always accept the transfer
 *
 * The variables that you need to pass into the flow has been deliberately defined as random, which the developer is expected to define
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */

public class CarLicenseTransferFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {

        private final String random;

        /**
         * The progress tracker throws a string for each stage of the progress
         * See the 'progressTracker.currentStep' expressions within the call() function
         */
        private final Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction based on new Car License.");
        private final Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
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

        public Initiator(String random) {
          this.random = random;
        }

        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            //Preparation creating the state object
            /**
             * This is a mock function to prevent errors. Delete the body of the function before starting development.
             * */
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            final TransactionBuilder builder = new TransactionBuilder(notary);
            final SignedTransaction ptx = getServiceHub().signInitialTransaction(builder);
            final List<FlowSession> sessions = Arrays.asList(initiateFlow(getOurIdentity()));
            return subFlow(new FinalityFlow(ptx, sessions));
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
