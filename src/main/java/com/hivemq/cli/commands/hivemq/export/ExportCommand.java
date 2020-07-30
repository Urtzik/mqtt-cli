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
package com.hivemq.cli.commands.hivemq.export;

import picocli.CommandLine;

import javax.inject.Inject;


@CommandLine.Command(
        name = "export",
        description = "Exports the specified details from a HiveMQ API endpoint")
public class ExportCommand implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Inject
    public ExportCommand() { }

    @Override
    public void run() {
        System.out.println(spec.commandLine().getUsageMessage(spec.commandLine().getColorScheme()));
    }
}