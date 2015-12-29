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

import static java.util.Arrays.asList;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.GraphDatabaseDependencies;
import org.neo4j.kernel.configuration.Config;
import org.neo4j.kernel.impl.logging.LogService;
import org.neo4j.kernel.monitoring.Monitors;
import org.neo4j.logging.LogProvider;
import org.neo4j.server.configuration.ConfigurationBuilder;
import org.neo4j.server.configuration.ConfigurationBuilder.ConfiguratorWrappingConfigurationBuilder;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ServerConfigurator;
import org.neo4j.server.configuration.ServerSettings;
import org.neo4j.server.web.ServerInternalSettings;

import org.neo4j.server.CommunityNeoServer;
import static org.neo4j.server.database.WrappedDatabase.wrappedDatabase;

public class StructrWrappingCommunityNeoServer extends CommunityNeoServer {

	public StructrWrappingCommunityNeoServer(final GraphDatabaseAPI db) {
		this(db, new ServerConfigurator(db));
	}

	public StructrWrappingCommunityNeoServer(final GraphDatabaseAPI db, final Configurator configurator) {
		this(db, new ConfiguratorWrappingConfigurationBuilder(configurator));
	}

	public StructrWrappingCommunityNeoServer(final GraphDatabaseAPI db, final ConfigurationBuilder configurator) {
		this(db, configurator, db.getDependencyResolver().resolveDependency(LogService.class).getUserLogProvider());
	}

	private StructrWrappingCommunityNeoServer(final GraphDatabaseAPI db, final ConfigurationBuilder configurator, final LogProvider logProvider) {
		super(toConfig(configurator), wrappedDatabase(db), GraphDatabaseDependencies.newDependencies().userLogProvider(logProvider).monitors(db.getDependencyResolver().resolveDependency(Monitors.class)), logProvider);
	}

	static Config toConfig(final ConfigurationBuilder configurator) {

		Config config = new Config(configurator.configuration().getParams());
		config.augment(configurator.getDatabaseTuningProperties());
		config.registerSettingsClasses(asList(ServerSettings.class, ServerInternalSettings.class, GraphDatabaseSettings.class));

		return config;
	}
}
