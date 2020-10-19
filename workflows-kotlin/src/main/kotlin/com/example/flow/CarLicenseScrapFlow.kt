package com.example.flow

import co.paralleluniverse.fibers.Suspendable
import com.review.contract.CarLicenseContract
import com.review.state.CarLicenseState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.ProgressTracker.Step

/**
 * This flow allows three parties (the [Initiator] and the [Acceptor]) to come to an agreement about scraping the Car License encapsulated
 * within an [CarLicenseState]. Only the Issuer can scrap the Car License.
 *
 * In our simple example, the [Acceptor] always accepts a valid Car License and its contents.
 *
 * The variables that you need to pass into the flow has been deliberately defined as random, which the developer is expected to define.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */

object CarLicenseScrapFlow{
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val random: String) : FlowLogic<SignedTransaction>() {
        /**
         * The progress tracker throws a string for each stage of the progress
         * See the 'progressTracker.currentStep' expressions within the call() function
         * NB: You can choose to use or not use these in your code
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

        @Suspendable
        override fun call(): SignedTransaction {
            return serviceHub.signInitialTransaction(
                    TransactionBuilder(notary = null)
            )
        }

        @InitiatedBy(Initiator::class)
        class Acceptor(val otherPartySession: FlowSession): FlowLogic<SignedTransaction>() {
            @Suspendable
            override fun call(): SignedTransaction {
                val SignedTransactionFlow = object : SignTransactionFlow(otherPartySession) {
                    override fun checkTransaction(stx: SignedTransaction) {
                        //Do nothing and no checks
                    }
                }
                val txId = subFlow(SignedTransactionFlow).id

                return subFlow(ReceiveFinalityFlow(otherPartySession, expectedTxId = txId))
            }
        }
    }
}