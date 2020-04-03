/*****************************************************************
JADE - Java Agent DEvelopment Framework is a framework to develop 
multi-agent systems in compliance with the FIPA specifications.
Copyright (C) 2000 CSELT S.p.A. 

GNU Lesser General Public License

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, 
version 2.1 of the License. 

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the
Free Software Foundation, Inc., 59 Temple Place - Suite 330,
Boston, MA  02111-1307, USA.
 *****************************************************************/

package original;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;

public class BookBuyerAgent extends Agent {
	// The title of the book to buy
	private String targetBookTitle;
	// The list of known seller agents
	private AID[] sellerAgents;

	private book history[] = new book[5];

	private int count = 0;
	
	private int relife = 3; // veces que puede esperar el agente a tener un vendedor

	// Put agent initializations here
	protected void setup() {
		// Printout a welcome message
		System.out.println("Hallo! Buyer-agent " + getAID().getName() + " is ready.");

		// Get the title of the book to buy as a start-up argument
		Object[] args = getArguments();
		if (args != null && args.length > 0) {
			targetBookTitle = (String) args[0];
		//	System.out.println("Target book is " + targetBookTitle);

			// Add a TickerBehaviour that schedules a request to seller agents every minute
			addBehaviour(new book_history());
		} else {
			// Make the agent terminate
			System.out.println("No target book title specified");
			doDelete();
		}
	}

	// Put agent clean-up operations here
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Buyer-agent " + getAID().getName() + " terminating.");
	}

	private class book_history extends OneShotBehaviour {

		Object[] args = getArguments();

		@Override
		public void action() {

			System.out.println("\n \nThe buyer  "+getAID().getName()+"  has the following books: ");
			for (int i = 1; i < args.length; i++) {
				history[count] = new book((String) args[i], 0,1);
				if (history[count] != null)
					System.out.println(history[count].getName());
				count++;

			}
			System.out.println("\n\n=====================================================");
			System.out.println("\n The buyer wants the book whose title is: " + targetBookTitle + "\n");
			System.out.println("=====================================================");

			if (search()) {

				System.out.println("\n The buyer does not have the book, therefore he wants to buy it \n \n \n ");
				myAgent.addBehaviour(new TickerBehaviour(myAgent, 20000) {

					protected void onTick() {
						// System.out.println("Trying to buy " + targetBookTitle);
						// Update the list of seller agents
						DFAgentDescription template = new DFAgentDescription();
						ServiceDescription sd = new ServiceDescription();
						sd.setType("book-selling");
						template.addServices(sd);
						try {

							DFAgentDescription[] result = DFService.search(myAgent, template);
							
							sellerAgents = new AID[result.length];
							// comprobamos que al menos exista 2 vendendores para poder comparar
							if (sellerAgents.length >= 2) {
								System.out.println("Found the following seller agents:");
								for (int i = 0; i < result.length; ++i) {
									sellerAgents[i] = result[i].getName();
									System.out.println(sellerAgents[i].getName());
								}

							} else {

								if (relife > 0) {
									System.out.println(
											"**********************************************************************");
									System.out.println(
											"\n There is no minimum number of sellers... new check in 30 seconds \n ");
									System.out.println(
											"**********************************************************************");
									relife--;
									reset(30000);

								} else {

									System.out.println(
											"=======================================================================================================");
									System.out.println(getAID().getName()
											+ " I have tried 3 times and I have not been successful therefore I am leaving");
									System.out.println(
											"=======================================================================================================");
									doDelete();
								}

							}
						} catch (FIPAException fe) {
							fe.printStackTrace();
						}

						// Perform the request
						myAgent.addBehaviour(new RequestPerformer());

					}
				});

			} else
				System.out.println("The buyer already has the book so he does not want to buy");

		}

		public boolean search() {

			for (int i = 0; i < history.length; i++) {

				if (history[i] != null && history[i].getName().equals(targetBookTitle)) {

					return false;

				}
			}

			return true;
		}

	}

	/**
	 * Inner class RequestPerformer. This is the behaviour used by Book-buyer agents
	 * to request seller agents the target book.
	 */
	private class RequestPerformer extends Behaviour {
		private AID bestSeller; // The agent who provides the best offer
		private int bestPrice; // The best offered price
		private int repliesCnt = 0; // The counter of replies from seller agents
		private MessageTemplate mt; // The template to receive replies
		private int step = 0;

		public void action() {
			switch (step) {
			case 0:
				// Send the cfp to all sellers
				ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
				for (int i = 0; i < sellerAgents.length; ++i) {
					cfp.addReceiver(sellerAgents[i]);
				}
				cfp.setContent(targetBookTitle);
				cfp.setConversationId("book-trade");
				cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique value
				myAgent.send(cfp);
				// Prepare the template to get proposals
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			case 1:
				// Receive all proposals/refusals from seller agents
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Reply received
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
						// This is an offer
						int price = Integer.parseInt(reply.getContent());
						if (bestSeller == null || price < bestPrice) {
							// This is the best offer at present
							bestPrice = price;
							bestSeller = reply.getSender();
						}
					}
					repliesCnt++;
					if (repliesCnt >= sellerAgents.length) {
						// We received all replies
						step = 2;
					}
				} else {
					block();
				}
				break;
			case 2:
				// Send the purchase order to the seller that provided the best offer
				ACLMessage order = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
				order.addReceiver(bestSeller);
				order.setContent(targetBookTitle);
				order.setConversationId("book-trade");
				order.setReplyWith("order" + System.currentTimeMillis());
				myAgent.send(order);
				// Prepare the template to get the purchase order reply
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("book-trade"),
						MessageTemplate.MatchInReplyTo(order.getReplyWith()));
				step = 3;
				break;
			case 3:
				// Receive the purchase order reply
				reply = myAgent.receive(mt);
				if (reply != null) {
					// Purchase order reply received
					if (reply.getPerformative() == ACLMessage.INFORM) {
						// Purchase successful. We can terminate
						System.out.println(
								targetBookTitle + " successfully purchased from agent " + reply.getSender().getName());
						System.out.println("Price = " + bestPrice);
						myAgent.doDelete();
					} else {
						System.out.println("Attempt failed: requested book already sold.");
					}

					step = 4;
				} else {
					block();
				}
				break;
			}
		}

		public boolean done() {
			if (step == 2 && bestSeller == null) {
				System.out.println("Attempt failed: " + targetBookTitle + " not available for sale");
			}
			return ((step == 2 && bestSeller == null) || step == 4);
		}
	} // End of inner class RequestPerformer
}
