/**
 * Copyright 2012 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.corundumstudio.socketio.transport;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.corundumstudio.socketio.DisconnectableHub;
import com.corundumstudio.socketio.StoreFactory;
import com.corundumstudio.socketio.Transport;
import com.corundumstudio.socketio.ack.AckManager;
import com.corundumstudio.socketio.messages.XHRNewChannelMessage;
import com.corundumstudio.socketio.messages.XHRPacketMessage;
import com.corundumstudio.socketio.parser.Packet;
import com.corundumstudio.socketio.parser.PacketType;

public class XHRPollingClient extends MainBaseClient {

    private final Queue<Packet> packetQueue = new ConcurrentLinkedQueue<Packet>();
    private String origin;

    public XHRPollingClient(AckManager ackManager, DisconnectableHub disconnectable, UUID sessionId, Transport transport, StoreFactory storeFactory) {
        super(sessionId, ackManager, disconnectable, transport, storeFactory);
    }

    public void bindChannel(Channel channel, String origin) {
        this.origin = origin;
        setChannel(channel);
        sendPackets();
        channel.write(new XHRNewChannelMessage(origin, getSessionId()));
    }

    public String getOrigin() {
        return origin;
    }

    public ChannelFuture send(final Packet packet) {
        final Channel currChannel = getChannel();
        ChannelFuture res = currChannel.write(new XHRPacketMessage(getSessionId(), origin, packet));
        if (!packet.getType().equals(PacketType.NOOP)) {
            res.addListener(new GenericFutureListener<Future<Void>>() {
                @Override
                public void operationComplete(Future<Void> future) throws Exception {
                    if (future.isSuccess()) {
                        return;
                    }

                    // channel could be closed because new channel was bound
                    // so we need to resend packet
                    if (!getChannel().equals(currChannel)) {
                        send(packet);
                        sendPackets();
                    } else {
                        packetQueue.add(packet);
                    }
                }
            });
        }

        return res;
    }

    private void sendPackets() {
        while (true) {
            Packet p = packetQueue.poll();
            if (p == null) {
                break;
            }
            send(p);
        }
    }
}
