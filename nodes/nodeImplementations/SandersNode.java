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

/**
 * The Node of the sample project.
 */
@Getter
@Setter
public class SandersNode extends Node {

    /**
     * the neighbor with the smallest ID
     */
    private SandersNode next;

    /**
     * number of messages sent by this node in the current round
     */
    private int msgSentInThisRound;

    /**
     * total number of messages sent by this node
     */
    private int msgSent;

    /**
     * The amount to increment the data of the message each time it goes throug a
     * node.
     */
    private int increment;

    Logging log = Logging.getLogger("s1_log");

    // a flag to prevent all nodes from sending messages
    @Getter
    @Setter
    private static boolean isSending = true;

    @Override
    public void handleMessages(Inbox inbox) {
        if (!isSending()) { // don't even look at incoming messages
            return;
        }
        if (inbox.hasNext()) {
            Message msg = inbox.next();
        }
    }

    @Override
    public void preStep() {
        this.msgSent += this.msgSentInThisRound;
        this.msgSentInThisRound = 0;
    }

    @Override
    public void init() {
        // initialize the node
        try {
            // Read a value from the configuration file config.xml.
            // The following command reads an integer, which is expected to
            // be stored in either of the two following styles in the XML file:
            // <S1Node>
            // <increment value="2"/>
            // </S1Node>
            // OR
            // <S1Node increment="2"/>

            this.increment = Configuration.getIntegerParameter("s1node/increment");
        } catch (CorruptConfigurationEntryException e) {
            // Missing entry in the configuration file: Abort the simulation and
            // display a message to the user
            throw new SinalgoFatalException(e.getMessage());
        }
    }

    @Override
    public void neighborhoodChange() {
        this.setNext(null);
        for (Edge e : this.getOutgoingConnections()) {
            if (this.getNext() == null) {
                this.setNext((SandersNode) e.getEndNode());
            } else {
                if (e.getEndNode().compareTo(this.getNext()) < 0) {
                    this.setNext((SandersNode) e.getEndNode());
                }
            }
        }
    }

    @Override
    public void draw(Graphics g, PositionTransformation pt, boolean highlight) {
        // set the color of this node
        this.setColor(
                new Color((float) 0.5 / (1 + this.msgSentInThisRound), (float) 0.5, (float) 1.0 / (1 + this.msgSentInThisRound)));
        String text = Integer.toString(this.msgSent) + "|" + this.msgSentInThisRound;
        // draw the node as a circle with the text inside
        super.drawNodeAsDiskWithText(g, pt, highlight, text, 10, Color.YELLOW);
        // super.drawNodeAsSquareWithText(g, pt, highlight, text, 10, Color.YELLOW);
    }

    @Override
    public void postStep() {

    }

    @Override
    public String toString() {
        return "Messages sent so far: " + this.msgSent + "\nMessages sent in this round: " + this.msgSentInThisRound;
    }

    @Override
    public void checkRequirements() throws WrongConfigurationException {
        if (this.increment < 0) {
            throw new WrongConfigurationException(
                    "S1Node: The increment value (specified in the config file) must be greater or equal to 1.");
        }
    }
}
