/**
 * Copyright (C) 2010-2015 Structr GmbH
 *
 * This file is part of Structr <http://structr.org>.
 *
 * Structr is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * Structr is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Structr.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.structr.core.property;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.neo4j.helpers.Predicate;
import org.structr.common.SecurityContext;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.script.Scripting;
import org.structr.schema.action.ActionContext;

/**
 *
 *
 */
public class FunctionProperty<T> extends Property<T> {

	private static final Logger logger = Logger.getLogger(FunctionProperty.class.getName());

	public FunctionProperty(final String name) {
		super(name);
	}

	@Override
	public Class relatedType() {
		return null;
	}

	@Override
	public T getProperty(final SecurityContext securityContext, final GraphObject obj, final boolean applyConverter) {
		return getProperty(securityContext, obj, applyConverter, null);
	}

	@Override
	public T getProperty(final SecurityContext securityContext, final GraphObject obj, final boolean applyConverter, final Predicate<GraphObject> predicate) {


		try {

			if (obj != null && readFunction != null) {

				return (T)Scripting.evaluate(new ActionContext(securityContext), obj, "${".concat(readFunction).concat("}"));

			} else {

				logger.log(Level.WARNING, "Unable to evaluate function property {0}, object was null.", jsonName());
			}

		} catch (Throwable t) {

			logger.log(Level.WARNING, "Exception while evaluating read function in Function property {0}.", jsonName());

			t.printStackTrace();
		}

		return null;
	}

	@Override
	public boolean isCollection() {
		return false;
	}

	@Override
	public Integer getSortType() {
		return null; // use string search
	}

	@Override
	public Class valueType() {
		return Object.class;
	}

	@Override
	public Object fixDatabaseProperty(Object value) {
		return value;
	}

	@Override
	public Object getValueForEmptyFields() {
		return null;
	}

	@Override
	public String typeName() {
		return "Object";
	}

	@Override
	public PropertyConverter<T, ?> databaseConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public PropertyConverter<T, ?> databaseConverter(SecurityContext securityContext, GraphObject entity) {
		return null;
	}

	@Override
	public PropertyConverter<?, T> inputConverter(SecurityContext securityContext) {
		return null;
	}

	@Override
	public Object setProperty(SecurityContext securityContext, GraphObject obj, T value) throws FrameworkException {

		try {
			final ActionContext ctx = new ActionContext(securityContext);

			ctx.setConstant("value", value);

			return (T)Scripting.evaluate(ctx, obj, "${".concat(writeFunction).concat("}"));

		} catch (Throwable t) {

			logger.log(Level.WARNING, "Exception while evaluating write function in Function property '{0}'.", new Object[] { jsonName() });

			t.printStackTrace();
		}

		return null;
	}
}
