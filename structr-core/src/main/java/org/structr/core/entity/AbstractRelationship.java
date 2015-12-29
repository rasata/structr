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
package org.structr.core.entity;

//~--- classes ----------------------------------------------------------------

import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.chemistry.opencmis.commons.enums.PropertyType;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.PropertyContainer;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.index.Index;
import org.structr.cmis.CMISInfo;
import org.structr.common.GraphObjectComparator;
import org.structr.common.PermissionResolutionMask;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.common.error.IdNotFoundToken;
import org.structr.common.error.NullArgumentToken;
import org.structr.common.error.ReadOnlyPropertyToken;
import org.structr.core.GraphObject;
import static org.structr.core.GraphObject.base;
import static org.structr.core.GraphObject.id;
import static org.structr.core.GraphObject.type;
import org.structr.core.Services;
import org.structr.core.app.App;
import org.structr.core.app.StructrApp;
import org.structr.core.converter.PropertyConverter;
import org.structr.core.graph.NodeFactory;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.NodeService;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.parser.Functions;
import org.structr.core.property.IntProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.RelationshipTypeProperty;
import org.structr.core.property.SourceId;
import org.structr.core.property.SourceNodeProperty;
import org.structr.core.property.TargetId;
import org.structr.core.property.TargetNodeProperty;
import org.structr.core.script.Scripting;
import org.structr.schema.action.ActionContext;


/**
 * Abstract base class for all relationship entities in structr.
 *
 *
 * @param <S>
 * @param <T>
 */
public abstract class AbstractRelationship<S extends NodeInterface, T extends NodeInterface> implements Comparable<AbstractRelationship>, RelationshipInterface {

	private static final Logger logger = Logger.getLogger(AbstractRelationship.class.getName());

	public static final Property<Integer>       cascadeDelete              = new IntProperty("cascadeDelete");
	public static final Property<String>        relType                    = new RelationshipTypeProperty("relType");
	public static final SourceId                sourceId                   = new SourceId("sourceId");
	public static final TargetId                targetId                   = new TargetId("targetId");
	public static final Property<NodeInterface> sourceNodeProperty         = new SourceNodeProperty("sourceNode");
	public static final Property<NodeInterface> targetNodeProperty         = new TargetNodeProperty("targetNode");

	public static final View defaultView = new View(AbstractRelationship.class, PropertyView.Public,
		id, type, relType, sourceId, targetId
	);

	public static final View uiView = new View(AbstractRelationship.class, PropertyView.Ui,
		id, type, relType, sourceId, targetId
	);

	public static final View graphView = new View(AbstractRelationship.class, View.INTERNAL_GRAPH_VIEW,
		id, type, relType, sourceNodeProperty, targetNodeProperty
	);

	private boolean readOnlyPropertiesUnlocked = false;
	private String cachedEndNodeId             = null;
	private String cachedStartNodeId           = null;

	protected SecurityContext securityContext  = null;
	protected Relationship dbRelationship      = null;
	protected Class entityType                 = null;

	public AbstractRelationship() {}

	public AbstractRelationship(final SecurityContext securityContext, final Relationship dbRel, final Class entityType) {

		init(securityContext, dbRel, entityType);
	}

	@Override
	public final void init(final SecurityContext securityContext, final Relationship dbRel, final Class entityType) {

		this.dbRelationship  = dbRel;
		this.entityType      = entityType;
		this.securityContext = securityContext;
	}

	public Property<String> getSourceIdProperty() {
		return sourceId;
	}

	public Property<String> getTargetIdProperty() {
		return targetId;
	}

	@Override
	public void onRelationshipCreation() {
	}

	/**
	 * Called when a relationship of this combinedType is instantiated. Please note that
	 * a relationship can (and will) be instantiated several times during a
	 * normal rendering turn.
	 */
	@Override
	public void onRelationshipInstantiation() {

		try {

			if (dbRelationship != null) {

				Node startNode = dbRelationship.getStartNode();
				Node endNode   = dbRelationship.getEndNode();

				if ((startNode != null) && (endNode != null) && startNode.hasProperty(GraphObject.id.dbName()) && endNode.hasProperty(GraphObject.id.dbName())) {

					cachedStartNodeId = (String) startNode.getProperty(GraphObject.id.dbName());
					cachedEndNodeId   = (String) endNode.getProperty(GraphObject.id.dbName());

				}

			}

		} catch (Throwable t) {
		}
	}

	@Override
	public void onRelationshipDeletion() {
	}

	@Override
	public void setSecurityContext(final SecurityContext securityContext) {
		this.securityContext = securityContext;
	}

	@Override
	public SecurityContext getSecurityContext() {
		return this.securityContext;
	}

	@Override
	public void unlockReadOnlyPropertiesOnce() {

		this.readOnlyPropertiesUnlocked = true;

	}

	@Override
	public void removeProperty(final PropertyKey key) throws FrameworkException {

		dbRelationship.removeProperty(key.dbName());

		// remove from index
		removeFromIndex(key);
	}

	@Override
	public boolean equals(final Object o) {

		return (o != null && new Integer(this.hashCode()).equals(new Integer(o.hashCode())));

	}

	@Override
	public int hashCode() {

		if (this.dbRelationship == null) {

			return (super.hashCode());
		}

		return Long.valueOf(dbRelationship.getId()).hashCode();

	}

	@Override
	public int compareTo(final AbstractRelationship rel) {

		// TODO: implement finer compare methods, e.g. taking title and position into account
		if (rel == null) {

			return -1;
		}

		return ((Long) this.getId()).compareTo((Long) rel.getId());
	}

	@Override
	public int cascadeDelete() {

		Integer value = getProperty(AbstractRelationship.cascadeDelete);

		return value != null ? value : 0;
	}

	/**
	 * Indicates whether this relationship type propagates modifications
	 * in the given direction. Overwrite this method and return true for
	 * the desired direction to enable a callback on non-local node
	 * modification.
	 *
	 * @param direction the direction for which the propagation should is to be returned
	 * @return the propagation status for the given direction
	 */
	public boolean propagatesModifications(Direction direction) {
		return false;
	}

	//~--- get methods ----------------------------------------------------

	@Override
	public PropertyKey getDefaultSortKey() {

		return null;

	}

	@Override
	public String getDefaultSortOrder() {

		return GraphObjectComparator.ASCENDING;

	}

	@Override
	public long getId() {

		return getInternalId();

	}

	@Override
	public String getUuid() {
		return getProperty(AbstractRelationship.id);
	}

	public long getRelationshipId() {

		return getInternalId();

	}

	public long getInternalId() {

		return dbRelationship.getId();

	}

	@Override
	public PropertyMap getProperties() throws FrameworkException {

		Map<String, Object> properties = new LinkedHashMap<>();

		for (String key : dbRelationship.getPropertyKeys()) {

			properties.put(key, dbRelationship.getProperty(key));
		}

		// convert the database properties back to their java types
		return PropertyMap.databaseTypeToJavaType(securityContext, this, properties);

	}

	@Override
	public <T> T getProperty(final PropertyKey<T> key) {
		return getProperty(key, true, null);
	}

	@Override
	public <T> T getProperty(final PropertyKey<T> key, final org.neo4j.helpers.Predicate<GraphObject> predicate) {
		return getProperty(key, true, predicate);
	}

	private <T> T getProperty(final PropertyKey<T> key, boolean applyConverter, final org.neo4j.helpers.Predicate<GraphObject> predicate) {

		// early null check, this should not happen...
		if (key == null || key.dbName() == null) {
			return null;
		}

		return key.getProperty(securityContext, this, applyConverter, predicate);
	}

	@Override
	public <T> Comparable getComparableProperty(final PropertyKey<T> key) {

		if (key != null) {

			final T propertyValue = getProperty(key, false, null);	// get "raw" property without converter

			// check property converter
			PropertyConverter converter = key.databaseConverter(securityContext, this);
			if (converter != null) {

				try {
					return converter.convertForSorting(propertyValue);

				} catch(FrameworkException fex) {
					logger.log(Level.WARNING, "Unable to convert property {0} of type {1}: {2}", new Object[] {
						key.dbName(),
						getClass().getSimpleName(),
						fex.getMessage()
					});
				}
			}

			// conversion failed, may the property value itself is comparable
			if(propertyValue instanceof Comparable) {
				return (Comparable)propertyValue;
			}

			// last try: convertFromInput to String to make comparable
			if(propertyValue != null) {
				return propertyValue.toString();
			}
		}

		return null;
	}

	/**
	 * Return database relationship
	 *
	 * @return database relationship
	 */
	@Override
	public Relationship getRelationship() {

		return dbRelationship;

	}

	@Override
	public T getTargetNode() {
		NodeFactory<T> nodeFactory = new NodeFactory<>(securityContext);
		return nodeFactory.instantiate(dbRelationship.getEndNode());
	}

	@Override
	public T getTargetNodeAsSuperUser() {
		NodeFactory<T> nodeFactory = new NodeFactory<>(SecurityContext.getSuperUserInstance());
		return nodeFactory.instantiate(dbRelationship.getEndNode());
	}

	@Override
	public S getSourceNode() {
		NodeFactory<S> nodeFactory = new NodeFactory<>(securityContext);
		return nodeFactory.instantiate(dbRelationship.getStartNode());
	}

	@Override
	public S getSourceNodeAsSuperUser() {
		NodeFactory<S> nodeFactory = new NodeFactory<>(SecurityContext.getSuperUserInstance());
		return nodeFactory.instantiate(dbRelationship.getStartNode());
	}

	@Override
	public NodeInterface getOtherNode(final NodeInterface node) {
		NodeFactory nodeFactory = new NodeFactory(securityContext);
		return nodeFactory.instantiate(dbRelationship.getOtherNode(node.getNode()));
	}

	public NodeInterface getOtherNodeAsSuperUser(final NodeInterface node) {
		NodeFactory nodeFactory = new NodeFactory(SecurityContext.getSuperUserInstance());
		return nodeFactory.instantiate(dbRelationship.getOtherNode(node.getNode()));
	}

	@Override
	public RelationshipType getRelType() {

		if (dbRelationship != null) {

			return dbRelationship.getType();
		}

		return null;
	}

	/**
	 * Return all property keys.
	 *
	 * @return property keys
	 */
	public Iterable<PropertyKey> getPropertyKeys() {

		return getPropertyKeys(PropertyView.All);

	}

	/**
	 * Return property value which is used for indexing.
	 *
	 * This is useful f.e. to filter markup from HTML to index only text
	 *
	 * @param key
	 * @return property value for indexing
	 */
	@Override
	public Object getPropertyForIndexing(final PropertyKey key) {

		Object value = getProperty(key, false, null);
		if (value != null) {
			return value;
		}

		return getProperty(key);
	}

	// ----- interface GraphObject -----
	@Override
	public Iterable<PropertyKey> getPropertyKeys(final String propertyView) {

		return StructrApp.getConfiguration().getPropertySet(this.getClass(), propertyView);

	}

	public Map<RelationshipType, Long> getRelationshipInfo(Direction direction) {

		return null;

	}

	public List<AbstractRelationship> getRelationships(RelationshipType type, Direction dir) {

		return null;

	}

	@Override
	public String getType() {

		final RelationshipType relType = getRelType();
		if (relType != null) {
			return relType.name();
		}

		return null;
	}

	@Override
	public PropertyContainer getPropertyContainer() {
		return dbRelationship;
	}

	@Override
	public String getSourceNodeId() {
		return cachedStartNodeId;
	}

	@Override
	public String getTargetNodeId() {
		return cachedEndNodeId;

	}

	public String getOtherNodeId(final AbstractNode node) {

		return getOtherNode(node).getProperty(AbstractRelationship.id);

	}

	@Override
	public boolean onCreation(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {
		return isValid(errorBuffer);
	}

	@Override
	public boolean onModification(SecurityContext securityContext, ErrorBuffer errorBuffer) throws FrameworkException {
		return isValid(errorBuffer);
	}

	@Override
	public boolean onDeletion(SecurityContext securityContext, ErrorBuffer errorBuffer, PropertyMap properties) throws FrameworkException {
		return true;
	}

	@Override
	public void afterCreation(SecurityContext securityContext) {
	}

	@Override
	public void afterModification(SecurityContext securityContext) {
	}

	@Override
	public void afterDeletion(SecurityContext securityContext, PropertyMap properties) {
	}

	@Override
	public void ownerModified(SecurityContext securityContext) {
	}

	@Override
	public void securityModified(SecurityContext securityContext) {
	}

	@Override
	public void locationModified(SecurityContext securityContext) {
	}

	@Override
	public void propagatedModification(SecurityContext securityContext) {
	}

	public boolean isValid(ErrorBuffer errorBuffer) {

		boolean error = false;

		error |= ValidationHelper.checkStringNotBlank(this, AbstractRelationship.id, errorBuffer);

		return !error;

	}

	//~--- set methods ----------------------------------------------------

	public void setProperties(final PropertyMap properties) throws FrameworkException {

		for (Entry<PropertyKey, Object> prop : properties.entrySet()) {

			setProperty(prop.getKey(), prop.getValue());
		}

	}

	@Override
	public <T> Object setProperty(final PropertyKey<T> key, final T value) throws FrameworkException {

		if (key == null) {

			logger.log(Level.SEVERE, "Tried to set property with null key (action was denied)");

			throw new FrameworkException(422, new NullArgumentToken(getClass().getSimpleName(), base));

		}

		try {

			// check for read-only properties
			//if (StructrApp.getConfiguration().isReadOnlyProperty(type, key) || (StructrApp.getConfiguration().isWriteOnceProperty(type, key) && (dbRelationship != null) && dbRelationship.hasProperty(key.name()))) {
			if (key.isReadOnly() || (key.isWriteOnce() && (dbRelationship != null) && dbRelationship.hasProperty(key.dbName()))) {

				if (!readOnlyPropertiesUnlocked && !securityContext.isSuperUser()) {

					throw new FrameworkException(422, new ReadOnlyPropertyToken(getClass().getSimpleName(), key));

				}

			}

			return key.setProperty(securityContext, this, value);

		} finally {

			// unconditionally lock read-only properties after every write (attempt) to avoid security problems
			// since we made "unlock_readonly_properties_once" available through scripting
			this.readOnlyPropertiesUnlocked = false;

		}
	}

	@Override
	public void addToIndex() {

		for (PropertyKey key : StructrApp.getConfiguration().getPropertySet(entityType, PropertyView.All)) {

			if (key.isIndexed()) {

				key.index(this, this.getPropertyForIndexing(key));
			}
		}
	}

	@Override
	public void updateInIndex() {

		removeFromIndex();
		addToIndex();
	}

	@Override
	public void removeFromIndex() {

		for (Index<Relationship> index : Services.getInstance().getService(NodeService.class).getRelationshipIndices()) {
			index.remove(dbRelationship);
		}
	}

	public void removeFromIndex(PropertyKey key) {

		for (Index<Relationship> index : Services.getInstance().getService(NodeService.class).getRelationshipIndices()) {
			index.remove(dbRelationship, key.dbName());
		}
	}

	@Override
	public void indexPassiveProperties() {

		for (PropertyKey key : StructrApp.getConfiguration().getPropertySet(entityType, PropertyView.All)) {

			if (key.isPassivelyIndexed()) {

				key.index(this, this.getPropertyForIndexing(key));
			}
		}

	}

	@Override
	public void setSourceNodeId(final String startNodeId) throws FrameworkException {

		// Do nothing if new id equals old
		if (getSourceNodeId().equals(startNodeId)) {
			return;
		}

		final App app = StructrApp.getInstance(securityContext);

		final NodeInterface newStartNode = app.getNodeById(startNodeId);
		final NodeInterface endNode      = getTargetNode();
		final Class relationType         = getClass();
		final PropertyMap _props         = getProperties();
		final String type                = this.getClass().getSimpleName();

		if (newStartNode == null) {
			throw new FrameworkException(404, new IdNotFoundToken(type, startNodeId));
		}

		// delete this as the new rel will be the container afterwards
		app.delete(this);

		// create new relationship
		app.create(newStartNode, endNode, relationType, _props);
	}

	@Override
	public void setTargetNodeId(final String targetIdNode) throws FrameworkException {

		// Do nothing if new id equals old
		if (getTargetNodeId().equals(targetIdNode)) {
			return;
		}

		final App app = StructrApp.getInstance(securityContext);

		final NodeInterface newTargetNode = app.getNodeById(targetIdNode);
		final NodeInterface startNode     = getSourceNode();
		final Class relationType          = getClass();
		final PropertyMap _props          = getProperties();
		final String type                 = this.getClass().getSimpleName();

		if (newTargetNode == null) {
			throw new FrameworkException(404, new IdNotFoundToken(type, targetIdNode));
		}

		// delete this as the new rel will be the container afterwards
		app.delete(this);

		// create new relationship and store here
		app.create(startNode, newTargetNode, relationType, _props);
	}

	@Override
	public String getPropertyWithVariableReplacement(ActionContext renderContext, PropertyKey<String> key) throws FrameworkException {
		return Scripting.replaceVariables(renderContext, this, getProperty(key));
	}

	@Override
	public Object evaluate(final SecurityContext securityContext, final String key, final String defaultValue) throws FrameworkException {

		switch (key) {

			case "_source":
				return getSourceNode();

			case "_target":
				return getTargetNode();

			default:

				// evaluate object value or return default
				final Object value = getProperty(StructrApp.getConfiguration().getPropertyKeyForJSONName(entityType, key));
				if (value == null) {

					return Functions.numberOrString(defaultValue);
				}
				return value;
		}
	}

	@Override
	public Object invokeMethod(String methodName, Map<String, Object> parameters, final boolean throwException) throws FrameworkException {
		throw new UnsupportedOperationException("Invoking a method on a relationship is not supported at the moment.");
	}

	// ----- protected methods -----
	protected Direction getDirectionForType(final Class<S> sourceType, final Class<T> targetType, final Class<? extends NodeInterface> type) {

		if (sourceType.equals(type) && targetType.equals(type)) {
			return Direction.BOTH;
		}

		if (sourceType.equals(type)) {
			return Direction.OUTGOING;
		}

		if (targetType.equals(type)) {
			return Direction.INCOMING;
		}

		if (sourceType.isAssignableFrom(type)) {
			return Direction.OUTGOING;
		}

		if (targetType.isAssignableFrom(type)) {
			return Direction.INCOMING;
		}

		return Direction.BOTH;
	}

	@Override
	public CMISInfo getCMISInfo() {
		return null;
	}

	@Override
	public PermissionResolutionMask getPermissionResolutionMask() {
		// no control over relationship properties yet..
		return null;
	}

	// ----- Cloud synchronization and replication -----
	@Override
	public List<GraphObject> getSyncData() {
		return new ArrayList<>(); // provide a basis for super.getSyncData() calls
	}

	@Override
	public boolean isNode() {
		return false;
	}

	@Override
	public boolean isRelationship() {
		return true;
	}

	@Override
	public NodeInterface getSyncNode() {
		throw new ClassCastException(this.getClass() + " cannot be cast to org.structr.core.graph.NodeInterface");
	}

	@Override
	public RelationshipInterface getSyncRelationship() {
		return this;
	}

	@Override
	public void updateFromPropertyMap(final Map<String, Object> properties) throws FrameworkException {

		// update all properties that exist in the source map
		for (final Entry<String, Object> entry : properties.entrySet()) {
			getRelationship().setProperty(entry.getKey(), entry.getValue());
		}
	}

	// ----- CMIS support methods -----
	public String getCreatedBy() {
		return getProperty(AbstractNode.createdBy);
	}

	public String getLastModifiedBy() {
		return getProperty(AbstractNode.lastModifiedBy);
	}

	public GregorianCalendar getLastModificationDate() {

		final Date creationDate = getProperty(AbstractNode.lastModifiedDate);
		if (creationDate != null) {

			final GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(creationDate);

			return calendar;
		}

		return null;
	}

	public GregorianCalendar getCreationDate() {

		final Date creationDate = getProperty(AbstractNode.createdDate);
		if (creationDate != null) {

			final GregorianCalendar calendar = new GregorianCalendar();
			calendar.setTime(creationDate);

			return calendar;
		}

		return null;
	}

	public PropertyMap getDynamicProperties() {

		final PropertyMap propertyMap       = new PropertyMap();
		final Class type                    = getClass();

		for (final PropertyKey key : StructrApp.getConfiguration().getPropertySet(type, PropertyView.All)) {

			// include all dynamic keys in definition
			if (key.isDynamic() || key.isCMISProperty()) {

				// only include primitives here
				final PropertyType dataType = key.getDataType();
				if (dataType != null) {

					propertyMap.put(key, getProperty(key));
				}
			}
		}



		return propertyMap;
	}
}
