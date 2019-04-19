package ddejonge.bandana.exampleAgents;

import java.util.*;

import ddejonge.bandana.anac.ANACNegotiator;
import ddejonge.bandana.dbraneTactics.DBraneTactics;
import ddejonge.bandana.dbraneTactics.Plan;
import ddejonge.bandana.negoProtocol.BasicDeal;
import ddejonge.bandana.negoProtocol.DMZ;
import ddejonge.bandana.negoProtocol.DiplomacyNegoClient;
import ddejonge.bandana.negoProtocol.DiplomacyProposal;
import ddejonge.bandana.negoProtocol.OrderCommitment;
import ddejonge.bandana.tools.Utilities;
import ddejonge.negoServer.Message;
import es.csic.iiia.fabregues.dip.board.Power;
import es.csic.iiia.fabregues.dip.orders.Order;


public class NaturalAlliesBot extends ANACNegotiator{


    /**
     * Main method to start the agent
     * @param args
     */

    public static void main(String[] args){

        NaturalAlliesBot myPlayer = new NaturalAlliesBot(args);
        myPlayer.run();

    }

    public String botName;
    protected DBraneTactics dBraneTactics;
    // initialize mapping from each power to its natural allies
    protected static Map<String, List<String>> naturalAllies = new HashMap<>();
    static {
        naturalAllies.put("FRA", new ArrayList<>(Arrays.asList("ENG", "GER", "ITA")));
        naturalAllies.put("RUS", new ArrayList<>(Arrays.asList("AUS", "ENG", "GER", "ITA", "TUR")));
        naturalAllies.put("AUS", new ArrayList<>(Arrays.asList("ITA", "RUS", "TUR")));
        naturalAllies.put("ITA", new ArrayList<>(Arrays.asList("AUS", "FRA", "RUS")));
        naturalAllies.put("TUR", new ArrayList<>(Arrays.asList("AUS", "RUS")));
        naturalAllies.put("ENG", new ArrayList<>(Arrays.asList("FRA", "GER", "RUS")));
        naturalAllies.put("GER", new ArrayList<>(Arrays.asList("ENG", "FRA", "ITA", "RUS")));
    }

    protected boolean isFirstRound = true;
    protected List<Power> currentCoalition = new ArrayList<>();

    //Constructor

    /**
     * Inheriting from ANACNegotiator calling it's constructor
     * and also initiating D-Brane's Tactics module
     * @param args
     */
    public NaturalAlliesBot(String[] args) {
        super(args);

        dBraneTactics = this.getTacticalModule();
        botName = "NaturalAlliesBot";


    }

    /**
     * This method is automatically called at the start of the game, after the 'game' field is set.
     *
     * It is called when the first NOW message is received from the game server.
     * The NOW message contains the current phase and the positions of all the units.
     *
     * You are allowed, but not required, to implement this method
     *
     */
    @Override
    public void start() {

        //You can use the logger to write stuff to the log file.
        //The location of the log file can be set through the command line option -log.
        //it is not necessary to call getLogger().enable() because this is already automatically done by the ANACNegotiator class.

        boolean printLog = true;
        //if set to true the text will be written to file, as well as printed to the standard output stream.
        //If set to false it will only be written to file.

        this.getLogger().logln("game is starting! let's go " + this.botName + "! good luck!", printLog);
        String myPowerName = this.me.getName();
        this.getLogger().logln("" + this.botName + " is playing as " + myPowerName, printLog);

    }

    /**
     * This is where the magic happens!
     * Implementing the negotiations protocol!
     *
     * @param negotiationDeadline - maximum time allowed for negotiation procedure
     */

    @Override
    public void negotiate(long negotiationDeadline) {
        boolean startOfCurrentNegotiation = true;

        // This loop repeats 2 steps
        // During the first step we try to find deals to propose to the other negotiators.
        // During the second step we handle any incoming messages.
        while (System.currentTimeMillis() < negotiationDeadline) {

            // STEP 1:  try to find a proposal to make, and if we do find one, propose it.
            List<BasicDeal> dealsToPropose;

            // search for deals only one time per negotiation round
            if (startOfCurrentNegotiation){
                dealsToPropose = this.getDealsToOffer();

                // if deals were found - propose them
                if (dealsToPropose != null){
                    for (BasicDeal deal: dealsToPropose){
                        this.proposeDeal(deal);
                    }
                    startOfCurrentNegotiation = false;
                }
            }


            // STEP 2: Handle incoming messages

            //See if we have received any message from any of the other negotiators.
            // e.g. a new proposal or an acceptance of a proposal made earlier.
            while(hasMessage()) {

                //if yes, remove it from the message queue.
                Message receivedMessage = removeMessageFromQueue();

                // extract message properties and log the event
                DiplomacyProposal messageContent = (DiplomacyProposal) receivedMessage.getContent();
                String messageSender = receivedMessage.getSender();
                String messageType = receivedMessage.getPerformative();

                this.getLogger().logln("got message " + messageContent + " from " + messageSender, true);

                switch (messageType){
                    case DiplomacyNegoClient.ACCEPT:

                        // log received acceptance and content
                        this.getLogger().logln("" + this.botName + ".negotiate() Received acceptance from " + messageSender + ": " + messageContent, true);

                        // add sender of message to coalition
                        // add to coalition only in first round
                        if (this.isFirstRound){
                            this.addAcceptedProposalMemberToCoalition(messageSender);
                        }
                        break;

                    case DiplomacyNegoClient.PROPOSE:
                        BasicDeal deal = (BasicDeal) messageContent.getProposedDeal();
                        // log proposal
                        this.getLogger().logln("" + this.botName + ".negotiate() Received proposal: " + messageContent, true);

                        // check if deal is not outdated and consistent with previous deals
                        if (this.checkProposedDealIsConsistentAndNotOutDated(deal)){

                            // for now we just accept any valid deals, so we will accept the deal
                            this.acceptProposal(messageContent.getId());
                        }
                        break;

                    case DiplomacyNegoClient.CONFIRM:
                        // we pretty much do nothing here
                        // just log the confirmation
                        this.getLogger().logln( "" + this.botName + ".negotiate() Received confirmed from " + messageSender + ": " + messageContent, true);
                        break;

                    case DiplomacyNegoClient.REJECT:
                        // we don't do nothing here, not really interesting
                        break;

                    default:
                        // received a message of unhandled type - just log the event
                        this.getLogger().logln("" + this.botName + ".negotiate() Received a message of unhandled type: " + receivedMessage.getPerformative() + ". Message content: " + messageContent.toString(), true);

                }

            }

            // Sleep for 250 milliseconds - let other negotiators time to propose deals
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {;}
        }

        this.getLogger().logln("" + this.botName + ".negotiate(): end of a negotiation round", true);

        // at the end of while loop
        // if it was the first round - update that it is over
        if (this.isFirstRound){
            this.getLogger().logln("" + this.botName + ".negotiate(): end of FIRST negotiation round", true);
            this.isFirstRound = false;
        }
    }

    /**
     * Each round, after each power has submitted its orders, this method is called several times:
     * once for each order submitted by any other power.
     *
     *
     */
    @Override
    public void receivedOrder(Order arg0) {
        // TODO Auto-generated method stub

    }

    protected ArrayList<BasicDeal> getFirstRoundDealsOffer(){

        this.getLogger().logln("" + this.botName + ": First round deals offers. Offering DMZ areas to Natural Allies", true);

        // initiate empty list of deals to be filled later
        ArrayList<BasicDeal> dealsToOffer = new ArrayList<>();

        // get names of all my natural allies
        List<String> myNaturalAlliesNames = NaturalAlliesBot.naturalAllies.get(this.me.getName());

        this.getLogger().logln("" + this.botName + ": First round deals offers. My power is: " + this.me.getName() + " " +
                "My natural allies are: " + Arrays.toString(myNaturalAlliesNames.toArray()), true);

        // offer to my natural allies a deal that includes joint DMZs
        List<Power> allPowers = this.game.getPowers();
        List<DMZ> demilitarizedZones = new ArrayList<>();

        for(Power power: allPowers) {
            String powerName = power.getName();

            this.getLogger().logln("" + this.botName + ": First round deals offers. Offering DMZ to: " + powerName, true);

            if (myNaturalAlliesNames.contains(powerName)) { // only offer to natural allies
                List<Power> currentAllyForDeal = new ArrayList<>(Arrays.asList(power));
                demilitarizedZones.add(new DMZ(game.getYear(), game.getPhase(), currentAllyForDeal, me.getOwnedSCs()));

                List<Power> myPowerForDeal = new ArrayList<>(Arrays.asList(this.me));
                demilitarizedZones.add(new DMZ(game.getYear(), game.getPhase(), myPowerForDeal, power.getOwnedSCs()));

                List<OrderCommitment> emptyOrderCommitments = new ArrayList<>();
                BasicDeal deal = new BasicDeal(emptyOrderCommitments, demilitarizedZones);
                dealsToOffer.add(deal);
            }
        }
        return dealsToOffer;
    }

    protected ArrayList<BasicDeal> getCoalitionDmzDealsOffer(List<Power> aliveCoalitionMembers){

        this.getLogger().logln("" + this.botName + ": Advanced round deals offers. Offering DMZ areas to coalition members", true);
        // initiate empty list of deals to be filled later
        ArrayList<BasicDeal> dmzDealsToOffer = new ArrayList<>();

        // make offers for all the coalition members to not attack each other
        List<DMZ> demilitarizedZones = new ArrayList<>();


        for (Power currentMember: aliveCoalitionMembers) {

            this.getLogger().logln("" + this.botName + ": Advanced round deals offers. Offering DMZ areas to the member: " + currentMember.getName(), true);

            // create a list of all other coalition members to participate in the deal
            ArrayList<Power> allOtherMembers = new ArrayList<>();

            // include our power in the list
            allOtherMembers.add(me);
            for (Power potentialMember: aliveCoalitionMembers) {

                // We want to make an offer including all the other coalition members except current one
                if (potentialMember != currentMember) {
                    allOtherMembers.add(potentialMember);
                }
            }

            // create a DMZ deal containing all alive member committing not to invade the current member SCs
            demilitarizedZones.add(new DMZ(game.getYear(), game.getPhase(), allOtherMembers, currentMember.getOwnedSCs()));
            List<OrderCommitment> emptyOrderCommitments = new ArrayList<>();
            BasicDeal deal = new BasicDeal(emptyOrderCommitments, demilitarizedZones);
            dmzDealsToOffer.add(deal);
        }



        return dmzDealsToOffer;
    }

    protected List<BasicDeal> getDealsToOffer() {
        ArrayList<BasicDeal> dealsToOffer;

        if (this.isFirstRound){ // offer all natural allies and create a coalition
            dealsToOffer = this.getFirstRoundDealsOffer();
        }

        else{
            // offer only to current coalition members
            List<Power> aliveCoalitionMembers = this.getAlivePowers(true);
            dealsToOffer = this.getCoalitionDmzDealsOffer(aliveCoalitionMembers);
        }

        return dealsToOffer;

    }

    protected List<Power> getAlivePowers(boolean onlyCoalitionMembers){
        //Get the names of all the powers that are connected to the negotiation server and which have not been eliminated.
        List<Power> aliveNegotiatingPowers = this.getNegotiatingPowers();
        if (onlyCoalitionMembers){
            List<Power> aliveCoalitionMembers = new ArrayList<>();
            for (Power alivePower: aliveNegotiatingPowers){
                if (this.currentCoalition.contains(alivePower)){
                    aliveCoalitionMembers.add(alivePower);
                }
            }
            return aliveCoalitionMembers;
        }
        else {
            return aliveNegotiatingPowers;
        }
    }

    protected void addAcceptedProposalMemberToCoalition(String messageSender){
        Power senderPower = this.game.getPower(messageSender);

        // because we have a list of coalition member and not a set we will check if the power exists
        if (!this.currentCoalition.contains(senderPower)){
            this.getLogger().logln("" + this.botName + ": Adding member to coalition: " + senderPower.getName(), true);
            this.currentCoalition.add(senderPower);
        }
    }

    protected boolean checkProposedDealIsConsistentAndNotOutDated(BasicDeal proposedDeal) {
        // check DMZ offers are not outdated
        for (DMZ dmz : proposedDeal.getDemilitarizedZones()) {

            // Sometimes we may receive messages too late, so we check if the proposal does not
            // refer to some round of the game that has already passed.
            // one offer is enough to eliminate the entire deal
            if (isHistory(dmz.getPhase(), dmz.getYear())) {
                return false;
            }
        }

        // check order commitments
        for(OrderCommitment orderCommitment : proposedDeal.getOrderCommitments()){

            // Sometimes we may receive messages too late, so we check if the proposal does not
            // refer to some round of the game that has already passed.
            // one offer is enough to eliminate the entire deal
            if(isHistory(orderCommitment.getPhase(), orderCommitment.getYear())){
                return false;
            }
        }

        // check offer is consistent with previous offers
        String consistencyReport;
        List<BasicDeal> commitments = new ArrayList<>();
        commitments.addAll(this.getConfirmedDeals());
        commitments.add(proposedDeal);
        consistencyReport = Utilities.testConsistency(game, commitments);

        // if we got up until here we know the offer is not outdated
        // so if it is consistent we say it is valid
        // the consistency report returns null for consistent deals

        if (consistencyReport != null){
            return false;
        }

        return true;
    }
}