/*
 * Copyright 2019 HiveMQ and the HiveMQ Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.hivemq.cli.mqtt;

import com.hivemq.cli.commands.Subscribe;
import com.hivemq.cli.utils.FileUtils;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.bouncycastle.util.encoders.Base64;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pmw.tinylog.Logger;
import org.pmw.tinylog.LoggingContext;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class SubscribeMqtt5PublishCallback implements Consumer<Mqtt5Publish> {

    @NotNull private final String identifier;
    @Nullable private final File publishFile;
    private final boolean printToStdout;
    private final boolean isBase64;
    private final boolean debug;
    private final boolean verbose;

    SubscribeMqtt5PublishCallback(final @NotNull Subscribe subscribe) {
        identifier = subscribe.getIdentifier();
        printToStdout = subscribe.isPrintToSTDOUT();
        publishFile = subscribe.getPublishFile();
        isBase64 = subscribe.isBase64();
        debug = subscribe.isDebug();
        verbose = subscribe.isVerbose();
    }

    @Override
    public void accept(final @NotNull Mqtt5Publish mqtt5Publish) {

        PrintWriter fileWriter = null;
        if (publishFile != null) {
            fileWriter = FileUtils.createFileAppender(publishFile);
        }


        byte[] payload = mqtt5Publish.getPayloadAsBytes();
        String payloadMessage = new String(payload);

        if (isBase64) {
            payloadMessage = Base64.toBase64String(payload);
        }

        if (fileWriter != null) {
            fileWriter.println(mqtt5Publish.getTopic() + ": " + payloadMessage);
            fileWriter.flush();
            fileWriter.close();
        }

        if (printToStdout) {
            System.out.println(payloadMessage);
        }

        if (verbose) {
            Logger.trace("Client {} received PUBLISH: {}, MESSAGE: '{}'", identifier, mqtt5Publish, new String(mqtt5Publish.getPayloadAsBytes(), StandardCharsets.UTF_8));
        }
        else if (debug) {
            Logger.debug("Client {} received PUBLISH: (Topic: '{}', MESSAGE: '{}')", identifier, mqtt5Publish.getTopic(), new String(mqtt5Publish.getPayloadAsBytes(), StandardCharsets.UTF_8));
        }

    }


}
