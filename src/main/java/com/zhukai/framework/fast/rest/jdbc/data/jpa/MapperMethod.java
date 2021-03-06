package com.zhukai.framework.fast.rest.jdbc.data.jpa;

import com.zhukai.framework.fast.rest.annotation.jpa.ExecuteUpdate;
import com.zhukai.framework.fast.rest.annotation.jpa.QueryCondition;
import com.zhukai.framework.fast.rest.jdbc.DBConnectionPool;
import com.zhukai.framework.fast.rest.util.ReflectUtil;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MapperMethod<T> {
	private static final Logger logger = LoggerFactory.getLogger(MapperMethod.class);

	private Connection conn;
	private Method method;
	private Object[] args;
	private ResultSet resultSet;
	private PreparedStatement statement;
	private Class<T> entityClass;
	private boolean isTransactional = true;

	public MapperMethod(Method method, Object[] args, Class<T> entityClass, Connection conn) {
		this.entityClass = entityClass;
		this.method = method;
		this.args = args;
		this.conn = conn;
	}

	public void release() throws SQLException, InterruptedException {
		if (conn != null && !isTransactional) {
			DBConnectionPool.freeConnection(conn);
		}
		if (statement != null) {
			statement.close();
		}
		if (resultSet != null) {
			resultSet.close();
		}
	}

	@SuppressWarnings("unchecked")
	public Object execute() throws Exception {
		checkTransactional();
		String methodName = method.getName();
		if (method.isAnnotationPresent(ExecuteUpdate.class)) {
			String sql = method.getAnnotation(ExecuteUpdate.class).value();
			return executeUpdate(sql, args);
		}
		if (method.isAnnotationPresent(QueryCondition.class)) {
			String queryCondition = method.getAnnotation(QueryCondition.class).value();
			String sql = JpaUtil.getSelectSqlWithoutProperties(entityClass).append(" WHERE ").append(queryCondition).toString();
			if (List.class.isAssignableFrom(method.getReturnType())) {
				return getEntityList(sql, args);
			}
			return getEntity(sql + " LIMIT 1 ", args);
		}
		switch (methodName) {
			case "findOne":
				return getBean(args[0]);
			case "exists":
				if (args[0] instanceof Object[]) {
					return existsByProperties((Object[]) args[0]);
				}
				return exists(args[0]);
			case "findAll":
				if (args != null) {
					if (args[0] instanceof Object[]) {
						return getBeans((Object[]) args[0]);
					} else if (args[0] instanceof List) {

						return getBeansIn(List.class.cast(args[0]));
					}
				}
				return getBeans(null);
			case "delete":
				return delete(args[0]);
			case "save":
				if (args[0] instanceof List) {
					return saveBeans((List<T>) args[0]);
				}
				return saveBean((T) args[0]);
			case "count":
				return count();
		}
		if (methodName.startsWith("findBy")) {
			StringBuilder propertiesSql = new StringBuilder();
			String propertiesString = methodName.substring(6);
			String[] arr = propertiesString.split("And|Or");
			for (int i = 0; i < arr.length; i++) {
				if (i == 0) {
					propertiesSql.append(" WHERE ");
				}
				propertiesSql.append(JpaUtil.getColumnName(entityClass, StringUtils.uncapitalize(arr[i]))).append("=").append(JpaUtil.convertToColumnValue(args[i]));
				String afterString = propertiesString.substring(propertiesString.indexOf(arr[i]) + arr[i].length());
				if (afterString.startsWith("And")) {
					propertiesSql.append(" AND ");
				} else if (afterString.startsWith("Or")) {
					propertiesSql.append(" OR ");
				}
			}
			String selectSQL = JpaUtil.getSelectSqlWithoutProperties(entityClass).append(propertiesSql).toString();
			if (List.class.isAssignableFrom(method.getReturnType())) {
				return getEntityList(selectSQL);
			}
			return getEntity(selectSQL + " LIMIT 1 ");
		}
		throw new NoSuchMethodException(methodName + " is not exists");
	}

	private void checkTransactional() throws Exception {
		if (conn == null) {
			isTransactional = false;
			conn = DBConnectionPool.getConnection();
		}
	}

	private long count() throws Exception {
		String tableName = JpaUtil.getTableName(entityClass);
		resultSet = executeQuery("select count(*) from " + tableName);
		if (resultSet.next()) {
			return resultSet.getInt(1);
		}
		logger.warn("Table {} is not exists", tableName);
		return -1;
	}

	private boolean saveBeans(List<T> beans) throws Exception {
		for (T bean : beans) {
			if (!saveBean(bean)) {
				return false;
			}
		}
		return true;
	}

	private boolean saveBean(T bean) throws Exception {
		Field idField = JpaUtil.getIdField(entityClass);
		Object id = ReflectUtil.getFieldValue(bean, idField.getName());
		String sql;
		if (!exists(id)) {
			sql = JpaUtil.getSaveSQL(bean);
		} else {
			sql = JpaUtil.getUpdateSQL(bean);
		}
		return executeUpdate(sql);
	}

	private <ID> boolean delete(ID id) throws SQLException {
		StringBuilder sql = new StringBuilder();
		sql.append("DELETE FROM ").append(JpaUtil.getTableName(entityClass)).append(" ");
		sql.append("WHERE ");
		Field idField = JpaUtil.getIdField(entityClass);
		String idFieldName = JpaUtil.getColumnName(idField);
		sql.append(idFieldName).append("=").append(JpaUtil.convertToColumnValue(id));
		return executeUpdate(sql.toString());
	}

	private <ID> T getBean(ID id) throws Exception {
		Field idField = JpaUtil.getIdField(entityClass);
		String idName = JpaUtil.getColumnName(idField);
		List<T> beans = getBeans(new Object[]{idName, id});
		if (beans != null && !beans.isEmpty()) {
			return beans.get(0);
		}
		return null;
	}

	private <ID> boolean exists(ID ID) throws Exception {
		Field idField = JpaUtil.getIdField(entityClass);
		String sql = JpaUtil.getSelectSQL(entityClass, new Object[]{idField.getName(), ID});
		resultSet = executeQuery(sql);
		return resultSet.next();
	}

	private boolean existsByProperties(Object[] properties) throws Exception {
		String sql = JpaUtil.getSelectSQL(entityClass, properties);
		resultSet = executeQuery(sql + " LIMIT 1");
		return resultSet.next();
	}

	private List<T> getBeans(Object[] properties) throws Exception {
		String sql = JpaUtil.getSelectSQL(entityClass, properties);
		return getEntityList(sql);
	}

	private <ID> List<T> getBeansIn(List<ID> ids) throws Exception {
		List<T> beans = new ArrayList<>();
		for (ID id : ids) {
			beans.add(getBean(id));
		}
		return beans;
	}

	private T getEntity(String sql) throws Exception {
		return getEntity(sql, null);
	}

	private T getEntity(String sql, Object[] properties) throws Exception {
		List<T> entityList = getEntityList(sql, properties);
		if (entityList != null && !entityList.isEmpty()) {
			return entityList.get(0);
		}
		return null;
	}

	private List<T> getEntityList(String sql) throws Exception {
		return getEntityList(sql, null);
	}

	private List<T> getEntityList(String sql, Object[] properties) throws Exception {
		if (properties != null) {
			resultSet = executeQuery(sql, properties);
		} else {
			resultSet = executeQuery(sql);
		}
		List<T> entityList = new ArrayList<>();
		while (resultSet.next()) {
			entityList.add(JpaUtil.convertToEntity(entityClass, resultSet));
		}
		return entityList;
	}

	private ResultSet executeQuery(String sql) throws SQLException {
		logger.debug("Execute sql: {}", sql);
		statement = conn.prepareStatement(sql);
		return statement.executeQuery();
	}

	private ResultSet executeQuery(String sql, Object[] properties) throws SQLException {
		statement = fillStatement(sql, properties);
		return statement.executeQuery();
	}

	private boolean executeUpdate(String sql) throws SQLException {
		logger.debug("Execute sql: {}", sql);
		statement = conn.prepareStatement(sql);
		return statement.executeUpdate() >= 1;
	}

	private boolean executeUpdate(String sql, Object[] properties) throws SQLException {
		statement = fillStatement(sql, properties);
		return statement.executeUpdate() >= 1;
	}

	private PreparedStatement fillStatement(String sql, Object[] properties) throws SQLException {
		logger.debug("Execute sql: {}, parameters: {}", sql, Arrays.toString(properties));
		PreparedStatement statement = conn.prepareStatement(sql);
		for (int i = 0; i < properties.length; i++) {
			statement.setObject(i + 1, properties[i]);
		}
		return statement;
	}
}
