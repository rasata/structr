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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.neo4j.helpers.Predicate;
import org.neo4j.helpers.collection.Iterables;
import org.structr.common.CaseHelper;
import org.structr.common.PermissionPropagation;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.ValidationHelper;
import org.structr.common.View;
import org.structr.common.error.ErrorBuffer;
import org.structr.common.error.FrameworkException;
import org.structr.core.GraphObject;
import org.structr.core.entity.relationship.Ownership;
import org.structr.core.entity.relationship.SchemaRelationshipSourceNode;
import org.structr.core.entity.relationship.SchemaRelationshipTargetNode;
import org.structr.core.graph.TransactionCommand;
import org.structr.core.notion.PropertyNotion;
import org.structr.core.property.EndNode;
import org.structr.core.property.EntityNotionProperty;
import org.structr.core.property.EnumProperty;
import org.structr.core.property.LongProperty;
import org.structr.core.property.Property;
import org.structr.core.property.PropertyKey;
import org.structr.core.property.PropertyMap;
import org.structr.core.property.StartNode;
import org.structr.core.property.StringProperty;
import org.structr.schema.ReloadSchema;
import org.structr.schema.SchemaHelper;
import org.structr.schema.SchemaHelper.Type;
import org.structr.schema.action.ActionEntry;
import org.structr.schema.action.Actions;
import org.structr.schema.json.JsonSchema;
import org.structr.schema.json.JsonSchema.Cascade;
import org.structr.schema.parser.Validator;

/**
 *
 *
 */
public class SchemaRelationshipNode extends AbstractSchemaNode {

	private static final Logger logger                              = Logger.getLogger(SchemaRelationshipNode.class.getName());
	private static final Set<String> propagatingRelTypes            = new TreeSet<>();
	private static final Pattern ValidKeyPattern                    = Pattern.compile("[a-zA-Z_]+");

	public static final Property<SchemaNode> sourceNode             = new StartNode<>("sourceNode", SchemaRelationshipSourceNode.class);
	public static final Property<SchemaNode> targetNode             = new EndNode<>("targetNode", SchemaRelationshipTargetNode.class);
	public static final Property<String>     sourceId               = new EntityNotionProperty<>("sourceId", sourceNode, new PropertyNotion(GraphObject.id));
	public static final Property<String>     targetId               = new EntityNotionProperty<>("targetId", targetNode, new PropertyNotion(GraphObject.id));
	public static final Property<String>     name                   = new StringProperty("name").indexed();
	public static final Property<String>     relationshipType       = new StringProperty("relationshipType").indexed();
	public static final Property<String>     sourceMultiplicity     = new StringProperty("sourceMultiplicity");
	public static final Property<String>     targetMultiplicity     = new StringProperty("targetMultiplicity");
	public static final Property<String>     sourceNotion           = new StringProperty("sourceNotion");
	public static final Property<String>     targetNotion           = new StringProperty("targetNotion");
	public static final Property<String>     sourceJsonName         = new StringProperty("sourceJsonName");
	public static final Property<String>     targetJsonName         = new StringProperty("targetJsonName");
	public static final Property<String>     previousSourceJsonName = new StringProperty("oldSourceJsonName");
	public static final Property<String>     previousTargetJsonName = new StringProperty("oldTargetJsonName");
	public static final Property<String>     extendsClass           = new StringProperty("extendsClass").indexed();
	public static final Property<Long>       cascadingDeleteFlag    = new LongProperty("cascadingDeleteFlag");
	public static final Property<Long>       autocreationFlag       = new LongProperty("autocreationFlag");


	public enum Propagation {
		Add, Keep, Remove
	}

	public enum Direction {
		None, In, Out, Both
	}

	// permission propagation via domain relationships
	public static final Property<Direction>   permissionPropagation    = new EnumProperty("permissionPropagation", Direction.class, Direction.None);
	public static final Property<Propagation> readPropagation          = new EnumProperty<>("readPropagation", Propagation.class, Propagation.Remove);
	public static final Property<Propagation> writePropagation         = new EnumProperty<>("writePropagation", Propagation.class, Propagation.Remove);
	public static final Property<Propagation> deletePropagation        = new EnumProperty<>("deletePropagation", Propagation.class, Propagation.Remove);
	public static final Property<Propagation> accessControlPropagation = new EnumProperty<>("accessControlPropagation", Propagation.class, Propagation.Remove);
	public static final Property<String>      propertyMask             = new StringProperty("propertyMask");

	public static final View defaultView = new View(SchemaRelationshipNode.class, PropertyView.Public,
		name, sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag, previousSourceJsonName, previousTargetJsonName,
		permissionPropagation, readPropagation, writePropagation, deletePropagation, accessControlPropagation, propertyMask
	);

	public static final View uiView = new View(SchemaRelationshipNode.class, PropertyView.Ui,
		name, sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag,  permissionPropagation,
		readPropagation, writePropagation, deletePropagation, accessControlPropagation, propertyMask
	);

	public static final View exportView = new View(SchemaMethod.class, "export",
		sourceId, targetId, sourceMultiplicity, targetMultiplicity, sourceNotion, targetNotion, relationshipType,
		sourceJsonName, targetJsonName, extendsClass, cascadingDeleteFlag, autocreationFlag, permissionPropagation,
		propertyMask
	);

	private final Set<String> dynamicViews = new LinkedHashSet<>();


	public static void registerPropagatingRelationshipType(final String type) {
		propagatingRelTypes.add(type);
	}

	public static void clearPropagatingRelationshipTypes() {
		propagatingRelTypes.clear();
	}

	public static Set<String> getPropagatingRelationshipTypes() {
		return propagatingRelTypes;
	}


	@Override
	public Iterable<PropertyKey> getPropertyKeys(final String propertyView) {

		final Set<PropertyKey> propertyKeys = new LinkedHashSet<>(Iterables.toList(super.getPropertyKeys(propertyView)));

		// add "custom" property keys as String properties
		for (final String key : SchemaHelper.getProperties(getNode())) {

			final PropertyKey newKey = new StringProperty(key);
			newKey.setDeclaringClass(getClass());

			propertyKeys.add(newKey);
		}

		return propertyKeys;
	}

	@Override
	public boolean isValid(final ErrorBuffer errorBuffer) {

		boolean error = false;

		error |= ValidationHelper.checkStringNotBlank(this, relationshipType, errorBuffer);

		return !error && super.isValid(errorBuffer);
	}

	@Override
	public boolean onCreation(SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onCreation(securityContext, errorBuffer)) {

			// store old property names
			setProperty(previousSourceJsonName, getProperty(sourceJsonName));
			setProperty(previousTargetJsonName, getProperty(targetJsonName));

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("reloadSchema", new ReloadSchema());

			return true;
		}

		return false;
	}

	@Override
	public boolean onModification(SecurityContext securityContext, final ErrorBuffer errorBuffer) throws FrameworkException {

		if (super.onModification(securityContext, errorBuffer)) {

			checkClassName();

			checkAndRenameSourceAndTargetJsonNames();

			// store old property names
			setProperty(previousSourceJsonName, getProperty(sourceJsonName));
			setProperty(previousTargetJsonName, getProperty(targetJsonName));

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("reloadSchema", new ReloadSchema());

			return true;
		}

		return false;
	}

	@Override
	public boolean onDeletion(SecurityContext securityContext, ErrorBuffer errorBuffer, PropertyMap properties) throws FrameworkException {

		if (super.onDeletion(securityContext, errorBuffer, properties)) {

			removeSourceAndTargetJsonNames(properties);

			// register transaction post processing that recreates the schema information
			TransactionCommand.postProcess("reloadSchema", new ReloadSchema());

			return true;

		}

		return false;

	}

	public SchemaNode getSourceNode() {
		return getProperty(sourceNode);
	}

	public SchemaNode getTargetNode() {
		return getProperty(targetNode);
	}

	// ----- interface Schema -----
	@Override
	public String getClassName() {

		String name = getProperty(AbstractNode.name);
		if (name == null) {

			final String _sourceType = getSchemaNodeSourceType();
			final String _targetType = getSchemaNodeTargetType();
			final String _relType    = SchemaHelper.cleanPropertyName(getRelationshipType());

			name = _sourceType + _relType + _targetType;

			try {
				setProperty(AbstractNode.name, name);

			} catch (FrameworkException fex) {
				logger.log(Level.WARNING, "Unable to set relationship name to {0}.", name);
			}
		}

		return name;
	}

	@Override
	public String getMultiplicity(String propertyNameToCheck) {
		return null;
	}

	@Override
	public String getRelatedType(String propertyNameToCheck) {
		return null;
	}

	public String getPropertySource(final String propertyName, final boolean outgoing) {
		return getPropertySource(propertyName, outgoing, false);
	}

	public String getPropertySource(final String propertyName, final boolean outgoing, final boolean newStatementOnly) {

		final StringBuilder buf          = new StringBuilder();
		final String _sourceMultiplicity = getProperty(sourceMultiplicity);
		final String _targetMultiplicity = getProperty(targetMultiplicity);
		final String _sourceNotion       = getProperty(sourceNotion);
		final String _targetNotion       = getProperty(targetNotion);
		final String _sourceType         = getSchemaNodeSourceType();
		final String _targetType         = getSchemaNodeTargetType();
		final String _className          = getClassName();

		if (outgoing) {

			if ("1".equals(_targetMultiplicity)) {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<").append(_targetType).append("> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new EndNode<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_sourceType, _targetNotion));
				buf.append(newStatementOnly ? ")" : ").dynamic();\n");

			} else {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<java.util.List<").append(_targetType).append(">> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new EndNodes<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_sourceType, _targetNotion));
				buf.append(newStatementOnly ? ")" : ").dynamic();\n");
			}

		} else {

			if ("1".equals(_sourceMultiplicity)) {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<").append(_sourceType).append("> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new StartNode<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_targetType, _sourceNotion));
				buf.append(newStatementOnly ? ")" : ").dynamic();\n");

			} else {

				if (!newStatementOnly) {

					buf.append("\tpublic static final Property<java.util.List<").append(_sourceType).append(">> ").append(SchemaHelper.cleanPropertyName(propertyName)).append("Property");
					buf.append(" = ");
				}
				buf.append("new StartNodes<>(\"").append(propertyName).append("\", ").append(_className).append(".class");
				buf.append(getNotion(_targetType, _sourceNotion));
				buf.append(newStatementOnly ? ")" : ").dynamic();\n");
			}
		}

		return buf.toString();
	}

	public String getMultiplicity(final boolean outgoing) {

		if (outgoing) {

			return getProperty(targetMultiplicity);

		} else {

			return getProperty(sourceMultiplicity);
		}
	}

	public String getPropertyName(final String relatedClassName, final Set<String> existingPropertyNames, final boolean outgoing) {

		final String relationshipTypeName = getProperty(SchemaRelationshipNode.relationshipType).toLowerCase();
		final String _sourceType          = getSchemaNodeSourceType();
		final String _targetType          = getSchemaNodeTargetType();
		final String _targetJsonName      = getProperty(targetJsonName);
		final String _targetMultiplicity  = getProperty(targetMultiplicity);
		final String _sourceJsonName      = getProperty(sourceJsonName);
		final String _sourceMultiplicity  = getProperty(sourceMultiplicity);

		final String propertyName = SchemaRelationshipNode.getPropertyName(relatedClassName, existingPropertyNames, outgoing, relationshipTypeName, _sourceType, _targetType, _targetJsonName, _targetMultiplicity, _sourceJsonName, _sourceMultiplicity);

		try {
			if (outgoing) {

				if (_targetJsonName == null) {

					setProperty(previousTargetJsonName, propertyName);
				}

			} else {

				if (_sourceJsonName == null) {

					setProperty(previousSourceJsonName, propertyName);
				}
			}

		} catch (FrameworkException fex) {

			fex.printStackTrace();
		}

		return propertyName;
	}

	public static String getPropertyName(final String relatedClassName, final Set<String> existingPropertyNames, final boolean outgoing, final String relationshipTypeName, final String _sourceType, final String _targetType, final String _targetJsonName, final String _targetMultiplicity, final String _sourceJsonName, final String _sourceMultiplicity) {

		String propertyName = "";

		if (outgoing) {


			if (_targetJsonName != null) {

				// FIXME: no automatic creation?
				propertyName = _targetJsonName;

			} else {

				if ("1".equals(_targetMultiplicity)) {

					propertyName = CaseHelper.toLowerCamelCase(relationshipTypeName) + CaseHelper.toUpperCamelCase(_targetType);

				} else {

					propertyName = CaseHelper.plural(CaseHelper.toLowerCamelCase(relationshipTypeName) + CaseHelper.toUpperCamelCase(_targetType));
				}
			}

		} else {


			if (_sourceJsonName != null) {
				propertyName = _sourceJsonName;
			} else {

				if ("1".equals(_sourceMultiplicity)) {

					propertyName = CaseHelper.toLowerCamelCase(_sourceType) + CaseHelper.toUpperCamelCase(relationshipTypeName);

				} else {

					propertyName = CaseHelper.plural(CaseHelper.toLowerCamelCase(_sourceType) + CaseHelper.toUpperCamelCase(relationshipTypeName));
				}
			}
		}

		if (existingPropertyNames.contains(propertyName)) {

			// First level: Add direction suffix
			propertyName += outgoing ? "Out" : "In";
			int i=0;

			// New name still exists: Add number
			while (existingPropertyNames.contains(propertyName)) {
				propertyName += ++i;
			}

		}

		existingPropertyNames.add(propertyName);

		return propertyName;
	}

	@Override
	public String getSource(final ErrorBuffer errorBuffer) throws FrameworkException {

		final Map<Actions.Type, List<ActionEntry>> actions = new LinkedHashMap<>();
		final Map<String, Set<String>> viewProperties      = new LinkedHashMap<>();
		final StringBuilder src                            = new StringBuilder();
		final Class baseType                               = AbstractRelationship.class;
		final String _className                            = getClassName();
		final String _sourceNodeType                       = getSchemaNodeSourceType();
		final String _targetNodeType                       = getSchemaNodeTargetType();
		final Set<String> propertyNames                    = new LinkedHashSet<>();
		final Set<Validator> validators                    = new LinkedHashSet<>();
		final Set<String> enums                            = new LinkedHashSet<>();
		final Set<String> interfaces                       = new LinkedHashSet<>();

		src.append("package org.structr.dynamic;\n\n");

		SchemaHelper.formatImportStatements(src, baseType);

		src.append("public class ").append(_className).append(" extends ").append(getBaseType());

		if ("OWNS".equals(getProperty(relationshipType))) {
			interfaces.add(Ownership.class.getName());
		}

		if (!Direction.None.equals(getProperty(permissionPropagation))) {
			interfaces.add(PermissionPropagation.class.getName());
		}

		// append interfaces if present
		if (!interfaces.isEmpty()) {

			src.append(" implements ");

			for (final Iterator<String> it = interfaces.iterator(); it.hasNext();) {

				src.append(it.next());

				if (it.hasNext()) {
					src.append(", ");
				}
			}
		}

		src.append(" {\n\n");

		if (!Direction.None.equals(getProperty(permissionPropagation))) {
			src.append("\tstatic {\n\t\tSchemaRelationshipNode.registerPropagatingRelationshipType(\"").append(getRelationshipType()).append("\");\n\t}\n\n");
		}

		src.append(SchemaHelper.extractProperties(this, propertyNames, validators, enums, viewProperties, errorBuffer));
		src.append(SchemaHelper.extractViews(this, viewProperties, errorBuffer));
		src.append(SchemaHelper.extractMethods(this, actions));

		// source and target id properties
		src.append("\tpublic static final Property<java.lang.String> sourceIdProperty = new SourceId(\"sourceId\");\n");
		src.append("\tpublic static final Property<java.lang.String> targetIdProperty = new TargetId(\"targetId\");\n");

		// add sourceId and targetId to view properties
		//SchemaHelper.addPropertyToView(PropertyView.Public, "sourceId", viewProperties);
		//SchemaHelper.addPropertyToView(PropertyView.Public, "targetId", viewProperties);

		SchemaHelper.addPropertyToView(PropertyView.Ui, "sourceId", viewProperties);
		SchemaHelper.addPropertyToView(PropertyView.Ui, "targetId", viewProperties);

		// output possible enum definitions
		for (final String enumDefition : enums) {
			src.append(enumDefition);
		}

		for (Map.Entry<String, Set<String>> entry :viewProperties.entrySet()) {

			final String viewName  = entry.getKey();
			final Set<String> view = entry.getValue();

			if (!view.isEmpty()) {
				dynamicViews.add(viewName);
				SchemaHelper.formatView(src, _className, viewName, viewName, view);
			}
		}

		// abstract method implementations
		src.append("\n\t@Override\n");
		src.append("\tpublic Class<").append(_sourceNodeType).append("> getSourceType() {\n");
		src.append("\t\treturn ").append(_sourceNodeType).append(".class;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic Class<").append(_targetNodeType).append("> getTargetType() {\n");
		src.append("\t\treturn ").append(_targetNodeType).append(".class;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic Property<java.lang.String> getSourceIdProperty() {\n");
		src.append("\t\treturn sourceId;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic Property<java.lang.String> getTargetIdProperty() {\n");
		src.append("\t\treturn targetId;\n");
		src.append("\t}\n\n");
		src.append("\t@Override\n");
		src.append("\tpublic java.lang.String name() {\n");
		src.append("\t\treturn \"").append(getRelationshipType()).append("\";\n");
		src.append("\t}\n\n");

		SchemaHelper.formatValidators(src, validators);
		SchemaHelper.formatSaveActions(src, actions);

		formatRelationshipFlags(src);

		formatPermissionPropagation(src);

		src.append("}\n");

		return src.toString();
	}

	@Override
	public Set<String> getViews() {
		return dynamicViews;
	}

	@Override
	public String getAuxiliarySource() throws FrameworkException {

		final Set<String> existingPropertyNames = new LinkedHashSet<>();
		final String sourceNodeType             = getSchemaNodeSourceType();
		final String targetNodeType             = getSchemaNodeTargetType();
		final StringBuilder src                 = new StringBuilder();
		final String _className                 = getClassName();
		final Class baseType                    = AbstractRelationship.class;

		if (!"File".equals(sourceNodeType) && !"File".equals(targetNodeType)) {
			return null;
		}

		src.append("package org.structr.dynamic;\n\n");

		SchemaHelper.formatImportStatements(src, baseType);

		src.append("public class _").append(_className).append("Helper {\n\n");

		src.append("\n\tstatic {\n\n");
		src.append("\t\tfinal PropertyKey outKey = ");
		src.append(getPropertySource(getPropertyName(sourceNodeType, existingPropertyNames, true), true, true));
		src.append(";\n");
		src.append("\t\toutKey.setDeclaringClass(").append(sourceNodeType).append(".class);\n\n");
		src.append("\t\tfinal PropertyKey inKey = ");
		src.append(getPropertySource(getPropertyName(targetNodeType, existingPropertyNames, false), false, true));
		src.append(";\n");
		src.append("\t\tinKey.setDeclaringClass(").append(targetNodeType).append(".class);\n\n");
		src.append("\t\tStructrApp.getConfiguration().registerDynamicProperty(");
		src.append(sourceNodeType).append(".class, outKey);\n");
		src.append("\t\tStructrApp.getConfiguration().registerDynamicProperty(");
		src.append(targetNodeType).append(".class, inKey);\n\n");
		src.append("\t\tStructrApp.getConfiguration().registerPropertySet(").append(sourceNodeType).append(".class, PropertyView.Ui, outKey);\n");
		src.append("\t\tStructrApp.getConfiguration().registerPropertySet(").append(targetNodeType).append(".class, PropertyView.Ui, inKey);\n");
		src.append("\t}\n");
		src.append("}\n");

		return src.toString();

//		return null;
	}

	// ----- public methods -----
	public String getSchemaNodeSourceType() {

		final SchemaNode sourceNode = getSourceNode();
		if (sourceNode != null) {

			return sourceNode.getProperty(SchemaNode.name);
		}

		return null;
	}

	public String getSchemaNodeTargetType() {

		final SchemaNode targetNode = getTargetNode();
		if (targetNode != null) {

			return targetNode.getProperty(SchemaNode.name);
		}

		return null;
	}

	@Override
	public String getResourceSignature() {

		final String _sourceType = getSchemaNodeSourceType();
		final String _targetType = getSchemaNodeTargetType();

		return _sourceType + "/" + _targetType;
	}

	public String getInverseResourceSignature() {

		final String _sourceType = getSchemaNodeSourceType();
		final String _targetType = getSchemaNodeTargetType();

		return _targetType + "/" + _sourceType;
	}

	// ----- private methods -----
	private String getRelationshipType() {

		String relType = getProperty(relationshipType);
		if (relType == null) {

			final String _sourceType = getSchemaNodeSourceType().toUpperCase();
			final String _targetType = getSchemaNodeTargetType().toUpperCase();

			relType = _sourceType + "_" + _targetType;
		}

		return relType;
	}
	private String getNotion(final String _className, final String notionSource) {

		final StringBuilder buf = new StringBuilder();

		if (StringUtils.isNotBlank(notionSource)) {

			final Set<String> keys = new LinkedHashSet<>(Arrays.asList(notionSource.split("[\\s,]+")));
			if (!keys.isEmpty()) {

				if (keys.size() == 1) {

					String key     = keys.iterator().next();
					boolean create = key.startsWith("+");

					if (create) {
						key = key.substring(1);
					}

					if (ValidKeyPattern.matcher(key).matches()) {

						buf.append(", new PropertyNotion(");
						buf.append(getNotionKey(_className, key));
						buf.append(", ").append(create);
						buf.append(")");

					} else {

						logger.log(Level.WARNING, "Invalid key name {0} for notion.", key);
					}

				} else {

					buf.append(", new PropertySetNotion(");

					// use only matching keys
					for (final Iterator<String> it = Iterables.filter(new KeyMatcher(), keys).iterator(); it.hasNext();) {

						buf.append(getNotionKey(_className, it.next()));

						if (it.hasNext()) {
							buf.append(", ");
						}
					}

					buf.append(")");
				}
			}
		}

		return buf.toString();
	}

	private String getNotionKey(final String _className, final String key) {
		return _className + "." + key;
	}

	private String getBaseType() {

		final String _sourceMultiplicity = getProperty(sourceMultiplicity);
		final String _targetMultiplicity = getProperty(targetMultiplicity);
		final String _sourceType         = getSchemaNodeSourceType();
		final String _targetType         = getSchemaNodeTargetType();
		final StringBuilder buf          = new StringBuilder();

		if ("1".equals(_sourceMultiplicity)) {

			if ("1".equals(_targetMultiplicity)) {

				buf.append("OneToOne");

			} else {

				buf.append("OneToMany");
			}

		} else {

			if ("1".equals(_targetMultiplicity)) {

				buf.append("ManyToOne");

			} else {

				buf.append("ManyToMany");
			}
		}

		buf.append("<");
		buf.append(_sourceType);
		buf.append(", ");
		buf.append(_targetType);
		buf.append(">");

		return buf.toString();
	}

	public void resolveCascadingEnums(final Cascade delete, final Cascade autoCreate) throws FrameworkException {

		if (delete != null) {

			switch (delete) {

				case sourceToTarget:
					setProperty(SchemaRelationshipNode.cascadingDeleteFlag, Long.valueOf(Relation.SOURCE_TO_TARGET));
					break;

				case targetToSource:
					setProperty(SchemaRelationshipNode.cascadingDeleteFlag, Long.valueOf(Relation.TARGET_TO_SOURCE));
					break;

				case always:
					setProperty(SchemaRelationshipNode.cascadingDeleteFlag, Long.valueOf(Relation.ALWAYS));
					break;

				case constraintBased:
					setProperty(SchemaRelationshipNode.cascadingDeleteFlag, Long.valueOf(Relation.CONSTRAINT_BASED));
					break;
			}
		}

		if (autoCreate != null) {

			switch (autoCreate) {

				case sourceToTarget:
					setProperty(SchemaRelationshipNode.autocreationFlag, Long.valueOf(Relation.SOURCE_TO_TARGET));
					break;

				case targetToSource:
					setProperty(SchemaRelationshipNode.autocreationFlag, Long.valueOf(Relation.TARGET_TO_SOURCE));
					break;

				case always:
					setProperty(SchemaRelationshipNode.autocreationFlag, Long.valueOf(Relation.ALWAYS));
					break;

				case constraintBased:
					setProperty(SchemaRelationshipNode.autocreationFlag, Long.valueOf(Relation.CONSTRAINT_BASED));
					break;
			}
		}

	}

	public Map<String, Object> resolveCascadingFlags() {

		final Long cascadingDelete        = getProperty(SchemaRelationshipNode.cascadingDeleteFlag);
		final Long autoCreate             = getProperty(SchemaRelationshipNode.autocreationFlag);
		final Map<String, Object> cascade = new TreeMap<>();

		if (cascadingDelete != null) {

			switch (cascadingDelete.intValue()) {

				case Relation.SOURCE_TO_TARGET:
					cascade.put(JsonSchema.KEY_DELETE, JsonSchema.Cascade.sourceToTarget.name());
					break;

				case Relation.TARGET_TO_SOURCE:
					cascade.put(JsonSchema.KEY_DELETE, JsonSchema.Cascade.targetToSource.name());
					break;

				case Relation.ALWAYS:
					cascade.put(JsonSchema.KEY_DELETE, JsonSchema.Cascade.always.name());
					break;

				case Relation.CONSTRAINT_BASED:
					cascade.put(JsonSchema.KEY_DELETE, JsonSchema.Cascade.constraintBased.name());
					break;
			}
		}

		if (autoCreate != null) {

			switch (autoCreate.intValue()) {

				case Relation.SOURCE_TO_TARGET:
					cascade.put(JsonSchema.KEY_CREATE, JsonSchema.Cascade.sourceToTarget.name());
					break;

				case Relation.TARGET_TO_SOURCE:
					cascade.put(JsonSchema.KEY_CREATE, JsonSchema.Cascade.targetToSource.name());
					break;

				case Relation.ALWAYS:
					cascade.put(JsonSchema.KEY_CREATE, JsonSchema.Cascade.always.name());
					break;

				case Relation.CONSTRAINT_BASED:
					cascade.put(JsonSchema.KEY_CREATE, JsonSchema.Cascade.constraintBased.name());
					break;
			}
		}

		return cascade;
	}

	// ----- interface Syncable -----
	@Override
	public List<GraphObject> getSyncData() throws FrameworkException {

		final List<GraphObject> syncables = super.getSyncData();

		syncables.add(getSourceNode());
		syncables.add(getTargetNode());

		return syncables;
	}

	// ----- private methods -----
	private void formatRelationshipFlags(final StringBuilder src) {

		Long cascadingDelete = getProperty(cascadingDeleteFlag);
		if (cascadingDelete != null) {

			src.append("\n\t@Override\n");
			src.append("\tpublic int getCascadingDeleteFlag() {\n");

			switch (cascadingDelete.intValue()) {

				case Relation.ALWAYS :
					src.append("\t\treturn Relation.ALWAYS;\n");
					break;

				case Relation.CONSTRAINT_BASED :
					src.append("\t\treturn Relation.CONSTRAINT_BASED;\n");
					break;

				case Relation.SOURCE_TO_TARGET :
					src.append("\t\treturn Relation.SOURCE_TO_TARGET;\n");
					break;

				case Relation.TARGET_TO_SOURCE :
					src.append("\t\treturn Relation.TARGET_TO_SOURCE;\n");
					break;

				case Relation.NONE :

				default :
					src.append("\t\treturn Relation.NONE;\n");

			}

			src.append("\t}\n\n");
		}

		Long autocreate = getProperty(autocreationFlag);
		if (autocreate != null) {

			src.append("\n\t@Override\n");
			src.append("\tpublic int getAutocreationFlag() {\n");

			switch (autocreate.intValue()) {

				case Relation.ALWAYS :
					src.append("\t\treturn Relation.ALWAYS;\n");
					break;

				case Relation.SOURCE_TO_TARGET :
					src.append("\t\treturn Relation.SOURCE_TO_TARGET;\n");
					break;

				case Relation.TARGET_TO_SOURCE :
					src.append("\t\treturn Relation.TARGET_TO_SOURCE;\n");
					break;

				default :
					src.append("\t\treturn Relation.NONE;\n");

			}

			src.append("\t}\n\n");
		}
	}

	private void formatPermissionPropagation(final StringBuilder buf) {

		if (!Direction.None.equals(getProperty(permissionPropagation))) {

			buf.append("\n\t@Override\n");
			buf.append("\tpublic SchemaRelationshipNode.Direction getPropagationDirection() {\n");
			buf.append("\t\treturn SchemaRelationshipNode.Direction.").append(getProperty(permissionPropagation)).append(";\n");
			buf.append("\t}\n\n");


			buf.append("\n\t@Override\n");
			buf.append("\tpublic SchemaRelationshipNode.Propagation getReadPropagation() {\n");
			buf.append("\t\treturn SchemaRelationshipNode.Propagation.").append(getProperty(readPropagation)).append(";\n");
			buf.append("\t}\n\n");


			buf.append("\n\t@Override\n");
			buf.append("\tpublic SchemaRelationshipNode.Propagation getWritePropagation() {\n");
			buf.append("\t\treturn SchemaRelationshipNode.Propagation.").append(getProperty(writePropagation)).append(";\n");
			buf.append("\t}\n\n");


			buf.append("\n\t@Override\n");
			buf.append("\tpublic SchemaRelationshipNode.Propagation getDeletePropagation() {\n");
			buf.append("\t\treturn SchemaRelationshipNode.Propagation.").append(getProperty(deletePropagation)).append(";\n");
			buf.append("\t}\n\n");


			buf.append("\n\t@Override\n");
			buf.append("\tpublic SchemaRelationshipNode.Propagation getAccessControlPropagation() {\n");
			buf.append("\t\treturn SchemaRelationshipNode.Propagation.").append(getProperty(accessControlPropagation)).append(";\n");
			buf.append("\t}\n\n");


			buf.append("\n\t@Override\n");
			buf.append("\tpublic String getDeltaProperties() {\n");
			final String _propertyMask = getProperty(propertyMask);
			if (_propertyMask != null) {

				buf.append("\t\treturn \"").append(_propertyMask).append("\";\n");

			} else {

				buf.append("\t\treturn null;\n");
			}

			buf.append("\t}\n\n");
		}
	}

	private void checkClassName() throws FrameworkException {

		final String className = getClassName();
		final String potentialNewClassName = assembleNewClassName();

		if (!className.equals(potentialNewClassName)) {

			try {
				setProperty(AbstractNode.name, potentialNewClassName);

			} catch (FrameworkException fex) {
				logger.log(Level.WARNING, "Unable to set relationship name to {0}.", potentialNewClassName);
			}
		}
	}

	private String assembleNewClassName() {

		final String _sourceType = getSchemaNodeSourceType();
		final String _targetType = getSchemaNodeTargetType();
		final String _relType    = SchemaHelper.cleanPropertyName(getRelationshipType());

		return _sourceType + _relType + _targetType;
	}

	private void checkAndRenameSourceAndTargetJsonNames() throws FrameworkException {

		final String _previousSourceJsonName = getProperty(previousSourceJsonName);
		final String _previousTargetJsonName = getProperty(previousTargetJsonName);
		final String _currentSourceJsonName  = ((getProperty(sourceJsonName) != null) ? getProperty(sourceJsonName) : getPropertyName(getSchemaNodeTargetType(), new LinkedHashSet<>(), false));
		final String _currentTargetJsonName  = ((getProperty(targetJsonName) != null) ? getProperty(targetJsonName) : getPropertyName(getSchemaNodeSourceType(), new LinkedHashSet<>(), true));
		final SchemaNode _sourceNode         = getProperty(sourceNode);
		final SchemaNode _targetNode         = getProperty(targetNode);

		if (_previousSourceJsonName != null && _currentSourceJsonName != null && !_currentSourceJsonName.equals(_previousSourceJsonName)) {

			renameNameInNonGraphProperties(_targetNode, _previousSourceJsonName, _currentSourceJsonName);

			renameNotionPropertyReferences(_sourceNode, _previousSourceJsonName, _currentSourceJsonName);
			renameNotionPropertyReferences(_targetNode, _previousSourceJsonName, _currentSourceJsonName);
		}

		if (_previousTargetJsonName != null && _currentTargetJsonName != null && !_currentTargetJsonName.equals(_previousTargetJsonName)) {

			renameNameInNonGraphProperties(_sourceNode, _previousTargetJsonName, _currentTargetJsonName);

			renameNotionPropertyReferences(_sourceNode, _previousTargetJsonName, _currentTargetJsonName);
			renameNotionPropertyReferences(_targetNode, _previousTargetJsonName, _currentTargetJsonName);
		}
	}

	private void removeSourceAndTargetJsonNames(PropertyMap properties) throws FrameworkException {

		final SchemaNode _sourceNode         = getProperty(sourceNode);
		final SchemaNode _targetNode         = getProperty(targetNode);
		final String _currentSourceJsonName  = (properties.get(sourceJsonName) != null) ? properties.get(sourceJsonName) : properties.get(previousSourceJsonName);
		final String _currentTargetJsonName  = (properties.get(targetJsonName) != null) ? properties.get(targetJsonName) : properties.get(previousTargetJsonName);

		if (_sourceNode != null) {

			removeNameFromNonGraphProperties(_sourceNode, _currentSourceJsonName);
			removeNameFromNonGraphProperties(_sourceNode, _currentTargetJsonName);

		}

		if (_targetNode != null) {

			removeNameFromNonGraphProperties(_targetNode, _currentSourceJsonName);
			removeNameFromNonGraphProperties(_targetNode, _currentTargetJsonName);

		}

	}

	private void renameNotionPropertyReferences(final SchemaNode schemaNode, final String previousValue, final String currentValue) throws FrameworkException {

		// examine properties of other node
		for (final SchemaProperty property : schemaNode.getSchemaProperties()) {

			if (Type.Notion.equals(property.getPropertyType())) {

				// try to rename
				final String basePropertyName = property.getNotionBaseProperty();
				if (basePropertyName.equals(previousValue)) {

					property.setProperty(SchemaProperty.format, property.getFormat().replace(previousValue, currentValue));
				}
			}

		}

	}

	private void renameNameInNonGraphProperties(final AbstractSchemaNode schemaNode, final String toRemove, final String newValue) throws FrameworkException {

		// examine all views
		for (final SchemaView view : schemaNode.getSchemaViews()) {

			final String nonGraphProperties = view.getProperty(SchemaView.nonGraphProperties);
			if (nonGraphProperties != null) {

				final ArrayList<String> properties = new ArrayList<>(Arrays.asList(nonGraphProperties.split("[, ]+")));

				final int pos = properties.indexOf(toRemove);
				if (pos != -1) {
					properties.set(pos, newValue);
				}

				view.setProperty(SchemaView.nonGraphProperties, StringUtils.join(properties, ", "));
			}
		}
	}

	private void removeNameFromNonGraphProperties(final AbstractSchemaNode schemaNode, final String toRemove) throws FrameworkException {

		// examine all views
		for (final SchemaView view : schemaNode.getSchemaViews()) {

			final String nonGraphProperties = view.getProperty(SchemaView.nonGraphProperties);
			if (nonGraphProperties != null) {

				final ArrayList<String> properties = new ArrayList<>(Arrays.asList(nonGraphProperties.split("[, ]+")));

				properties.remove(toRemove);

				view.setProperty(SchemaView.nonGraphProperties, StringUtils.join(properties, ", "));
			}
		}

	}

	// ----- public static methods -----
	public static String getDefaultRelationshipType(final SchemaRelationshipNode rel) {
		return getDefaultRelationshipType(rel.getSourceNode(), rel.getTargetNode());
	}

	public static String getDefaultRelationshipType(final SchemaNode sourceNode, final SchemaNode targetNode) {
		return getDefaultRelationshipType(sourceNode.getName(), targetNode.getName());
	}

	public static String getDefaultRelationshipType(final String sourceType, final String targetType) {
		return sourceType + "_" + targetType;
	}

	// ----- nested classes -----
	private static class KeyMatcher implements Predicate<String> {

		@Override
		public boolean accept(String t) {

			if (ValidKeyPattern.matcher(t).matches()) {
				return true;
			}

			logger.log(Level.WARNING, "Invalid key name {0} for notion.", t);

			return false;
		}
	}
}
