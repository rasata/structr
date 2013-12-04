package org.structr.core.app;

import java.util.List;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.Result;
import org.structr.core.graph.search.SearchAttribute;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;

/**
 *
 * @author Christian Morgner
 * @param <T>
 */
public interface Query<T extends GraphObject> {

	public Result<T> getResult() throws FrameworkException;
	public List<T> getAsList() throws FrameworkException;
	public T getFirst() throws FrameworkException;
	public int resultCount() throws FrameworkException;

	// ----- builder methods -----
	public Query<T> sort(final PropertyKey key);
	public Query<T> sortAscending(final PropertyKey key);
	public Query<T> sortDescending(final PropertyKey key);
	public Query<T> order(final boolean descending);
	public Query<T> pageSize(final int pageSize);
	public Query<T> page(final int page);
	public Query<T> publicOnly();
	public Query<T> includeDeletedAndHidden();
	public Query<T> publicOnly(final boolean publicOnly);
	public Query<T> includeDeletedAndHidden(final boolean publicOnly);
	public Query<T> offsetId(final String offsetId);
	public Query<T> uuid(final String uuid);
	public Query<T> type(final Class<T> type);
	public Query<T> types(final Class<T> type);
	public Query<T> types(final Class<T> type, final boolean inexact);
	
	public Query<T> andName(final String name);
	public Query<T> orName(final String name);

	public Query<T> location(final String street, final String postalCode, final String city, final String country, final double distance);
	public Query<T> location(final String street, final String postalCode, final String city, final String state, final String country, final double distance);
	public Query<T> location(final String street, final String house, final String postalCode, final String city, final String state, final String country, final double distance);
	public <P> Query<T> and(final PropertyKey<P> key, final P value);
	public <P> Query<T> and(final PropertyKey<P> key, final P value, final boolean inexact);
	public <P> Query<T> and(final PropertyMap attributes);
	public Query<T> and();
	public <P> Query<T> or(final PropertyKey<P> key, P value);
	public <P> Query<T> or(final PropertyMap attributes);
	public Query<T> or();
	public Query<T> not();
	
	public Query<T> parent();
	public Query<T> attributes(final List<SearchAttribute> attributes);
}