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
 * These flows have deliberately been implemented by using only the call() method for ease of understanding.
 *
 * All methods called within the [FlowLogic] sub-class need to be annotated with the @Suspendable annotation.
 */

object CarLicenseScrapFlow{
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val LinearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
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

        @Suspendable
        override fun call(): SignedTransaction {
            //Preparing the CarLicenseState input and note that there is no output in Expiry
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(LinearId))
            val inputStateAndRef = serviceHub.vaultService.queryBy<CarLicenseState>(queryCriteria).states.single()

            //Restrict the flow to be run by the Issuer only
            val myIdentity = serviceHub.myInfo.legalIdentities.single()
            if (inputStateAndRef.state.data.issuer != myIdentity) {
                throw IllegalArgumentException("Only the issuer of the Car License can scrap")
            }

            //Create an unsigned Tx
            progressTracker.currentStep = GENERATING_TRANSACTION
            val notary = inputStateAndRef.state.notary
            val scrapSigners = (inputStateAndRef.state.data.participants).map { it.owningKey }
            val scrapCommand = Command(CarLicenseContract.Commands.Scrap(), scrapSigners)
            val txBuilder = TransactionBuilder(notary = notary)
                    .addInputState(inputStateAndRef)
                    .addCommand(scrapCommand)

            //Verify that the transaction is valid
            progressTracker.currentStep = VERIFYING_TRANSACTION
            txBuilder.verify(serviceHub)

            //Sign the transaction before sending to the issuer where ptx = partially signed Tx
            progressTracker.currentStep = SIGNING_TRANSACTION
            val ptx = serviceHub.signInitialTransaction(txBuilder)

            //Send the transaction to the counterparty where stx = signed Tx
            progressTracker.currentStep = GATHERING_SIGS
            val session = (inputStateAndRef.state.data.participants - myIdentity).map { initiateFlow(it) }
            val stx = subFlow(CollectSignaturesFlow(ptx, session, GATHERING_SIGS.childProgressTracker()))

            //After you have received the stx from the counterparty, it is time to send to the notary where ftx = fully signed Tx
            progressTracker.currentStep = FINALISING_TRANSACTION
            val ftx = subFlow(FinalityFlow(stx, session, FINALISING_TRANSACTION.childProgressTracker()))

            return ftx
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