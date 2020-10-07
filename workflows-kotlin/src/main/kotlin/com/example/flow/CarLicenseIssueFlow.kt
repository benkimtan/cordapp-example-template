package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.review.contract.CarLicenseContract
import com.review.state.CarLicenseState
import net.corda.core.contracts.Command
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

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

object CarLicenseIssueFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val issuer: Party,
                    val licensePlate: String): FlowLogic<SignedTransaction>(){

        /**
         * The progress tracker throws a string for each stage of the progress
         * See the 'progressTracker.currentStep' expressions within the call() function
         */
        companion object {
            object GENERATING_TRANSACTION : Step("Generating transaction based on new Car License.")
            object VERIFYING_TRANSACTION : Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : Step("Signing transaction with our private key.")
            object GATHERING_SIGS : Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION : Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }
            fun tracker() = ProgressTracker(
                    GENERATING_TRANSACTION,
                    VERIFYING_TRANSACTION,
                    SIGNING_TRANSACTION,
                    GATHERING_SIGS,
                    FINALISING_TRANSACTION
            )
        }
        override val progressTracker = tracker()
        /**
         * The flow logic is encapsulated within the call() method.
         */
        @Suspendable
        override fun call(): SignedTransaction {
            //Preparation creating the state object
            val myIdentity = serviceHub.myInfo.legalIdentities.single()
            val newState = CarLicenseState(licensePlate = licensePlate ,issuer = issuer, licensee = myIdentity )

            //Selecting the notary and using the lazy way of selecting the first entry on the list
            val notary = serviceHub.networkMapCache.notaryIdentities.first()

            //Create an unsigned Tx
            progressTracker.currentStep = GENERATING_TRANSACTION
            val issueSigners = newState.participants.map { it.owningKey }
            val issueCommand = Command(CarLicenseContract.Commands.Create(),issueSigners)
            val txBuilder = TransactionBuilder(notary)
                    .addOutputState(newState,CarLicenseContract.ID)
                    .addCommand(issueCommand)

            //Verify that the transaction is valid
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            //Sign the transaction before sending to the issuer where ptx = partially signed Tx
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(txBuilder)

            //Send the transaction to the counterparty where stx = signed Tx
            progressTracker.currentStep = GATHERING_SIGS
            val session = (newState.participants - myIdentity).map { initiateFlow(it) }
            val stx = subFlow(CollectSignaturesFlow(ptx, session, GATHERING_SIGS.childProgressTracker()))

            //After you have received the stx from the counterparty, it is time to send to the notary where ftx = fully signed Tx
            val ftx = subFlow(FinalityFlow(stx, session, FINALISING_TRANSACTION.childProgressTracker()))

            //return the ftx: signedTransaction to the call
            return ftx
        }
    }
    @InitiatedBy(Initiator::class)
    class Acceptor(val otherPartySession: FlowSession): FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val SignedTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // Do nothing and no checks
                }
            }
            val txId =  subFlow(SignedTransactionFlow).id

            return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
        }
    }

}