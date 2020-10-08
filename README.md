<p align="center">
  <img src="https://www.corda.net/wp-content/uploads/2016/11/fg005_corda_b.png" alt="Corda" width="500">
</p>

# Review CorDapp for test

Welcome to the review CorDapp which we are going to use as a test at the end of your Corda foundational training.

This example application has modules for both Java and Kotlin and would compile if you just want to attempt using one of the languages

The Corda distributed application (CorDapp) you are going to create is to get a new Car License plate number issued by an Issuer.

As party of the test, you should design your application around the following:

State

CarLicenseState - to define your data model of what needs to go into the Car License state object

Contract

Your contract code to cater for 3 types of flows in this use-case i.e. CarLicenseIssueFlow, CarLicenseTransferFlow and CarLicenseScrapFlow

Flow

CarLicenseIssueFlow - Create a new CarLicenseState between the issuer and the licensee 

CarLicenseTransferFlow - Transfer the CarLicenseState from the old licensee to the new licensee while notifying the issuer

CarLicenseScrapFlow - At the end of the life cycle, the Car License has to be scrapped which in the context of corda is to expire the state

Expectations

As the application is to test on your understanding of how the framework of Corda can be applied to a simple use-case.
The participant is advised not to over complicate the design of the implementation and whenever applicable just put in your assumptions 