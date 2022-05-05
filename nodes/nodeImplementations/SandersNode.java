/*
BSD 3-Clause License

Copyright (c) 2007-2013, Distributed Computing Group (DCG)
                         ETH Zurich
                         Switzerland
                         dcg.ethz.ch
              2017-2018, André Brait

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package projects.sanders.nodes.nodeImplementations;

import lombok.Getter;
import lombok.Setter;
import projects.sanders.nodes.messages.*;
import projects.sanders.nodes.timers.CriticalSessionTimer;
import sinalgo.configuration.Configuration;
import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.exception.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.tools.logging.Logging;

import java.awt.*;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Random;

@Getter
@Setter
public class SandersNode extends Node {
    public static final double CRITICAL_SESSION_TIME = 5.0;
    boolean inCs = false;
    boolean waitingCS = false;
    boolean hasVoted = false;
    boolean inquired = false;
    int relinquishCounter = 0;
    int currTs = 0;
    int yesVotes = 0;
    int myTs = 0;
    int candidateTs;
    Node candidate;
    PriorityQueue<Requester> deferredQ;

    Logging myLog = Logging.getLogger("logfile.txt");

    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            Node sender = inbox.getSender();

            if (msg instanceof YesMessage) {
                handleYes(sender);
            } else if (msg instanceof InqMessage) {
                handleInq(sender, (InqMessage) msg);
            } else if (msg instanceof RequestMessage) {
                handleRequest(sender, (RequestMessage) msg);
            } else if (msg instanceof RelinquishMessage) {
                handleRelinquish(sender);
            } else if (msg instanceof ReleaseMessage) {
                handleRelease(sender);
            }
        }
    }

    @Override
    public void preStep() {
        printDeferredQ();

        if (!inCs && tryEnterCS()) {
            enterCS();
        }
    }

    @Override
    public void init() {
        deferredQ = new PriorityQueue<>(5, new RequesterComparator());
    }

    @Override
    public void neighborhoodChange() {
    }

    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        String text;
        Color color;

        if (waitingCS) {
            color = Color.ORANGE;
        } else if (inCs) {
            color = Color.RED;
        } else {
            color = Color.GREEN;
        }

        if (inCs) {
            text = "CS";
            super.drawNodeAsDiskWithText(g, pt, highlight, text, 20, color);
        } else {
            text = "yV: " + yesVotes;
            if (hasVoted) {
                if (inquired) {
                    text = text + " InqV [" + candidate.getID() + "]";
                } else {
                    text = text + " V [" + candidate.getID() + "]";
                }
            } else {
                text = text + " NotV";
            }
            text = text + " Rq: " + relinquishCounter;
            super.drawNodeAsSquareWithText(g, pt, highlight, text, 20, color);
        }
    }

    @Override
    public void postStep() {
        currTs++;
    }

    @Override
    public String toString() {
        return "";
    }

    @Override
    public void checkRequirements() throws WrongConfigurationException {
    }

    private boolean tryEnterCS() {
        // if already waiting for CS, not try to enter again...
        if (waitingCS) {
            return false;
        }

        Random random = new Random();
        double criticalSessionProbability = 0.0;

        try {
            criticalSessionProbability = Configuration.getDoubleParameter("CriticalSessionProbability");
        } catch (CorruptConfigurationEntryException e) {
            e.printStackTrace();
        }

        return random.nextDouble() <= criticalSessionProbability;
    }


    private void enterCS() {
        System.out.println("Node " + this.getID() + " trying to enter in CS");
        waitingCS = true;
        myTs = currTs;
        RequestMessage requestMessage = new RequestMessage(myTs);

        broadcastToCoterie(requestMessage);
    }

    public void exitCS() {
        inCs = false;
        yesVotes = 0;
        ReleaseMessage releaseMessage = new ReleaseMessage();

        broadcastToCoterie(releaseMessage);
    }

    private void broadcastToCoterie(Message msg) {
        if (msg instanceof YesMessage) {
            sendYes(this);
            broadcast(msg);
        } else if (msg instanceof InqMessage) {
            sendInq(this, ((InqMessage) msg).timestamp);
            broadcast(msg);
        } else if (msg instanceof RequestMessage) {
            sendRequest(this, ((RequestMessage) msg).timestamp);
            broadcast(msg);
        } else if (msg instanceof RelinquishMessage) {
            sendRelinquish(this);
            broadcast(msg);
        } else if (msg instanceof ReleaseMessage) {
            sendRelease(this);
            broadcast(msg);
        }
    }

    private void sendYes(Node target) {
        if (targetEqualToSender(target, this)) {
            handleYes(this);
        } else {
            send(new YesMessage(), target);
        }
    }

    private void sendRelinquish(Node target) {
        if (targetEqualToSender(target, this)) {
            handleRelinquish(target);
        } else {
            send(new RelinquishMessage(), target);
        }
    }

    private void sendInq(Node target, int targetTs) {
        InqMessage inqMessage = new InqMessage(targetTs);

        if (targetEqualToSender(target, this)) {
            handleInq(this, inqMessage);
        } else {
            send(inqMessage, target);
        }
    }

    private void sendRequest(Node target, int targetTs) {
        RequestMessage requestMessage = new RequestMessage(targetTs);

        if (targetEqualToSender(target, this)) {
            handleRequest(this, requestMessage);
        } else {
            send(requestMessage, target);
        }
    }

    private void sendRelease(Node target) {
        if (targetEqualToSender(target, this)) {
            handleRelease(target);
        } else {
            send(new ReleaseMessage(), target);
        }
    }

    private void handleYes(Node sender) {
        System.out.println("Node " + this.getID() + " received yes message from node " + sender.getID());
        // coterieSize = outgoing connections + node itself
        int coterieSize = this.getOutgoingConnections().size() + 1;
        yesVotes++;

        // enter to CS if every node from coterie vote yes
        if (yesVotes == coterieSize) {
            inCs = true;
            waitingCS = false;

            // trigger timer to leave critical session
            CriticalSessionTimer timer = new CriticalSessionTimer(this);
            timer.startRelative(CRITICAL_SESSION_TIME, this);
        }
    }

    private void handleInq(Node sender, InqMessage msg) {
        System.out.println("Node " + this.getID() + " received inq message from node " + sender.getID());

        if (waitingCS && myTs == msg.timestamp) {
            sendRelinquish(sender);
            relinquishCounter++;
            yesVotes--;
        }
    }


    private void handleRequest(Node sender, RequestMessage msg) {
        System.out.println("Node " + this.getID() + " received request message from node " + sender.getID());

        int senderTs = msg.timestamp;

        if (!hasVoted) {
            // send vote to sender
            sendYes(sender);
            candidate = sender;
            candidateTs = senderTs;
            hasVoted = true;
        } else {
            // add sender to deferred queue
            deferredQ.add(new Requester(sender, senderTs));

            if ((senderTs < candidateTs || (senderTs == candidateTs && sender.getID() < candidate.getID())) && !inquired) {
                // request vote annulment
                inquired = true;
                sendInq(candidate, candidateTs);
            }
        }
    }

    private void handleRelinquish(Node sender) {
        System.out.println("Node " + this.getID() + " received relinquish message from node " + sender.getID());

        // add candidate to deferred queue
        deferredQ.add(new Requester(candidate, candidateTs));

        // get first requester from deferred queue and use as candidate
        Requester requester = deferredQ.poll();
        sendYes(requester.node);
        candidate = requester.node;
        candidateTs = requester.timestamp;
        inquired = false;
    }

    private void handleRelease(Node sender) {
        System.out.println("Node " + this.getID() + " received release message from node " + sender.getID());

        if (!deferredQ.isEmpty()) {
            // get first requester from deferred queue and use as candidate
            Requester nextRequester = deferredQ.poll();
            sendYes(nextRequester.node);
            candidate = nextRequester.node;
            candidateTs = nextRequester.timestamp;
        } else {
            hasVoted = false;
        }

        inquired = false;
    }

    private void printDeferredQ() {
        PriorityQueue<Requester> PQCopy = new PriorityQueue<>(deferredQ);
        ArrayList<Long> queueToPrint = new ArrayList<>();

        while (!PQCopy.isEmpty()) {
            queueToPrint.add(PQCopy.poll().node.getID());
        }

        System.out.println("Node " + this.getID() + " deferredQ: " + queueToPrint);
    }

    private boolean targetEqualToSender(Node target, Node sender) {
        return target.getID() == sender.getID();
    }
}
