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
package org.structr.module;

import org.structr.agent.Agent;
import org.structr.core.entity.AbstractNode;
import org.structr.core.entity.AbstractRelationship;
import org.structr.core.entity.GenericNode;

//~--- JDK imports ------------------------------------------------------------
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.lang3.StringUtils;
import org.structr.common.DefaultFactoryDefinition;
import org.structr.common.FactoryDefinition;
import org.structr.common.PropertyView;
import org.structr.common.SecurityContext;
import org.structr.common.View;
import org.structr.core.*;
import org.structr.core.entity.Relation;
import org.structr.core.graph.NodeInterface;
import org.structr.core.graph.RelationshipInterface;
import org.structr.core.property.GenericProperty;
import org.structr.core.property.PropertyKey;
import org.structr.schema.ConfigurationProvider;
import org.structr.schema.SchemaService;

//~--- classes ----------------------------------------------------------------
/**
 * The module service main class.
 *
 *
 */
public class JarConfigurationProvider implements ConfigurationProvider {

	private static final Logger logger = Logger.getLogger(JarConfigurationProvider.class.getName());

	public static final String DYNAMIC_TYPES_PACKAGE = "org.structr.dynamic";

	private final Map<String, Class<? extends RelationshipInterface>> relationshipEntityClassCache = new ConcurrentHashMap<>(1000);
	private final Map<String, Class<? extends NodeInterface>> nodeEntityClassCache                 = new ConcurrentHashMap(1000);
	private final Map<String, Class<? extends Agent>> agentClassCache                              = new ConcurrentHashMap<>(100);

	private final Set<String> agentPackages                                                        = new LinkedHashSet<>();
 	private final Set<String> nodeEntityPackages                                                   = new LinkedHashSet<>();
 	private final Set<String> relationshipPackages                                                 = new LinkedHashSet<>();

	private final Map<String, Class> combinedTypeRelationClassCache                                = new ConcurrentHashMap<>(100);
	private final Map<String, Set<Class>> interfaceCache                                           = new ConcurrentHashMap<>(2000);

	private final String fileSep                                                                   = System.getProperty("file.separator");
	private final String pathSep                                                                   = System.getProperty("path.separator");
	private final String fileSepEscaped                                                            = fileSep.replaceAll("\\\\", "\\\\\\\\");
	private final String testClassesDir                                                            = fileSep.concat("test-classes");
	private final String classesDir                                                                = fileSep.concat("classes");

	private final Map<String, Map<String, Set<PropertyKey>>> globalPropertyViewMap                 = new ConcurrentHashMap<>(2000);
	private final Map<String, Map<PropertyKey, Set<PropertyValidator>>> globalValidatorMap         = new ConcurrentHashMap<>(100);
	private final Map<String, Map<String, PropertyKey>> globalClassDBNamePropertyMap               = new ConcurrentHashMap<>(2000);
	private final Map<String, Map<String, PropertyKey>> globalClassJSNamePropertyMap               = new ConcurrentHashMap<>(2000);
	private final Map<String, Map<String, PropertyGroup>> globalAggregatedPropertyGroupMap         = new ConcurrentHashMap<>(100);
	private final Map<String, Map<String, PropertyGroup>> globalPropertyGroupMap                   = new ConcurrentHashMap<>(100);
	private final Map<String, Map<String, ViewTransformation>> viewTransformations                 = new ConcurrentHashMap<>(100);
	private final Map<String, Set<Transformation<GraphObject>>> globalTransformationMap            = new ConcurrentHashMap<>(100);
	private final Map<String, Map<String, Method>> exportedMethodMap                               = new ConcurrentHashMap<>(100);
	private final Map<Class, Set<Class>> interfaceMap                                              = new ConcurrentHashMap<>(2000);
	private final Map<String, Class> reverseInterfaceMap                                           = new ConcurrentHashMap<>(5000);

	private final Set<PropertyKey> globalKnownPropertyKeys                                         = new LinkedHashSet<>();
	private final Set<String> dynamicViews                                                         = new LinkedHashSet<>();

	private FactoryDefinition factoryDefinition                                                    = new DefaultFactoryDefinition();

	// ----- interface Configuration -----
	@Override
	public void initialize() {
		scanResources();
	}

	@Override
	public void shutdown() {

		/* do not clear caches
		 nodeEntityClassCache.clear();
		 combinedTypeRelationClassCache.clear();
		 relationshipEntityClassCache.clear();
		 agentClassCache.clear();
		 */
	}

	@Override
	public Map<String, Class<? extends Agent>> getAgents() {
		return agentClassCache;
	}

	@Override
	public Map<String, Class<? extends NodeInterface>> getNodeEntities() {

		synchronized (SchemaService.class) {
			return nodeEntityClassCache;
		}
	}

	@Override
	public Map<String, Class<? extends RelationshipInterface>> getRelationshipEntities() {

		synchronized (SchemaService.class) {
			return relationshipEntityClassCache;
		}
	}

	@Override
	public Set<Class> getClassesForInterface(final String simpleName) {

		synchronized (SchemaService.class) {
			return interfaceCache.get(simpleName);
		}
	}

	@Override
	public Class getNodeEntityClass(final String simpleName) {

		Class nodeEntityClass = GenericNode.class;

		if ((simpleName != null) && (!simpleName.isEmpty())) {

			synchronized (SchemaService.class) {

				nodeEntityClass = nodeEntityClassCache.get(simpleName);

				if (nodeEntityClass == null) {

					for (String possiblePath : nodeEntityPackages) {

						if (possiblePath != null) {

							try {

								Class nodeClass = Class.forName(possiblePath + "." + simpleName);

								if (!Modifier.isAbstract(nodeClass.getModifiers())) {

									nodeEntityClassCache.put(simpleName, nodeClass);
									nodeEntityClass = nodeClass;

									// first match wins
									break;

								}

							} catch (ClassNotFoundException ex) {

								// ignore
							}
						}
					}
				}
			}
		}

		return nodeEntityClass;

	}

	@Override
	public Class getRelationshipEntityClass(final String name) {

		Class relationClass = AbstractRelationship.class;

		if ((name != null) && (name.length() > 0)) {

			synchronized (SchemaService.class) {

				relationClass = relationshipEntityClassCache.get(name);

				if (relationClass == null) {

					for (String possiblePath : relationshipPackages) {

						if (possiblePath != null) {

							try {

								Class nodeClass = Class.forName(possiblePath + "." + name);

								if (!Modifier.isAbstract(nodeClass.getModifiers())) {

									relationshipEntityClassCache.put(name, nodeClass);

									// first match wins
									return nodeClass;

								}

							} catch (ClassNotFoundException ex) {

								// ignore
							}
						}
					}
				}
			}
		}

		return relationClass;

	}

	public Class<? extends Agent> getAgentClass(final String name) {

		Class agentClass = null;

		if ((name != null) && (name.length() > 0)) {

			agentClass = agentClassCache.get(name);

			if (agentClass == null) {

				for (String possiblePath : agentPackages) {

					if (possiblePath != null) {

						try {

							Class nodeClass = Class.forName(possiblePath + "." + name);

							agentClassCache.put(name, nodeClass);

							// first match wins
							return nodeClass;

						} catch (ClassNotFoundException ex) {

							// ignore
						}

					}

				}

			}

		}

		return agentClass;

	}

	@Override
	public Map<String, Class> getInterfaces() {
		return reverseInterfaceMap;
	}

	@Override
	public void setRelationClassForCombinedType(final String combinedType, final Class clazz) {
		combinedTypeRelationClassCache.put(combinedType, clazz);
	}

	@Override
	public void setRelationClassForCombinedType(final String sourceType, final String relType, final String targetType, final Class clazz) {
		combinedTypeRelationClassCache.put(getCombinedType(sourceType, relType, targetType), clazz);
	}

	private Class getRelationClassForCombinedType(final String combinedType) {

		Class cachedRelationClass = combinedTypeRelationClassCache.get(combinedType);

		if (cachedRelationClass != null) {
			return cachedRelationClass;
		}

		return null;
	}

	@Override
	public Class getRelationClassForCombinedType(final String sourceTypeName, final String relType, final String targetTypeName) {

		if (sourceTypeName == null || relType == null || targetTypeName == null) {
			return null;
		}

		String combinedType
			= sourceTypeName
			.concat(DefaultFactoryDefinition.COMBINED_RELATIONSHIP_KEY_SEP)
			.concat(relType)
			.concat(DefaultFactoryDefinition.COMBINED_RELATIONSHIP_KEY_SEP)
			.concat(targetTypeName);

		Class cachedRelationClass = getRelationClassForCombinedType(combinedType);

		if (cachedRelationClass != null) {
			return cachedRelationClass;
		}

		return findNearestMatchingRelationClass(sourceTypeName, relType, targetTypeName);
	}

	/**
	 * Return a list of all relation entity classes filtered by relationship
	 * type.
	 *
	 * @param relType
	 * @return classes
	 */
	private List<Class<? extends RelationshipInterface>> getRelationClassCandidatesForRelType(final String relType) {

		List<Class<? extends RelationshipInterface>> candidates = new ArrayList();

		for (final Class<? extends RelationshipInterface> candidate : getRelationshipEntities().values()) {

			Relation rel = instantiate(candidate);

			if (rel == null) {
				continue;
			}

			if (rel.name().equals(relType)) {
				candidates.add(candidate);
			}

		}

		return candidates;

	}

	/**
	 * Find the most specialized relation class matching the given
	 * parameters.
	 *
	 * If no direct match is found (source and target type are equal), we
	 * count the levels of inheritance, including interfaces.
	 *
	 * @param sourceTypeName
	 * @param relType
	 * @param targetTypeName
	 * @param rel
	 * @param candidate
	 * @return class
	 */
	private Class findNearestMatchingRelationClass(final String sourceTypeName, final String relType, final String targetTypeName) {

		//System.out.println("###### Find nearest matching relation class for " + sourceTypeName + " " + relType + " " + targetTypeName);
		Map<Integer, Class> candidates = new TreeMap<>();
		Class sourceType = getNodeEntityClass(sourceTypeName);
		Class targetType = getNodeEntityClass(targetTypeName);

		for (final Class candidate : getRelationClassCandidatesForRelType(relType)) {

			Relation rel = instantiate(candidate);

			//System.out.println("? " + candidate.getSimpleName() + " for [" + sourceTypeName + " " + relType + " " + targetTypeName + "]");
			int distance = getDistance(rel.getSourceType(), sourceType, -1) + getDistance(rel.getTargetType(), targetType, -1);

			if (distance >= 2000) {

				candidates.put(distance - 2000, candidate);
				//System.out.println("\n=========================== Found " + candidate.getSimpleName() + " for " + sourceTypeName + " " + relType + " " + targetTypeName + " at distance " + (distance-2000));

			} else {
				//System.out.println(" no match.");
			}

		}

		if (candidates.isEmpty()) {

			//System.out.println("!!!!!!! No matching relation class found for " + sourceTypeName + " " + relType + " " + targetTypeName);
			return null;

		} else {

			Entry<Integer, Class> candidateEntry = candidates.entrySet().iterator().next();
			Class c = candidateEntry.getValue();

			//System.out.println("########### Final nearest relation class : " + c.getSimpleName() + " <" + candidateEntry.getKey() + ">############################################\n\n");
			combinedTypeRelationClassCache.put(getCombinedType(sourceTypeName, relType, targetTypeName), c);

			return c;
		}
	}

	private int getDistance(final Class candidateType, final Class type, int distance) {

		if (distance >= 1000) {
			return distance;
		}

		distance++;

		// Just in case...
		if (type == null) {
			return Integer.MIN_VALUE;
		}

		//System.out.print(".");
		// Abort if type is Object.class here
		if (type.equals(Object.class)) {
			return Integer.MIN_VALUE;
		}

		//System.out.print(".");
		//System.out.print(candidateType.getSimpleName() + "<" + distance + ">");
		//System.out.print(".");
		// Check direct equality
		if (type.equals(candidateType)) {
			//System.out.print("MATCH<" + distance + ">!");
			return distance + 1000;
		}

		// Abort here if type is NodeInterface.
		if (type.equals(NodeInterface.class)) {
			return Integer.MIN_VALUE;
		}

		//System.out.print(".");
		// Relation candidate's source and target types must be superclasses or interfaces of the given relationship
		if (!(candidateType.isAssignableFrom(type))) {
			return Integer.MIN_VALUE;
		}

		//System.out.print(".");
		distance++;

		// Test source's interfaces against target class
		Class[] interfaces = type.getInterfaces();
		for (Class iface : interfaces) {
			//System.out.print("." + iface.getSimpleName() + "<" + distance + ">" + "(SI).");
			if (iface.equals(candidateType)) {
				//System.out.print("MATCH<" + distance + ">!");
				return distance + 1000;
			}
		}

		distance++;

		Class superClass = type.getSuperclass();
		if (superClass != null) {
			//System.out.println("." + superClass.getSimpleName() + "<" + distance + ">");
			int d = getDistance(candidateType, superClass, distance);
			if (d >= 1000) {
				return d;
			}
		}

		return distance;
	}

	@Override
	public Map<String, Method> getAnnotatedMethods(Class entityType, Class annotationType) {

		Map<String, Method> methods = new HashMap<>();
		Set<Class<?>> allTypes      = getAllTypes(entityType);

		for (Class<?> type : allTypes) {

			for (Method method : type.getDeclaredMethods()) {

				if (method.getAnnotation(annotationType) != null) {

					methods.put(method.getName(), method);
				}
			}
		}

		return methods;
	}

	@Override
	public void unregisterEntityType(final Class oldType) {

		synchronized (SchemaService.class) {

			final String simpleName = oldType.getSimpleName();
			final String fqcn       = oldType.getName();

			nodeEntityClassCache.remove(simpleName);
			relationshipEntityClassCache.remove(simpleName);

			nodeEntityPackages.remove(fqcn);
			relationshipPackages.remove(fqcn);

			globalPropertyViewMap.remove(simpleName);
			globalClassDBNamePropertyMap.remove(simpleName);
			globalClassJSNamePropertyMap.remove(simpleName);

			interfaceMap.remove(oldType);

			// clear all
			combinedTypeRelationClassCache.clear();

			// clear interfaceCache manually..
			for (final Set<Class> classes : interfaceCache.values()) {

				if (classes.contains(oldType)) {
					classes.remove(oldType);
				}
			}
		}
	}

	@Override
	public void registerEntityType(final Class type) {

		// moved here from scanEntity, no reason to have this in a separate
		// method requiring two different calls instead of one
		String simpleName = type.getSimpleName();
		String fullName   = type.getName();

		if (AbstractNode.class.isAssignableFrom(type)) {
			nodeEntityClassCache.put(simpleName, type);
			nodeEntityPackages.add(fullName.substring(0, fullName.lastIndexOf(".")));
			globalPropertyViewMap.remove(type.getName());
		}

		if (AbstractRelationship.class.isAssignableFrom(type)) {

			relationshipEntityClassCache.put(simpleName, type);
			relationshipPackages.add(fullName.substring(0, fullName.lastIndexOf(".")));
			globalPropertyViewMap.remove(type.getName());
		}

		for (Class interfaceClass : type.getInterfaces()) {

			String interfaceName = interfaceClass.getSimpleName();
			Set<Class> classesForInterface = interfaceCache.get(interfaceName);

			if (classesForInterface == null) {

				classesForInterface = new LinkedHashSet<>();

				interfaceCache.put(interfaceName, classesForInterface);

			}

			classesForInterface.add(type);

		}

		try {

			Map<Field, PropertyKey> allProperties = getFieldValuesOfType(PropertyKey.class, type);
			Map<Field, View> views = getFieldValuesOfType(View.class, type);

			for (Map.Entry<Field, PropertyKey> entry : allProperties.entrySet()) {

				PropertyKey propertyKey = entry.getValue();
				Field field = entry.getKey();
				Class declaringClass = field.getDeclaringClass();

				if (declaringClass != null) {

					propertyKey.setDeclaringClass(declaringClass);
					registerProperty(declaringClass, propertyKey);

				}

				registerProperty(type, propertyKey);
			}

			for (Map.Entry<Field, View> entry : views.entrySet()) {

				Field field = entry.getKey();
				View view = entry.getValue();

				for (PropertyKey propertyKey : view.properties()) {

					// register field in view for entity class and declaring superclass
					registerPropertySet(field.getDeclaringClass(), view.name(), propertyKey);
					registerPropertySet(type, view.name(), propertyKey);
				}
			}

		} catch (Throwable t) {
			logger.log(Level.SEVERE, "Unable to register type {0}: {1}", new Object[]{type, t.getMessage()});
			t.printStackTrace();
		}

		Map<String, Method> typeMethods = exportedMethodMap.get(type.getName());
		if (typeMethods == null) {
			typeMethods = new HashMap<>();
			exportedMethodMap.put(type.getName(), typeMethods);
		}

		typeMethods.putAll(getAnnotatedMethods(type, Export.class));

		// extract interfaces for later use
		getInterfacesForType(type);
	}

	/**
	 * Register a transformation that will be applied to every newly created
	 * entity of a given type.
	 *
	 * @param type the type of the entities for which the transformation
	 * should be applied
	 * @param transformation the transformation to apply on every entity
	 */
	@Override
	public void registerEntityCreationTransformation(Class type, Transformation<GraphObject> transformation) {

		final Set<Transformation<GraphObject>> transformations = getEntityCreationTransformationsForType(type);
		if (!transformations.contains(transformation)) {

			transformations.add(transformation);
		}
	}

	@Override
	public Set<Class> getInterfacesForType(Class type) {

		Set<Class> interfaces = interfaceMap.get(type);
		if (interfaces == null) {

			interfaces = new LinkedHashSet<>();
			interfaceMap.put(type, interfaces);

			for (Class iface : type.getInterfaces()) {

				reverseInterfaceMap.put(iface.getSimpleName(), iface);
				interfaces.add(iface);
			}
		}

		return interfaces;
	}

	@Override
	public Map<String, Method> getExportedMethodsForType(Class type) {
		return exportedMethodMap.get(type.getName());
	}

	@Override
	public boolean isKnownProperty(final PropertyKey key) {
		return globalKnownPropertyKeys.contains(key);
	}

	@Override
	public FactoryDefinition getFactoryDefinition() {
		return factoryDefinition;
	}

	@Override
	public void registerFactoryDefinition(FactoryDefinition factory) {
		factoryDefinition = factory;
	}

	/**
	 * Registers a property group for the given key of the given entity
	 * type. A property group can be used to combine a set of properties
	 * into an object.
	 *
	 * @param type the type of the entities for which the property group
	 * should be registered
	 * @param key the property key under which the property group should be
	 * visible
	 * @param propertyGroup the property group
	 */
	@Override
	public void registerPropertyGroup(Class type, PropertyKey key, PropertyGroup propertyGroup) {
		getPropertyGroupMapForType(type).put(key.dbName(), propertyGroup);
	}

	@Override
	public void registerConvertedProperty(PropertyKey propertyKey) {
		globalKnownPropertyKeys.add(propertyKey);
	}

	@Override
	public synchronized Set<Transformation<GraphObject>> getEntityCreationTransformations(Class type) {

		Set<Transformation<GraphObject>> transformations = new TreeSet<>();
		Class localType = type;

		// collect for all superclasses
		while (localType != null && !localType.equals(Object.class)) {

			transformations.addAll(getEntityCreationTransformationsForType(localType));

			localType = localType.getSuperclass();

		}

		return transformations;
	}

	@Override
	public PropertyGroup getPropertyGroup(Class type, PropertyKey key) {
		return getPropertyGroup(type, key.dbName());
	}

	@Override
	public PropertyGroup getPropertyGroup(Class type, String key) {

		PropertyGroup group = getAggregatedPropertyGroupMapForType(type).get(key);
		if (group == null) {

			Class localType = type;

			while (group == null && localType != null && !localType.equals(Object.class)) {

				group = getPropertyGroupMapForType(localType).get(key);

				if (group == null) {

					// try interfaces as well
					for (Class interfaceClass : getInterfacesForType(localType)) {

						group = getPropertyGroupMapForType(interfaceClass).get(key);
						if (group != null) {
							break;
						}
					}
				}

				localType = localType.getSuperclass();
			}

			getAggregatedPropertyGroupMapForType(type).put(key, group);
		}

		return group;
	}

	@Override
	public void registerViewTransformation(Class type, String view, ViewTransformation transformation) {
		getViewTransformationMapForType(type).put(view, transformation);
	}

	@Override
	public ViewTransformation getViewTransformation(Class type, String view) {
		return getViewTransformationMapForType(type).get(view);
	}

	@Override
	public Set<String> getPropertyViews() {

		Set<String> views = new LinkedHashSet<>();

		// add all existing views
		for (Map<String, Set<PropertyKey>> view : globalPropertyViewMap.values()) {
			views.addAll(view.keySet());
		}

		// merge dynamic views in as well
		views.addAll(dynamicViews);

		return Collections.unmodifiableSet(views);
	}

	@Override
	public Set<String> getPropertyViewsForType(final Class type) {

		final Map<String, Set<PropertyKey>> map = getPropertyViewMapForType(type);
		if (map != null) {

			return map.keySet();
		}

		return Collections.emptySet();
	}

	@Override
	public void registerDynamicViews(final Set<String> dynamicViews) {
		this.dynamicViews.clear();
		this.dynamicViews.addAll(dynamicViews);
	}

	@Override
	public Set<PropertyKey> getPropertySet(Class type, String propertyView) {

		Map<String, Set<PropertyKey>> propertyViewMap = getPropertyViewMapForType(type);
		Set<PropertyKey> properties = propertyViewMap.get(propertyView);

		if (properties == null) {
			properties = new LinkedHashSet<>();
		}

		// read-only
		return Collections.unmodifiableSet(properties);
	}

	/**
	 * Registers the given set of property keys for the view with name
	 * <code>propertyView</code> and the given prefix of entities with the
	 * given type.
	 *
	 * @param type the type of the entities for which the view will be
	 * registered
	 * @param propertyView the name of the property view for which the
	 * property set will be registered
	 * @param propertySet the set of property keys to register for the given
	 * view
	 */
	@Override
	public void registerPropertySet(Class type, String propertyView, PropertyKey... propertySet) {

		Map<String, Set<PropertyKey>> propertyViewMap = getPropertyViewMapForType(type);
		Set<PropertyKey> properties = propertyViewMap.get(propertyView);

		if (properties == null) {
			properties = new LinkedHashSet<>();
			propertyViewMap.put(propertyView, properties);
		}

		// allow properties to override existing ones as they
		// are most likely from a more concrete class.
		for (final PropertyKey key : propertySet) {

			// property keys are referenced by their names,
			// that's why we seemingly remove the existing
			// key, but the set does not differentiate
			// between different keys
			if (properties.contains(key)) {
				properties.remove(key);
			}

			properties.add(key);
		}
	}

	@Override
	public PropertyKey getPropertyKeyForDatabaseName(Class type, String dbName) {
		return getPropertyKeyForDatabaseName(type, dbName, true);
	}

	@Override
	public PropertyKey getPropertyKeyForDatabaseName(Class type, String dbName, boolean createGeneric) {

		Map<String, PropertyKey> classDBNamePropertyMap = getClassDBNamePropertyMapForType(type);
		PropertyKey key = classDBNamePropertyMap.get(dbName);

		if (key == null) {

			// first try: uuid
			if (GraphObject.id.dbName().equals(dbName)) {
				return GraphObject.id;
			}

			if (createGeneric) {
				key = new GenericProperty(dbName);
			}
		}

		return key;
	}

	@Override
	public PropertyKey getPropertyKeyForJSONName(Class type, String jsonName) {
		return getPropertyKeyForJSONName(type, jsonName, true);
	}

	@Override
	public PropertyKey getPropertyKeyForJSONName(Class type, String jsonName, boolean createIfNotFound) {

		if (jsonName == null) {
			return null;
		}

		Map<String, PropertyKey> classJSNamePropertyMap = getClassJSNamePropertyMapForType(type);
		PropertyKey key = classJSNamePropertyMap.get(jsonName);

		if (key == null) {

			// first try: uuid
			if (GraphObject.id.dbName().equals(jsonName)) {

				return GraphObject.id;
			}

			if (createIfNotFound) {

				key = new GenericProperty(jsonName);
			}
		}

		return key;
	}

	@Override
	public Set<PropertyValidator> getPropertyValidators(final SecurityContext securityContext, Class type, PropertyKey propertyKey) {

		Set<PropertyValidator> validators = new LinkedHashSet<>();
		Map<PropertyKey, Set<PropertyValidator>> validatorMap = null;
		Class localType = type;

		// try all superclasses
		while (localType != null && !localType.equals(Object.class)) {

			validatorMap = getPropertyValidatorMapForType(localType);

			Set<PropertyValidator> classValidators = validatorMap.get(propertyKey);
			if (classValidators != null) {
				validators.addAll(validatorMap.get(propertyKey));
			}

			// try converters from interfaces as well
			for (Class interfaceClass : getInterfacesForType(localType)) {
				Set<PropertyValidator> interfaceValidators = getPropertyValidatorMapForType(interfaceClass).get(propertyKey);
				if (interfaceValidators != null) {
					validators.addAll(interfaceValidators);
				}
			}

//                      logger.log(Level.INFO, "Validator class {0} found for type {1}", new Object[] { clazz != null ? clazz.getSimpleName() : "null", localType } );
			// one level up :)
			localType = localType.getSuperclass();

		}

		return validators;
	}

	@Override
	public void registerProperty(Class type, PropertyKey propertyKey) {

		getClassDBNamePropertyMapForType(type).put(propertyKey.dbName(), propertyKey);
		getClassJSNamePropertyMapForType(type).put(propertyKey.jsonName(), propertyKey);

		registerPropertySet(type, PropertyView.All, propertyKey);

		// inform property key of its registration
		propertyKey.registrationCallback(type);
	}

	@Override
	public void registerDynamicProperty(Class type, PropertyKey propertyKey) {

		synchronized (SchemaService.class) {

			final String typeName = type.getName();

			registerProperty(type, propertyKey);

			// scan all existing classes and find all classes that have the given
			// type as a supertype

			for (final Class possibleSubclass : nodeEntityClassCache.values()) {

				// need to compare strings not classes here..
				for (final Class supertype : getAllTypes(possibleSubclass)) {

					if (supertype.getName().equals(typeName)) {

						registerProperty(possibleSubclass, propertyKey);
						registerPropertySet(possibleSubclass, PropertyView.Ui, propertyKey);
					}
				}
			}
		}
	}

	// ----- private methods -----
	private void scanResources() {

		Set<String> resourcePaths = getResourcesToScan();

		for (String resourcePath : resourcePaths) {

			scanResource(resourcePath);
		}

		logger.log(Level.INFO, "{0} JARs scanned", resourcePaths.size());

	}

	private void scanResource(String resourceName) {

		try {

			Module module = loadResource(resourceName);

			if (module != null) {

				importResource(module);

			} else {

				logger.log(Level.WARNING, "Module was null!");
			}

		} catch (IOException ioex) {

			logger.log(Level.WARNING, "Error loading module {0}: {1}", new Object[]{resourceName, ioex});
			ioex.printStackTrace();

		}

	}

	private void importResource(Module module) throws IOException {

		final Set<String> classes = module.getClasses();

		for (final String name : classes) {

			String className = StringUtils.removeStart(name, ".");

			logger.log(Level.FINE, "Instantiating class {0} ", className);

			try {

				// instantiate class..
				Class clazz = Class.forName(className);
				int modifiers = clazz.getModifiers();

				logger.log(Level.FINE, "Class {0} instantiated: {1}", new Object[]{className, clazz});

				// register node entity classes
				if (NodeInterface.class.isAssignableFrom(clazz)) {

					registerEntityType(clazz);
				}

				// register entity classes
				if (AbstractRelationship.class.isAssignableFrom(clazz) && !(Modifier.isAbstract(modifiers))) {

					registerEntityType(clazz);
				}

				// register services
				if (Service.class.isAssignableFrom(clazz) && !(Modifier.isAbstract(modifiers))) {

					Services.getInstance().registerServiceClass(clazz);
				}

				// register agents
				if (Agent.class.isAssignableFrom(clazz) && !(Modifier.isAbstract(modifiers))) {

					String simpleName = clazz.getSimpleName();
					String fullName = clazz.getName();

					agentClassCache.put(simpleName, clazz);
					agentPackages.add(fullName.substring(0, fullName.lastIndexOf(".")));

				}

			} catch (Throwable t) {
			}

		}

	}

	private Module loadResource(String resource) throws IOException {

		// create module
		DefaultModule ret = new DefaultModule(resource);
		Set<String> classes = ret.getClasses();

		if (resource.endsWith(".jar") || resource.endsWith(".war")) {

			ZipFile zipFile = new ZipFile(new File(resource), ZipFile.OPEN_READ);

			// conventions that might be useful here:
			// ignore entries beginning with meta-inf/
			// handle entries beginning with images/ as IMAGE
			// handle entries beginning with pages/ as PAGES
			// handle entries ending with .jar as libraries, to be deployed to WEB-INF/lib
			// handle other entries as potential page and/or entity classes
			// .. to be extended
			// (entries that end with "/" are directories)
			for (Enumeration<? extends ZipEntry> entries = zipFile.entries(); entries.hasMoreElements();) {

				ZipEntry entry = entries.nextElement();
				String entryName = entry.getName();

				if (entryName.endsWith(".class")) {

					String fileEntry = entry.getName().replaceAll("[/]+", ".");

					// add class entry to Module
					classes.add(fileEntry.substring(0, fileEntry.length() - 6));

				}

			}

			zipFile.close();

		} else if (resource.endsWith(classesDir)) {

			addClassesRecursively(new File(resource), classesDir, classes);

		} else if (resource.endsWith(testClassesDir)) {

			addClassesRecursively(new File(resource), testClassesDir, classes);
		}

		return ret;
	}

	private void addClassesRecursively(File dir, String prefix, Set<String> classes) {

		if (dir == null) {
			return;
		}

		int prefixLen = prefix.length();
		File[] files = dir.listFiles();

		if (files == null) {
			return;
		}

		for (File file : files) {

			if (file.isDirectory()) {

				addClassesRecursively(file, prefix, classes);

			} else {

				try {

					String fileEntry = file.getAbsolutePath();

					fileEntry = fileEntry.substring(0, fileEntry.length() - 6);
					fileEntry = fileEntry.substring(fileEntry.indexOf(prefix) + prefixLen);
					fileEntry = fileEntry.replaceAll("[".concat(fileSepEscaped).concat("]+"), ".");

					if (fileEntry.startsWith(".")) {
						fileEntry = fileEntry.substring(1);
					}

					classes.add(fileEntry);

				} catch (Throwable t) {
					// ignore
					t.printStackTrace();
				}

			}

		}

	}

	private Relation instantiate(final Class clazz) {

		try {

			return (Relation) clazz.newInstance();

		} catch (Throwable t) {
			// ignore
			//t.printStackTrace();
		}

		return null;
	}

	private String getCombinedType(final String sourceType, final String relType, final String targetType) {

		return sourceType
			.concat(DefaultFactoryDefinition.COMBINED_RELATIONSHIP_KEY_SEP)
			.concat(relType)
			.concat(DefaultFactoryDefinition.COMBINED_RELATIONSHIP_KEY_SEP)
			.concat(targetType);
	}

	/**
	 * Scans the class path and returns a Set containing all structr
	 * modules.
	 *
	 * @return a Set of active module names
	 */
	private Set<String> getResourcesToScan() {

		String classPath = System.getProperty("java.class.path");
		Set<String> modules = new LinkedHashSet<>();
		Pattern pattern = Pattern.compile(".*(structr).*(war|jar)");
		Matcher matcher = pattern.matcher("");

		for (String jarPath : classPath.split("[".concat(pathSep).concat("]+"))) {

			String lowerPath = jarPath.toLowerCase();

			if (lowerPath.endsWith(classesDir) || lowerPath.endsWith(testClassesDir)) {

				modules.add(jarPath);

			} else {

				String moduleName = lowerPath.substring(lowerPath.lastIndexOf(pathSep) + 1);

				matcher.reset(moduleName);

				if (matcher.matches()) {

					modules.add(jarPath);
				}

			}

		}

		for (String resource : Services.getInstance().getResources()) {

			String lowerResource = resource.toLowerCase();

			if (lowerResource.endsWith(".jar") || lowerResource.endsWith(".war")) {

				modules.add(resource);
			}

		}

		return modules;
	}

	private <T> Map<Field, T> getFieldValuesOfType(Class<T> fieldType, Class entityType) {

		Map<Field, T> fields = new LinkedHashMap<>();
		Set<Class<?>> allTypes = getAllTypes(entityType);

		for (Class<?> type : allTypes) {

			for (Field field : type.getDeclaredFields()) {

				// only use static fields, because field.get(null) will throw a NPE on non-static fields
				if (fieldType.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())) {

					try {

						// ensure access
						field.setAccessible(true);

						// fetch value
						final T value = (T) field.get(null);
						if (value != null) {

							fields.put(field, value);
						}

					} catch (Throwable t) {
						// ignore
					}
				}
			}
		}

		return fields;
	}

	private Set<Class<?>> getAllTypes(Class<?> type) {

		List<Class<?>> types = new LinkedList<>();
		Class localType = type;

		do {

			collectAllInterfaces(localType, types);
			types.add(localType);

			localType = localType.getSuperclass();

		} while (localType != null && !localType.equals(Object.class));

		Collections.reverse(types);

		return new LinkedHashSet<>(types);
	}

	private void collectAllInterfaces(Class<?> type, List<Class<?>> interfaces) {

		if (interfaces.contains(type)) {
			return;
		}

		for (Class iface : type.getInterfaces()) {

			collectAllInterfaces(iface, interfaces);
			interfaces.add(iface);
		}
	}

	private Map<String, Set<PropertyKey>> getPropertyViewMapForType(Class type) {

		Map<String, Set<PropertyKey>> propertyViewMap = globalPropertyViewMap.get(type.getName());

		if (propertyViewMap == null) {

			propertyViewMap = new LinkedHashMap<>();

			globalPropertyViewMap.put(type.getName(), propertyViewMap);

		}

		return propertyViewMap;
	}

	private Map<String, PropertyKey> getClassDBNamePropertyMapForType(Class type) {

		Map<String, PropertyKey> classDBNamePropertyMap = globalClassDBNamePropertyMap.get(type.getName());

		if (classDBNamePropertyMap == null) {

			classDBNamePropertyMap = new LinkedHashMap<>();

			globalClassDBNamePropertyMap.put(type.getName(), classDBNamePropertyMap);

		}

		return classDBNamePropertyMap;
	}

	private Map<String, PropertyKey> getClassJSNamePropertyMapForType(Class type) {

		Map<String, PropertyKey> classJSNamePropertyMap = globalClassJSNamePropertyMap.get(type.getName());

		if (classJSNamePropertyMap == null) {

			classJSNamePropertyMap = new LinkedHashMap<>();

			globalClassJSNamePropertyMap.put(type.getName(), classJSNamePropertyMap);

		}

		return classJSNamePropertyMap;
	}

	private Map<PropertyKey, Set<PropertyValidator>> getPropertyValidatorMapForType(Class type) {

		Map<PropertyKey, Set<PropertyValidator>> validatorMap = globalValidatorMap.get(type.getName());

		if (validatorMap == null) {

			validatorMap = new LinkedHashMap<>();

			globalValidatorMap.put(type.getName(), validatorMap);

		}

		return validatorMap;
	}

	private Map<String, PropertyGroup> getAggregatedPropertyGroupMapForType(Class type) {

		Map<String, PropertyGroup> groupMap = globalAggregatedPropertyGroupMap.get(type.getName());

		if (groupMap == null) {

			groupMap = new LinkedHashMap<>();

			globalAggregatedPropertyGroupMap.put(type.getName(), groupMap);

		}

		return groupMap;
	}

	private Map<String, PropertyGroup> getPropertyGroupMapForType(Class type) {

		Map<String, PropertyGroup> groupMap = globalPropertyGroupMap.get(type.getName());

		if (groupMap == null) {

			groupMap = new LinkedHashMap<>();

			globalPropertyGroupMap.put(type.getName(), groupMap);

		}

		return groupMap;
	}

	private Set<Transformation<GraphObject>> getEntityCreationTransformationsForType(Class type) {

		final String name = type.getName();

		Set<Transformation<GraphObject>> transformations = globalTransformationMap.get(name);
		if (transformations == null) {

			transformations = new LinkedHashSet<>();

			globalTransformationMap.put(name, transformations);
		}

		return transformations;
	}

	private Map<String, ViewTransformation> getViewTransformationMapForType(Class type) {

		Map<String, ViewTransformation> viewTransformationMap = viewTransformations.get(type.getName());
		if (viewTransformationMap == null) {
			viewTransformationMap = new LinkedHashMap<>();
			viewTransformations.put(type.getName(), viewTransformationMap);
		}

		return viewTransformationMap;
	}

	public void printCacheStats() {

		System.out.println("###################################################");
 		System.out.println("" + relationshipEntityClassCache.size());
 		System.out.println("" + nodeEntityClassCache.size());
 		System.out.println("" + nodeEntityPackages.size());
 		System.out.println("" + relationshipPackages.size());
		System.out.println("" + combinedTypeRelationClassCache.size());
 		System.out.println("" + interfaceCache.size());
 		System.out.println("" + globalPropertyViewMap.size());
		System.out.println("" + globalValidatorMap.size());
 		System.out.println("" + globalClassDBNamePropertyMap.size());
 		System.out.println("" + globalClassJSNamePropertyMap.size());
		System.out.println("" + globalAggregatedPropertyGroupMap.size());
		System.out.println("" + globalPropertyGroupMap.size());
		System.out.println("" + viewTransformations.size());
		System.out.println("" + globalTransformationMap.size());
		System.out.println("" + exportedMethodMap.size());
		System.out.println("" + interfaceMap.size());
	 	System.out.println("" + reverseInterfaceMap.size());
		System.out.println("" + globalKnownPropertyKeys.size());
		System.out.println("" + dynamicViews.size());
		System.out.println("###################################################");
	}
}
