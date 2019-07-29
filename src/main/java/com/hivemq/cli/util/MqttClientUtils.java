package com.hivemq.cli.util;

import com.hivemq.cli.commands.Connect;
import com.hivemq.cli.commands.Disconnect;
import com.hivemq.cli.commands.Publish;
import com.hivemq.cli.commands.Subscribe;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilder;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.connect.Mqtt5Connect;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublish;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5WillPublishBuilder;
import jline.internal.Log;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pmw.tinylog.Logger;
import org.pmw.tinylog.LoggingContext;

public class MqttClientUtils {

    private static MqttClientUtils instance = null;

    private ClientCache<String, Mqtt5AsyncClient> clientCache;

    private MqttClientUtils() {
        if (clientCache == null) {
            clientCache = new ClientCache<>(true);
        }
    }

    public static MqttClientUtils getInstance() {
        if (instance == null) {
            instance = new MqttClientUtils();
        }
        return instance;
    }

    private static MqttQos getQosFromParamField(int[] qos, int i) {
        if (qos == null || qos.length <= i) {
            return MqttQos.AT_MOST_ONCE;
        }
        return getQosFromInt(qos[i]);
    }

    private static MqttQos getQosFromInt(int qos) {
        if (qos == 0) {
            return MqttQos.AT_MOST_ONCE;
        }
        return qos == 1 ? MqttQos.AT_LEAST_ONCE : MqttQos.EXACTLY_ONCE;
    }


    public Mqtt5AsyncClient connect(final @NotNull Connect connect) {
        return doConnect(connect);
    }

    public Mqtt5AsyncClient subscribe(final @NotNull Subscribe subscribe) {
        final Mqtt5AsyncClient client = getMqttClientFromCacheOrConnect(subscribe);
        if (client != null) {
            LoggingContext.put("identifier", client.getConfig().getClientIdentifier().get());
            return doSubscribe(client, subscribe);
        }
        return null;
    }

    public Mqtt5AsyncClient publish(final @NotNull Publish publish) {
        final Mqtt5AsyncClient client = getMqttClientFromCacheOrConnect(publish);
        if (client != null) {
            LoggingContext.put("identifier", client.getConfig().getClientIdentifier().get());
            return doPublish(client, publish);
        }
        return null;
    }

    public boolean isConnected(final @NotNull Subscribe subscriber) {
        LoggingContext.put("identifier", subscriber.getIdentifier());

        if (clientCache.hasKey(subscriber.getKey())) {
            final Mqtt5AsyncClient client = clientCache.get(subscriber.getKey());
            Logger.debug("Client in cache key: {} ", subscriber.getKey());
            return client.getConfig().getState().isConnected();
        }
        return false;
    }

    public boolean disconnect(final @NotNull Disconnect disconnect) {
        LoggingContext.put("identifier", disconnect.getIdentifier());

        clientCache.setVerbose(disconnect.isDebug());

        if (clientCache.hasKey(disconnect.getKey())) {
            final Mqtt5AsyncClient client = clientCache.get(disconnect.getKey());
            clientCache.remove(disconnect.getKey());
            client.disconnect();
            return true;
        }
        return false;
    }


    private Mqtt5AsyncClient doConnect(final @NotNull Connect connectCommand) {

        final String identifier = connectCommand.createIdentifier();
        LoggingContext.put("identifier", identifier);

        final @NotNull MqttClientBuilder mqttClientBuilder = createBuilder(connectCommand, identifier);
        final @NotNull Mqtt5BlockingClient client = mqttClientBuilder.useMqttVersion5().build().toBlocking();


        try {
            final @Nullable Mqtt5Publish willPublish = createWillPublish(connectCommand, identifier);

            final Mqtt5Connect connectMessage = Mqtt5Connect.builder()
                    .willPublish(willPublish)
                    .build();

            final Mqtt5ConnAck connAck = client.connect(connectMessage);
            if (connectCommand.isDebug()) {
                Log.debug("Client connect with {} ", connectCommand.toString());
            } else {
                Logger.info("Client connect with {} ", connAck.getReasonCode());
            }
            clientCache.put(connectCommand.getKey(), client.toAsync());

        } catch (Exception throwable) {
            Logger.error("Client connect failed with reason: {}", throwable.getMessage());
        }


        return client.toAsync();
    }

    private Mqtt5AsyncClient doSubscribe(Mqtt5AsyncClient client, final @NotNull Subscribe subscribe) {

        for (int i = 0; i < subscribe.getTopics().length; i++) {
            final String topic = subscribe.getTopics()[i];
            final MqttQos qos = getQosFromParamField(subscribe.getQos(), i);

            client.subscribeWith()
                    .topicFilter(topic)
                    .qos(qos)
                    .callback(publish -> {
                        final String p = new String(publish.getPayloadAsBytes());
                        if (subscribe.isDebug()) {
                            Log.debug("Client received on topic: {} message: '{}' ", topic, p);
                        } else {
                            Logger.info("Client received msg: '{}...' ", p.length() > 10 ? p.substring(0, 10) : p);
                        }
                    })
                    .send()
                    .whenComplete((subAck, throwable) -> {
                        if (throwable != null) {
                            if (subscribe.isDebug()) {
                                Log.debug("Client subscribe failed with reason: {} ", topic, throwable.getStackTrace());
                            } else {
                                Logger.error("Client subscribe failed with reason: {}", topic, throwable.getMessage());
                            }

                        } else {
                            Logger.info("Client subscribed to Topic: {} ", topic);
                        }
                    });

        }

        return client;
    }

    private Mqtt5AsyncClient doPublish(Mqtt5AsyncClient client, final @NotNull Publish publish) {
        for (int i = 0; i < publish.getTopics().length; i++) {
            final String topic = publish.getTopics()[i];
            final MqttQos qos = getQosFromParamField(publish.getQos(), i);

            client.publishWith()
                    .topic(topic)
                    .qos(qos)
                    .payload(publish.getMessage().getBytes())
                    .send()
                    .whenComplete((publishResult, throwable) -> {
                        if (throwable != null) {
                            if (publish.isDebug()) {
                                Log.debug("Client publish to topic: {} failed with reason: {} ", topic, throwable.getStackTrace());
                            } else {
                                Logger.error("Client publish to topic: {} failed with reason: {}", topic, throwable.getMessage());
                            }
                        } else {
                            final String p = publish.getMessage();
                            if (publish.isDebug()) {
                                Log.debug("Client publish to topic: {} message: '{}' ", topic, p);
                            } else {
                                Logger.info("Client publish to topic: {} message: '{}... ' ", topic,
                                        p.length() > 10 ? p.substring(0, 10) : p);
                            }
                        }
                    });
        }
        return client;
    }

    private Mqtt5Publish createWillPublish(final @NotNull Connect connectCommand, final @NotNull String identifier) throws Exception {
        // only topic is mandatory for will message creation
        if (connectCommand.getWillTopic() != null) {
            Mqtt5WillPublishBuilder builder = Mqtt5WillPublish.builder()
                    .topic(connectCommand.getWillTopic())
                    .payload(connectCommand.getWillMessage().getBytes())
                    .qos(getQosFromInt(connectCommand.getWillQos()))
                    .retain(connectCommand.isWillRetain());
            try {
                return ((Mqtt5WillPublishBuilder.Complete) builder).build().asWill();
            } catch (Exception e) {
                Logger.error("Client can't create Will Message, error: {} " + e.getMessage());
                throw e;
            }
        } else if (!connectCommand.getWillMessage().isEmpty()) {
            //seems somebody like to create a will message without a topic
            Logger.debug("option -wt is missing if a will message is configured - command was: {} ", connectCommand.toString());
        }
        return null;
    }

    private MqttClientBuilder createBuilder(final @NotNull Connect connectCommand, final @NotNull String identifier) {
        return MqttClient.builder()
                .serverHost(connectCommand.getHost())
                .serverPort(connectCommand.getPort())
                .identifier(identifier);
    }

    private Mqtt5AsyncClient getMqttClientFromCacheOrConnect(final @NotNull Connect connect) {
        clientCache.setVerbose(connect.isDebug());

        Mqtt5AsyncClient mqtt5Client = null;

        if (clientCache.hasKey(connect.getKey())) {
            mqtt5Client = clientCache.get(connect.getKey());
        }

        if (mqtt5Client == null || (!mqtt5Client.getConfig().getState().isConnectedOrReconnect())) {
            mqtt5Client = doConnect(connect);
        }
        return mqtt5Client;
    }

}