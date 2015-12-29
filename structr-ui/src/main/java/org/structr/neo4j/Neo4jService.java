/**
 * Copyright (C) 2010-2015 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.neo4j;

import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.configuration.Configuration;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.server.configuration.Configurator;
import org.neo4j.server.configuration.ServerConfigurator;
import org.structr.common.StructrConf;
import org.structr.core.Command;
import org.structr.core.RunnableService;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;

/**
 *
 *
 */
public class Neo4jService implements RunnableService {

	private static final Logger logger = Logger.getLogger(Neo4jService.class.getName());
	private boolean isRunning          = false;

	private static int port;
	private static String host;
	private StructrWrappingCommunityNeoServer neoServerBootstrapper;

	public static final String NEO4J_BROWSER_HOST          = "neo4j.server.host";
	public static final String NEO4J_BROWSER_PORT          = "neo4j.server.port";

	@Override
	public void startService() {



		try {
			final GraphDatabaseAPI api = (GraphDatabaseAPI) StructrApp.getInstance().getGraphDatabaseService();
			final ServerConfigurator config = new ServerConfigurator(api);
			final Configuration configuration = config.configuration();
				
			configuration.addProperty(Configurator.WEBSERVER_ADDRESS_PROPERTY_KEY, host);
			configuration.addProperty(Configurator.WEBSERVER_PORT_PROPERTY_KEY, port);

			logger.log(Level.INFO, "Starting Neo4j server on host {0} and port {1}", new Object[] { host, String.valueOf(port) });
			
			// Save current logger config
			final Level oldLevel        = Logger.getLogger("").getLevel();
			final Filter oldFilter      = Logger.getLogger("").getFilter();
			final Handler[] oldHandlers = Logger.getLogger("").getHandlers();
			
			neoServerBootstrapper = new StructrWrappingCommunityNeoServer(api, config);
			neoServerBootstrapper.start();
			
			// Reset to old logger config (Neo4j's Bootstrapper changes global log level)
			resetLogger(Logger.getLogger(""), oldLevel, oldFilter, oldHandlers);
			
			logger.log(Level.INFO, "Neo4j server started on port {0}", new Object[] { String.valueOf(port) });

		} catch (Exception e) {

			logger.log(Level.SEVERE, e.getMessage());

		}

	}

	@Override
	public void stopService() {

		if (isRunning) {
			this.shutdown();
		}
	}

	@Override
	public boolean runOnStartup() {
		return true;
	}

	@Override
	public boolean isRunning() {
		return neoServerBootstrapper != null;
	}

	@Override
	public void injectArguments(Command command) {
	}

	@Override
	public void initialize(final Services services, final StructrConf config) {

		final StructrConf finalConfig = new StructrConf();

		// Default config
		finalConfig.setProperty(NEO4J_BROWSER_PORT,      "7474");

		Services.mergeConfiguration(finalConfig, config);

		final String configuredPort = finalConfig.getProperty(NEO4J_BROWSER_PORT);
		host = finalConfig.getProperty(NEO4J_BROWSER_HOST);

		try {
			port = Integer.parseInt(configuredPort);

		} catch (Throwable t) {

			logger.log(Level.SEVERE, "Unable to parse Neo4j Browser port {0}", configuredPort);

			port = -1;
		}

		if (port == -1) {
			logger.log(Level.SEVERE, "Unable to start Neo4j service.");
		}
	}

	@Override
	public void initialized() {}

	@Override
	public void shutdown() {
		if (isRunning) {
			neoServerBootstrapper.stop();
			this.isRunning = false;
		}
	}

	@Override
	public String getName() {
		return Neo4jService.class.getSimpleName();
	}

	@Override
	public boolean isVital() {
		return false;
	}

	private void resetLogger(final Logger logger, final Level oldLevel, final Filter oldFilter, final Handler[] oldHandlers) {

		logger.setLevel(oldLevel);
		logger.setFilter(oldFilter);

		for (final Handler h : Logger.getLogger("").getHandlers()) {
			logger.removeHandler(h);
		}

		for (final Handler h : oldHandlers) {
			logger.addHandler(h);
		}
	}
	
}
