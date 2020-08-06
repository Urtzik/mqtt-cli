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
package com.hivemq.cli.commands.hivemq.export.clients;

import com.hivemq.cli.openapi.ApiCallback;
import com.hivemq.cli.openapi.ApiException;
import com.hivemq.cli.openapi.hivemq.ClientDetails;
import com.hivemq.cli.openapi.hivemq.ClientItem;
import com.hivemq.cli.rest.HiveMQRestService;
import org.jetbrains.annotations.NotNull;
import org.tinylog.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class ClientDetailsRetrieverTask implements Runnable {

    final @NotNull HiveMQRestService hivemqRestService;
    final @NotNull CompletableFuture<Void> clientIdsFuture;
    final @NotNull BlockingQueue<String> clientIdsQueue;
    final @NotNull BlockingQueue<ClientDetails> clientDetailsQueue;
    final @NotNull Semaphore clientDetailsInProgress;

    final static int MAX_CONCURRENT_REQUESTS = 100;

    public ClientDetailsRetrieverTask(final @NotNull HiveMQRestService hivemqRestService,
                                      final @NotNull CompletableFuture<Void> clientIdsFuture,
                                      final @NotNull BlockingQueue<String> clientIdsQueue,
                                      final @NotNull BlockingQueue<ClientDetails> clientDetailsQueue) {
        this.hivemqRestService = hivemqRestService;
        this.clientIdsFuture = clientIdsFuture;
        this.clientIdsQueue = clientIdsQueue;
        this.clientDetailsQueue = clientDetailsQueue;
        clientDetailsInProgress = new Semaphore(MAX_CONCURRENT_REQUESTS);
    }

    @Override
    public void run() {
        try {
            while (!clientIdsFuture.isDone() || !clientIdsQueue.isEmpty()) {

                final String clientId = clientIdsQueue.poll(50, TimeUnit.MILLISECONDS);
                if (clientId != null) {
                    final ClientItemApiCallback clientItemApiCallback = new ClientItemApiCallback(clientDetailsQueue, clientDetailsInProgress);
                    clientDetailsInProgress.acquire();
                    hivemqRestService.getClientDetails(clientId, clientItemApiCallback);
                }
            }

            // Block until all callbacks are finished
            clientDetailsInProgress.acquire(MAX_CONCURRENT_REQUESTS);
        }
        catch (final Exception e) {
            Logger.error(e, "Retrieval of client details failed");
            throw new CompletionException(e);
        }
        Logger.debug("Finished retrieving client details");
    }


    private static class ClientItemApiCallback implements ApiCallback<ClientItem> {
        final @NotNull BlockingQueue<ClientDetails> clientDetailsQueue;
        final @NotNull Semaphore clientDetailsInProgress;

        public ClientItemApiCallback(final @NotNull BlockingQueue<ClientDetails> clientDetailsQueue,
                                     final @NotNull Semaphore clientDetailsInProgress) {
            this.clientDetailsQueue = clientDetailsQueue;
            this.clientDetailsInProgress = clientDetailsInProgress;
        }

        @Override
        public void onFailure(ApiException e, int statusCode, Map<String, List<String>> responseHeaders) {
            Logger.trace(e,"Failed to retrieve client details");
            clientDetailsInProgress.release();
        }

        @Override
        public void onSuccess(ClientItem result, int statusCode, Map<String, List<String>> responseHeaders) {
            final ClientDetails clientDetails = result.getClient();
            if (clientDetails != null) {
                try {
                    clientDetailsQueue.put(clientDetails);
                } catch (InterruptedException ignored) {}
            }

            clientDetailsInProgress.release();
        }

        @Override
        public void onUploadProgress(long bytesWritten, long contentLength, boolean done) {
        }

        @Override
        public void onDownloadProgress(long bytesRead, long contentLength, boolean done) {
        }
    }
}