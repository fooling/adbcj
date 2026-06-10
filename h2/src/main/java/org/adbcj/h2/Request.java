package org.adbcj.h2;

import org.adbcj.DbCallback;
import org.adbcj.DbException;
import org.adbcj.h2.decoding.DecoderState;
import org.adbcj.h2.packets.ClientToServerPacket;

import java.util.ArrayList;

public class Request<T> {
    private final String description;
    protected final DbCallback<T> toComplete;
    private final DecoderState startState;
    private final ClientToServerPacket request;

    protected Request(
            String description,
            DbCallback<T> toComplete,
            DecoderState startState,
            ClientToServerPacket request) {
        this.description = description;
        this.toComplete = toComplete;
        this.startState = startState;
        this.request = request;
    }

    public DecoderState getStartState() {
        return startState;
    }

    @Override
    public String toString() {
        return String.valueOf(description);
    }

    ClientToServerPacket getRequest() {
        return request;
    }

    public void completeFailure(DbException failed){
        toComplete.onComplete(null, failed);
    }

}


/**
 * Usually we aggressively pipeline request through the TCP connection.
 *
 * However, executeQuery, executeUpdate are internally actually multiple steps.
 * If those are just pipelined through, and a error occur in the first step, the follow up step make no sense.
 * Even works, the follow up step are invalid commands, tripping up the H2 server with more errors.
 *
 * So, to avoid this, we have block requests. If a blocking request is running, all follow up commands are qued behind it.
 * Then, once it completed, the commands are queued up again
 *
 * Assummes that all it's operaiton are done withing the connection's lock
 */
class BlockingRequestInProgress<T> extends Request<T> {

    private final ArrayList<Request> waitingRequests = new ArrayList<>();
    private final H2Connection connection;
    private final Request<T> blockedOn;

    BlockingRequestInProgress(
            H2Connection connection,
            String description,
            DbCallback<T> toComplete,
            DecoderState startState,
            ClientToServerPacket request,
            Request<T> blockedOn
            ) {
        super(description, toComplete, startState, request);
        this.connection = connection;
        this.blockedOn = blockedOn;
    }

    void add(Request request) {
        waitingRequests.add(request);
    }


    boolean unblockBy(Request nextRequest) {
        return blockedOn==nextRequest;
    }

    void continueWithRequests() {
        connection.blockingRequest = null;
        for (Request waitingRequest : waitingRequests) {
            connection.forceQueRequest(waitingRequest);
        }
    }

    public int size() {
        return waitingRequests.size();
    }
}