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
import projects.defaultProject.nodes.timers.MessageTimer;
import projects.sanders.nodes.messages.*;
import projects.sanders.nodes.timers.DelayTimer;
import sinalgo.configuration.Configuration;
import sinalgo.exception.CorruptConfigurationEntryException;
import sinalgo.exception.SinalgoFatalException;
import sinalgo.exception.WrongConfigurationException;
import sinalgo.gui.transformation.PositionTransformation;
import sinalgo.nodes.Node;
import sinalgo.nodes.edges.Edge;
import sinalgo.nodes.messages.Inbox;
import sinalgo.nodes.messages.Message;
import sinalgo.tools.Tools;
import sinalgo.tools.logging.Logging;

import java.awt.*;
import java.util.PriorityQueue;

@Getter
@Setter
public class SandersNode extends Node {
    boolean inCs = false;
    boolean hasVoted = false;
    boolean inquired = false;
    int currTs = 0;
    int yesVotes = 0;
    int myTs;
    int candidateTs;
    Node candidate;
    PriorityQueue<Requester> deferredQ;

    Logging log = Logging.getLogger("sanders_log");

    @Override
    public void handleMessages(Inbox inbox) {
        while (inbox.hasNext()) {
            Message msg = inbox.next();
            Node sender = inbox.getSender();

            if (msg instanceof YesMessage) {
                handleYes();
            } else if (msg instanceof InqMessage) {
                handleInq(sender, (InqMessage) msg);
            } else if (msg instanceof RequestMessage) {
                handleRequest(sender, (RequestMessage) msg);
            } else if (msg instanceof RelinquishMessage) {
                handleRelinquish();
            } else if (msg instanceof ReleaseMessage) {
                handleRelease();
            }
        }
    }

    @Override
    public void preStep() {

    }

    @Override
    public void init() {
        deferredQ = new PriorityQueue<Requester>(5, new RequesterComparator());
    }

    @Override
    public void neighborhoodChange() {
    }

    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        super.draw(g, pt, highlight);
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

    // TODO: add enterCS method call logic somewhere
    private void enterCS() {
        myTs = currTs;

        broadcast(new RequestMessage(myTs));
    }

    // TODO: add exitCS method call logic somewhere
    private void exitCS() {
        inCs = false;

        broadcast(new RelinquishMessage());
    }

    private void handleYes() {
        // safe to get coterie size this way?
        int neighboursSize = this.getOutgoingConnections().size();
        yesVotes++;

        // enter to CS if every neighbour vote yes
        if (yesVotes == neighboursSize) {
            inCs = true;
        }
    }

    private void handleInq(Node sender, InqMessage msg) {
        if (myTs == msg.timestamp) {
            send(new RelinquishMessage(), sender);
            yesVotes--;
        }
    }


    private void handleRequest(Node sender, RequestMessage msg) {
        int senderTs = msg.timestamp;

        if (!hasVoted) {
            // send vote to sender
            send(new YesMessage(), sender);
            candidate = sender;
            candidateTs = senderTs;
            hasVoted = true;
        } else {
            // add sender to deferred queue
            deferredQ.add(new Requester(sender, senderTs));

            if ((senderTs < candidateTs) && !inquired) {
                // request vote annulment
                send(new InqMessage(candidateTs), candidate);
                inquired = true;
            }
        }
    }

    private void handleRelinquish() {
        // add candidate to deferred queue
        deferredQ.add(new Requester(candidate, candidateTs));

        // get first requester from deferred queue and use as candidate
        Requester requester = deferredQ.poll();
        send(new YesMessage(), requester.node);
        candidate = requester.node;
        candidateTs = requester.timestamp;
        inquired = false;
    }

    private void handleRelease() {
        if (!deferredQ.isEmpty()) {
            // get first requester from deferred queue and use as candidate
            Requester nextRequester = deferredQ.poll();
            send(new YesMessage(), nextRequester.node);
            candidate = nextRequester.node;
            candidateTs = nextRequester.timestamp;
        } else {
            hasVoted = false;
        }

        inquired = false;
    }
}
