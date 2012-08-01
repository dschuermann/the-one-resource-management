/* 
 * Copyright 2012 Dominik Schürmann <dominik@dominikschuermann.de>
 * Released under GPLv3. See LICENSE.txt for details. 
 */

package report;

import applications.ResourceManagementApplication;
import core.Application;
import core.ApplicationListener;
import core.DTNHost;

/**
 * Reporter for the <code>ResourceManagementApplication</code>. Counts the
 * number of requests and responses sent and received. Calculates success
 * probabilities.
 * 
 * @author Dominik Schürmann
 */
public class ResourceManagementAppReporter extends Report implements
        ApplicationListener {

    private int requestsSent = 0, requestsReceived = 0;
    private int responsesSent = 0, responsesReceived = 0;
    private int unidirectionalSent = 0, unidirectionalReceived = 0;

    private int requestsSentResHog = 0, requestsReceivedResHog = 0;
    private int responsesSentResHog = 0, responsesReceivedResHog = 0;

    public void gotEvent(String event, Object params, Application app,
            DTNHost host) {
        // Check that the event is sent by correct application type
        if (!(app instanceof ResourceManagementApplication))
            return;

        // Increment the counters based on the event type
        if (event.equalsIgnoreCase("GotRequest")) {
            requestsReceived++;
        }
        if (event.equalsIgnoreCase("SentResponse")) {
            responsesSent++;
        }
        if (event.equalsIgnoreCase("GotResponse")) {
            responsesReceived++;
        }
        if (event.equalsIgnoreCase("SentRequest")) {
            requestsSent++;
        }
        if (event.equalsIgnoreCase("GotUnidirectional")) {
            unidirectionalReceived++;
        }
        if (event.equalsIgnoreCase("SentUnidirectional")) {
            unidirectionalSent++;
        }

        if (event.equalsIgnoreCase("GotRequestResHog")) {
            requestsReceivedResHog++;
        }
        if (event.equalsIgnoreCase("SentResponseResHog")) {
            responsesSentResHog++;
        }
        if (event.equalsIgnoreCase("GotResponseResHog")) {
            responsesReceivedResHog++;
        }
        if (event.equalsIgnoreCase("SentRequestResHog")) {
            requestsSentResHog++;
        }

    }

    @Override
    public void done() {
        write("Stats for scenario " + getScenarioName() + "\nsim_time: "
                + format(getSimTime()));
        double requestProb = 0; // request probability
        double responseProb = 0; // response probability
        double successProb = 0; // success probability

        double requestProbResHog = 0; // request probability
        double responseProbResHog = 0; // response probability
        double successProbResHog = 0; // success probability

        if (this.requestsSent > 0) {
            requestProb = (1.0 * this.requestsReceived) / this.requestsSent;
        }
        if (this.responsesSent > 0) {
            responseProb = (1.0 * this.responsesReceived) / this.responsesSent;
        }
        if (this.requestsSent > 0) {
            successProb = (1.0 * this.responsesReceived) / this.requestsSent;
        }

        if (this.requestsSentResHog > 0) {
            requestProbResHog = (1.0 * this.requestsReceivedResHog)
                    / this.requestsSentResHog;
        }
        if (this.responsesSentResHog > 0) {
            responseProbResHog = (1.0 * this.responsesReceivedResHog)
                    / this.responsesSentResHog;
        }

        if (this.requestsSentResHog > 0) {
            successProbResHog = (1.0 * this.responsesReceivedResHog)
                    / this.requestsSentResHog;
        }

        String statsText = "requests sent: " + this.requestsSent
                + "\nrequests received: " + this.requestsReceived
                + "\nresponses sent: " + this.responsesSent
                + "\nresponses received: " + this.responsesReceived
                + "\nrequests reshog sent: " + this.requestsSentResHog
                + "\nrequests reshog received: " + this.requestsReceivedResHog
                + "\nresponses reshog sent: " + this.responsesSentResHog
                + "\nresponses reshog received: "
                + this.responsesReceivedResHog + "\nunidirectional sent: "
                + this.unidirectionalSent + "\nunidirectional received: "
                + this.unidirectionalReceived + "\nrequest delivery prob: "
                + format(requestProb) + "\nresponse delivery prob: "
                + format(responseProb) + "\nrequest/response success prob: "
                + format(successProb) + "\nrequest reshog delivery prob: "
                + format(requestProbResHog)
                + "\nresponse reshog delivery prob: "
                + format(responseProbResHog)
                + "\nrequest/response reshog success prob: "
                + format(successProbResHog);

        write(statsText);

        super.done();
    }
}
