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
package org.structr.web.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.AccessMode;
import org.structr.common.PathHelper;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.Services;
import org.structr.core.app.StructrApp;
import org.structr.core.auth.AuthHelper;
import org.structr.core.auth.Authenticator;
import org.structr.core.auth.exception.AuthenticationException;
import org.structr.core.auth.exception.UnauthorizedException;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.Person;
import org.structr.core.entity.Principal;
import org.structr.core.entity.ResourceAccess;
import org.structr.core.entity.SuperUser;
import org.structr.core.property.PropertyKey;
import org.structr.web.entity.User;
import org.structr.web.resource.RegistrationResource;
import org.structr.web.servlet.HtmlServlet;

//~--- classes ----------------------------------------------------------------

/**
 *
 *
 */
public class UiAuthenticator implements Authenticator {

	private static final Logger logger       = Logger.getLogger(UiAuthenticator.class.getName());

	protected boolean examined = false;
	protected static boolean userAutoCreate;
	protected static boolean userAutoLogin;
	private static Class userClass;

	private enum Method { GET, PUT, POST, DELETE, HEAD, OPTIONS }
	private static final Map<String, Method> methods = new LinkedHashMap();

	// HTTP methods
	static {

		methods.put("GET", Method.GET);
		methods.put("PUT", Method.PUT);
		methods.put("POST", Method.POST);
		methods.put("HEAD", Method.HEAD);
		methods.put("DELETE", Method.DELETE);
		methods.put("OPTIONS", Method.OPTIONS);

	}

	// access flags
	public static final long FORBIDDEN		= 0;
	public static final long AUTH_USER_GET		= 1;
	public static final long AUTH_USER_PUT		= 2;
	public static final long AUTH_USER_POST		= 4;
	public static final long AUTH_USER_DELETE	= 8;

	public static final long NON_AUTH_USER_GET	= 16;
	public static final long NON_AUTH_USER_PUT	= 32;
	public static final long NON_AUTH_USER_POST	= 64;
	public static final long NON_AUTH_USER_DELETE	= 128;

	public static final long AUTH_USER_OPTIONS	= 256;
	public static final long NON_AUTH_USER_OPTIONS	= 512;

	public static final long AUTH_USER_HEAD		= 1024;
	public static final long NON_AUTH_USER_HEAD	= 2048;

	//~--- methods --------------------------------------------------------

	/**
	 * Examine request and try to find a user.
	 *
	 * First, check session id, then try external (OAuth) authentication,
	 * finally, check standard login by credentials.
	 *
	 * @param request
	 * @param response
	 * @return security context
	 * @throws FrameworkException
	 */
	@Override
	public SecurityContext initializeAndExamineRequest(final HttpServletRequest request, final HttpServletResponse response) throws FrameworkException {

		// Initialize custom user class
		getUserClass();

		SecurityContext securityContext;

		Principal user = checkSessionAuthentication(request);

		if (user == null) {

			user = checkExternalAuthentication(request, response);

		}

		if (user == null) {

			user = getUser(request, true);

		}

		if (user == null) {

			// If no user could be determined, assume frontend access
			securityContext = SecurityContext.getInstance(user, request, AccessMode.Frontend);

		} else {


			if (user instanceof SuperUser) {

				securityContext = SecurityContext.getSuperUserInstance(request);

			} else {

				securityContext = SecurityContext.getInstance(user, request, AccessMode.Backend);

			}

		}

		securityContext.setAuthenticator(this);

		// Check CORS settings (Cross-origin resource sharing, see http://en.wikipedia.org/wiki/Cross-origin_resource_sharing)
		final String origin = request.getHeader("Origin");
		if (!StringUtils.isBlank(origin)) {

			final Services services = Services.getInstance();

			response.setHeader("Access-Control-Allow-Origin", origin);

			 // allow cross site resource sharing (read only)
			final String maxAge = services.getConfigurationValue(Services.ACCESS_CONTROL_MAX_AGE);
			if (StringUtils.isNotBlank(maxAge)) {
				response.setHeader("Access-Control-MaxAge", maxAge);
			}

			final String allowMethods = services.getConfigurationValue(Services.ACCESS_CONTROL_ALLOW_METHODS);
			if (StringUtils.isNotBlank(allowMethods)) {
				response.setHeader("Access-Control-Allow-Methods", allowMethods);
			}

			final String allowHeaders = services.getConfigurationValue(Services.ACCESS_CONTROL_ALLOW_HEADERS);
			if (StringUtils.isNotBlank(allowHeaders)) {
				response.setHeader("Access-Control-Allow-Headers", allowHeaders);
			}

			final String allowCredentials = services.getConfigurationValue(Services.ACCESS_CONTROL_ALLOW_CREDENTIALS);
			if (StringUtils.isNotBlank(allowCredentials)) {
				response.setHeader("Access-Control-Allow-Credentials", allowCredentials);
			}

			final String exposeHeaders = services.getConfigurationValue(Services.ACCESS_CONTROL_EXPOSE_HEADERS);
			if (StringUtils.isNotBlank(exposeHeaders)) {
				response.setHeader("Access-Control-Expose-Headers", exposeHeaders);
			}
		 }

		examined = true;

		// store a reference of the response object in SecurityContext
		// to be able to stream data directly from builtin functions
		securityContext.setResponse(response);

		return securityContext;

	}

	@Override
	public boolean hasExaminedRequest() {

		return examined;

	}

	@Override
	public void setUserAutoCreate(final boolean userAutoCreate) {

		UiAuthenticator.userAutoCreate = userAutoCreate;
	}

	@Override
	public void setUserAutoLogin(boolean userAutoLogin) {

		UiAuthenticator.userAutoLogin = userAutoLogin;
	}

	@Override
	public void checkResourceAccess(final SecurityContext securityContext, final HttpServletRequest request, final String rawResourceSignature, final String propertyView)
		throws FrameworkException {

		final ResourceAccess resourceAccess = ResourceAccess.findGrant(securityContext, rawResourceSignature);
		final Method method                 = methods.get(request.getMethod());
		final Principal user                = getUser(request, true);
		final boolean validUser             = (user != null);

		// super user is always authenticated
		if (validUser && (user instanceof SuperUser || user.getProperty(Principal.isAdmin))) {
			return;
		}

		// no grants => no access rights
		if (resourceAccess == null) {

			logger.log(Level.INFO, "No resource access grant found for signature {0}.", rawResourceSignature);

			throw new UnauthorizedException("Forbidden");

		} else {

			switch (method) {

				case GET :

					if (!validUser && resourceAccess.hasFlag(NON_AUTH_USER_GET)) {

						return;

					}

					if (validUser && resourceAccess.hasFlag(AUTH_USER_GET)) {

						return;

					}

					break;

				case PUT :

					if (!validUser && resourceAccess.hasFlag(NON_AUTH_USER_PUT)) {

						return;

					}

					if (validUser && resourceAccess.hasFlag(AUTH_USER_PUT)) {

						return;

					}

					break;

				case POST :

					if (!validUser && resourceAccess.hasFlag(NON_AUTH_USER_POST)) {

						return;

					}

					if (validUser && resourceAccess.hasFlag(AUTH_USER_POST)) {

						return;

					}

					break;

				case DELETE :

					if (!validUser && resourceAccess.hasFlag(NON_AUTH_USER_DELETE)) {

						return;

					}

					if (validUser && resourceAccess.hasFlag(AUTH_USER_DELETE)) {

						return;

					}

					break;

				case OPTIONS :

					if (!validUser && resourceAccess.hasFlag(NON_AUTH_USER_OPTIONS)) {

						return;

					}

					if (validUser && resourceAccess.hasFlag(AUTH_USER_OPTIONS)) {

						return;

					}

					break;

				case HEAD :

					if (!validUser && resourceAccess.hasFlag(NON_AUTH_USER_HEAD)) {

						return;

					}

					if (validUser && resourceAccess.hasFlag(AUTH_USER_HEAD)) {

						return;

					}

					break;
			}
		}

		logger.log(Level.INFO, "Resource access grant found for signature {0}, but method {1} not allowed for {2}.", new Object[] { rawResourceSignature, method, validUser ? "authenticated users" : "public users" } );

		throw new UnauthorizedException("Forbidden");

	}

	@Override
	public Principal doLogin(final HttpServletRequest request, final String emailOrUsername, final String password) throws AuthenticationException, FrameworkException {

		final Principal user = AuthHelper.getPrincipalForPassword(Person.eMail, emailOrUsername, password);
		if  (user != null) {

			final String allowLoginBeforeConfirmation = Services.getInstance().getConfigurationValue(RegistrationResource.ALLOW_LOGIN_BEFORE_CONFIRMATION);
			if (user.getProperty(User.confirmationKey) != null && Boolean.FALSE.equals(Boolean.parseBoolean(allowLoginBeforeConfirmation))) {
				logger.log(Level.WARNING, "Login as {0} not allowed before confirmation.", user);
				throw new AuthenticationException(AuthHelper.STANDARD_ERROR_MSG);
			}

			AuthHelper.doLogin(request, user);
		}

		return user;
	}

	@Override
	public void doLogout(final HttpServletRequest request) {

		try {
			final Principal user = getUser(request, false);
			if (user != null) {

				AuthHelper.doLogout(request, user);
			}

			final HttpSession session = request.getSession(false);
			if (session != null) {

				session.invalidate();
			}

		} catch (IllegalStateException | FrameworkException ex) {

			logger.log(Level.WARNING, "Error while logging out user", ex);
		}
	}

	/**
	 * This method checks all configured external authentication services.
	 *
	 * @param request
	 * @param response
	 * @return user
	 */
	protected static Principal checkExternalAuthentication(final HttpServletRequest request, final HttpServletResponse response) throws FrameworkException {

		final String path = PathHelper.clean(request.getPathInfo());
		final String[] uriParts = PathHelper.getParts(path);

		logger.log(Level.FINE, "Checking external authentication ...");

		if (uriParts == null || uriParts.length != 3 || !("oauth".equals(uriParts[0]))) {

			logger.log(Level.FINE, "Incorrect URI parts for OAuth process, need /oauth/<name>/<action>");
			return null;
		}

		final String name   = uriParts[1];
		final String action = uriParts[2];

		// Try to getValue an OAuth2 server for the given name
		final StructrOAuthClient oauthServer = StructrOAuthClient.getServer(name);

		if (oauthServer == null) {

			logger.log(Level.FINE, "No OAuth2 authentication server configured for {0}", path);
			return null;

		}

		if ("login".equals(action)) {

			try {

				response.sendRedirect(oauthServer.getEndUserAuthorizationRequestUri(request));
				return null;

			} catch (Exception ex) {

				logger.log(Level.SEVERE, "Could not send redirect to authorization server", ex);
			}

		} else if ("auth".equals(action)) {

			final String accessToken = oauthServer.getAccessToken(request);
			final SecurityContext superUserContext = SecurityContext.getSuperUserInstance();

			if (accessToken != null) {

				logger.log(Level.FINE, "Got access token {0}", accessToken);
				//securityContext.setAttribute("OAuthAccessToken", accessToken);

				String value = oauthServer.getCredential(request);
				logger.log(Level.FINE, "Got credential value: {0}", new Object[] { value });

				if (value != null) {

					PropertyKey credentialKey = oauthServer.getCredentialKey();

					Principal user = AuthHelper.getPrincipalForCredential(credentialKey, value);

					if (user == null && userAutoCreate) {

						user = RegistrationResource.createUser(superUserContext, credentialKey, value, true, userClass);

					}

					if (user != null) {

						AuthHelper.doLogin(request, user);
						HtmlServlet.setNoCacheHeaders(response);

						try {

							logger.log(Level.FINE, "Response status: {0}", response.getStatus());

							response.sendRedirect(oauthServer.getReturnUri());

						} catch (IOException ex) {

							logger.log(Level.SEVERE, "Could not redirect to {0}: {1}", new Object[]{oauthServer.getReturnUri(), ex});

						}
						return user;
					}
				}
			}
		}

		try {

			response.sendRedirect(oauthServer.getErrorUri());

		} catch (IOException ex) {

			logger.log(Level.SEVERE, "Could not redirect to {0}: {1}", new Object[]{ oauthServer.getReturnUri(), ex });

		}

		return null;

	}

	protected Principal checkSessionAuthentication(final HttpServletRequest request) throws FrameworkException {

		String requestedSessionId = request.getRequestedSessionId();
		HttpSession session       = request.getSession(false);
		boolean sessionValid      = false;

		if (requestedSessionId == null) {

			// No session id requested => create new session
			AuthHelper.newSession(request);

			// we just created a totally new session, there can't
			// be a user with this session ID, so don't search.
			return null;

		} else {

			// Existing session id, check if we have an existing session
			if (session != null) {

				if (session.getId().equals(requestedSessionId)) {

					if (AuthHelper.isSessionTimedOut(session)) {

						sessionValid = false;

						// remove invalid session ID from user
						invalidateSessionId(requestedSessionId);

					} else {

						sessionValid = true;
					}
				}

			} else {

				// No existing session, create new
				session = AuthHelper.newSession(request);

				// remove invalid session ID from user
				invalidateSessionId(requestedSessionId);

			}

		}

		if (sessionValid) {

			final Principal user = AuthHelper.getPrincipalForSessionId(session.getId());
			logger.log(Level.FINE, "Valid session found: {0}, last accessed {1}, authenticated with user {2}", new Object[] { session, session.getLastAccessedTime(), user });

			return user;


		} else {

			final Principal user = AuthHelper.getPrincipalForSessionId(requestedSessionId);

			logger.log(Level.FINE, "Invalid session: {0}, last accessed {1}, authenticated with user {2}", new Object[] { session, (session != null ? session.getLastAccessedTime() : ""), user });

			if (user != null) {

				AuthHelper.doLogout(request, user);
			}

			try { request.logout(); request.changeSessionId(); } catch (Throwable t) {}

		}


		return null;

	}

	public static void writeUnauthorized(final HttpServletResponse response) throws IOException {

		response.setHeader("WWW-Authenticate", "BASIC realm=\"Restricted Access\"");
		response.sendError(HttpServletResponse.SC_UNAUTHORIZED);

	}

	public static void writeNotFound(final HttpServletResponse response) throws IOException {

		response.sendError(HttpServletResponse.SC_NOT_FOUND);

	}

	public static void writeInternalServerError(final HttpServletResponse response) {

		try {

			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);

		} catch (IOException ignore) {}

	}

	@Override
	public boolean getUserAutoCreate() {
		return userAutoCreate;
	}

	@Override
	public boolean getUserAutoLogin() {
		return userAutoLogin;
	}

	@Override
	public Class getUserClass() {

		if (userClass == null) {

			String configuredCustomClassName = StructrApp.getConfigurationValue("Registration.customUserClass");
			if (StringUtils.isEmpty(configuredCustomClassName)) {
				configuredCustomClassName = User.class.getSimpleName();
			}
			userClass = StructrApp.getConfiguration().getNodeEntityClass(configuredCustomClassName);

		}

		return userClass;

	}

	@Override
	public Principal getUser(final HttpServletRequest request, final boolean tryLogin) throws FrameworkException {

		Principal user = null;

		// First, check session (JSESSIONID cookie)
		final HttpSession session = request.getSession(false);

		if (session != null) {

			user = AuthHelper.getPrincipalForSessionId(session.getId());

		}

		if (user == null) {

			// Second, check X-Headers
			String userName = request.getHeader("X-User");
			String password = request.getHeader("X-Password");
			String token    = request.getHeader("X-StructrSessionToken");

			// Try to authorize with a session token first
			if (token != null) {

				user = AuthHelper.getPrincipalForSessionId(token);

			} else if ((userName != null) && (password != null)) {

				if (tryLogin) {

					user = AuthHelper.getPrincipalForPassword(AbstractNode.name, userName, password);

				}
			}
		}

		return user;

	}

	// ----- private methods -----
	private void invalidateSessionId(final String sessionId) {

		// find user with given session ID and remove ID from list of valid sessions
		final Principal userWithInvalidSession = AuthHelper.getPrincipalForSessionId(sessionId);
		if (userWithInvalidSession != null) {

			userWithInvalidSession.removeSessionId(sessionId);
		}
	}
}
