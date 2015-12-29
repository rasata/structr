/**
 * Copyright (C) 2010-2015 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Structr is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr. If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.neo4j;

import java.io.File;

import org.neo4j.helpers.Pair;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.GraphDatabaseDependencies;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.logging.Log;
import org.neo4j.logging.LogProvider;
import org.neo4j.server.Bootstrapper;
import org.neo4j.server.NeoServer;
import org.neo4j.server.WrappingNeoServer;
import org.neo4j.server.configuration.ConfigurationBuilder;
import org.neo4j.server.configuration.ConfigurationBuilder.ConfiguratorWrappingConfigurationBuilder;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ServerConfigurator;

public class StructrWrappingNeoServerBootstrapper extends Bootstrapper {

	private final GraphDatabaseAPI db;
	private final ConfigurationBuilder configurator;

	public StructrWrappingNeoServerBootstrapper(final GraphDatabaseAPI db) {
		this(db, new ServerConfigurator(db));
	}

	public void start() {
		super.start(null);
	}

	public StructrWrappingNeoServerBootstrapper(final GraphDatabaseAPI db, final Configurator configurator) {
		this(db, new ConfiguratorWrappingConfigurationBuilder(configurator));
	}

	private StructrWrappingNeoServerBootstrapper(final GraphDatabaseAPI db, final ConfigurationBuilder configurator) {
		this.db = db;
		this.configurator = configurator;
	}

	@Override
	protected NeoServer createNeoServer(final Config config, final GraphDatabaseDependencies dependencies, LogProvider userLogProvider) {
		return new WrappingNeoServer(db, configurator);
	}

	@Override
	protected Config createConfig(final Log log, final File file, final Pair<String, String>[] configOverrides) {
		return StructrWrappingCommunityNeoServer.toConfig(configurator);
	}
}
